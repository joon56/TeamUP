package com.modo.app.data

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.modo.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 첨부 파일 저장소: Supabase Storage 버킷 `modo-files` (REST).
 * 모든 요청에 공개 키(apikey)와 Firebase ID 토큰을 함께 보낸다. 버킷 정책이 우리 Firebase 프로젝트 사용자만
 * 올리기·받기를 허용하고, 지우기는 올린 사람만, 목록 조회는 막혀 있다. 키는 "projects|dm/폴더/UUID.확장자".
 */
object FileStore {

    private const val BUCKET = "modo-files"
    private const val CHUNK = 64 * 1024

    private var baseUrl = ""
    private var apiKey = ""
    private lateinit var cacheDir: File

    /** 서버가 돌려준 오류 코드 (AccessDenied, NoSuchKey, EntityTooLarge …) */
    class StorageException(val code: String, message: String) : IOException(message)

    fun init(context: Context) {
        baseUrl = context.getString(R.string.supabase_url).trimEnd('/') + "/storage/v1"
        apiKey = context.getString(R.string.supabase_publishable_key)
        cacheDir = File(context.cacheDir, "modo-files")
    }

    val isConfigured: Boolean get() = baseUrl.startsWith("https://") && apiKey.isNotBlank()

    /** 받아 둔 파일 (없으면 null). 화면을 다시 그릴 때 바로 쓰려고 */
    fun cached(key: String): File? = cacheFile(key).takeIf { it.exists() && it.length() > 0 }

    /**
     * 올리기. onProgress(0~100)는 메인 스레드에서 5% 단위로 불린다.
     * 크기를 모르면(일부 앱이 0을 알려 줌) 먼저 임시 파일로 복사해 크기를 정한다.
     */
    suspend fun upload(context: Context, uri: Uri, key: String, mime: String, size: Long, onProgress: (Int) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            var source = uri
            var length = size
            var temp: File? = null
            if (length <= 0) {
                temp = File.createTempFile("upload", null, context.cacheDir)
                context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
                    ?: throw StorageException("NoInput", "cannot read file")
                source = Uri.fromFile(temp)
                length = temp.length()
            }
            try {
                val conn = open("POST", "/object/$BUCKET/$key")
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", mime)
                conn.setRequestProperty("x-upsert", "false")
                conn.setRequestProperty("cache-control", "max-age=31536000")
                conn.setFixedLengthStreamingMode(length)
                val input = context.contentResolver.openInputStream(source) ?: throw StorageException("NoInput", "cannot read file")
                input.use {
                    conn.outputStream.use { output ->
                        val buffer = ByteArray(CHUNK)
                        var sent = 0L
                        var reported = -1
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            sent += n
                            val percent = (sent * 100 / length).toInt().coerceAtMost(100)
                            if (percent / 5 != reported / 5) {
                                reported = percent
                                withContext(Dispatchers.Main) { onProgress(percent) }
                            }
                        }
                    }
                }
                ensureOk(conn)
                // 내가 올린 파일은 바로 열 수 있게 캐시에도 둠
                runCatching { context.contentResolver.openInputStream(source)?.use { input -> writeCache(key, input) } }
            } finally {
                temp?.delete()
            }
        }

    /** 받기 (한 번 받은 파일은 캐시에서) */
    suspend fun download(key: String): File = withContext(Dispatchers.IO) {
        cached(key)?.let { return@withContext it }
        val conn = open("GET", "/object/authenticated/$BUCKET/$key")
        ensureOk(conn, keepOpen = true)
        conn.inputStream.use { writeCache(key, it) }
    }

    /** 지우기 (올린 사람만 됨). 이미 없으면 성공으로 봄 */
    suspend fun delete(key: String) = withContext(Dispatchers.IO) {
        cacheFile(key).delete()
        val conn = open("DELETE", "/object/$BUCKET/$key")
        try {
            ensureOk(conn)
        } catch (e: StorageException) {
            if (e.code != "NoSuchKey") throw e
        }
    }

    private fun cacheFile(key: String): File = File(cacheDir, key.replace('/', '_'))

    private fun writeCache(key: String, input: java.io.InputStream): File {
        val target = cacheFile(key)
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        part.outputStream().use { input.copyTo(it) }
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        return target
    }

    private fun idToken(): String {
        val user = FirebaseAuth.getInstance().currentUser ?: throw StorageException("NoUser", "not signed in")
        return Tasks.await(user.getIdToken(false)).token ?: throw StorageException("NoToken", "no id token")
    }

    private fun open(method: String, path: String): HttpURLConnection =
        (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("apikey", apiKey)
            setRequestProperty("Authorization", "Bearer ${idToken()}")
        }

    /** 2xx가 아니면 본문의 {statusCode, code, message}로 예외. 저장소는 대부분의 오류를 400으로 보냄 */
    private fun ensureOk(conn: HttpURLConnection, keepOpen: Boolean = false) {
        val status = conn.responseCode
        if (status in 200..299) {
            if (!keepOpen) runCatching { conn.inputStream.use { it.readBytes() } }
            return
        }
        val body = runCatching { conn.errorStream?.use { it.readBytes().decodeToString() } }.getOrNull().orEmpty()
        val json = runCatching { JSONObject(body) }.getOrNull()
        val code = json?.optString("code")?.takeIf { it.isNotEmpty() } ?: "HTTP$status"
        val message = json?.optString("message")?.takeIf { it.isNotEmpty() } ?: body.take(200)
        conn.disconnect()
        throw StorageException(code, message)
    }
}
