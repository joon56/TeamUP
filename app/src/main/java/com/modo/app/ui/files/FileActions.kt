package com.modo.app.ui.files

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.util.LruCache
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.modo.app.R
import com.modo.app.data.Attachment
import com.modo.app.data.DataHub
import com.modo.app.data.FileStore
import com.modo.app.data.TeamRepository
import com.modo.app.logic.FileRules
import com.modo.app.ui.common.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

/** 파일 첨부 공통 동작: 고른 파일 정보 읽기 · 올리기 · 열기 · 사진 미리보기 · 지우기 */
object FileActions {

    private const val TAG = "FileActions"
    private const val THUMB_PX = 720

    /** Supabase 설정이 들어 있으면 true (없으면 첨부 버튼을 숨김) */
    val isAvailable: Boolean get() = FileStore.isConfigured

    /** 고른 파일: 원래 이름(한글 가능) · 크기 · 형식 */
    data class Picked(val uri: Uri, val name: String, val size: Long, val mime: String)

    fun describe(context: Context, uri: Uri): Picked {
        var name = "file"
        var size = -1L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        val mime = FileRules.mimeOf(context.contentResolver.getType(uri), name)
        // 사진 선택기는 "23.png"처럼 번호만 주므로 알아보기 쉬운 이름으로
        if (mime.startsWith("image/") && name.matches(Regex("""^\d+(\.\w+)?$"""))) {
            val stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            name = "IMG_$stamp" + (FileRules.extension(name)?.let { ".$it" } ?: "")
        }
        return Picked(uri, name, size, mime)
    }

    /**
     * 올리고 첨부 정보를 돌려줌. 너무 크거나 실패하면 토스트로 알리고 null.
     * onProgress(0~100)는 메인 스레드.
     */
    suspend fun upload(activity: AppCompatActivity, picked: Picked, folder: FileRules.Folder, onProgress: (Int) -> Unit = {}): Attachment? {
        if (picked.size > FileRules.MAX_BYTES) {
            activity.toast(R.string.file_too_big)
            return null
        }
        val key = FileRules.key(folder, picked.name, UUID.randomUUID().toString())
        if (!FileRules.isSafeKey(key)) {
            activity.toast(R.string.file_upload_failed)
            return null
        }
        return try {
            FileStore.upload(activity, picked.uri, key, picked.mime, picked.size, onProgress)
            val size = if (picked.size > 0) picked.size else FileStore.cached(key)?.length() ?: 0L
            Attachment(picked.name, size, picked.mime, key, DataHub.uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "upload", e)
            activity.toast(if ((e as? FileStore.StorageException)?.code == "EntityTooLarge") R.string.file_too_big else R.string.file_upload_failed)
            null
        }
    }

    /** 받아서 다른 앱(사진·PDF·문서 보기)으로 열기 */
    fun open(activity: AppCompatActivity, file: Attachment) {
        if (FileStore.cached(file.path) == null) activity.toast(R.string.file_downloading)
        activity.lifecycleScope.launch {
            try {
                val local = FileStore.download(file.path)
                // 받은 파일은 "UUID.확장자"라 다른 앱에 원래 이름이 보이도록 이름을 붙여 복사
                val named = withContext(Dispatchers.IO) { namedCopy(activity, local, file.name) }
                val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", named)
                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, file.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                activity.startActivity(Intent.createChooser(intent, file.name))
            } catch (e: ActivityNotFoundException) {
                activity.toast(R.string.file_no_app)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "open", e)
                activity.toast(R.string.file_open_failed)
            }
        }
    }

    private fun namedCopy(context: Context, source: File, displayName: String): File {
        val dir = File(context.cacheDir, "modo-open").apply { mkdirs() }
        val safe = displayName.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { source.name }
        return File(dir, safe).also { if (!it.exists() || it.length() != source.length()) source.copyTo(it, overwrite = true) }
    }

    private val thumbnails = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /**
     * 사진 미리보기 (작게 줄여서). 다른 메시지에 재사용된 ImageView에는 붙이지 않음.
     * onLoaded: 내려받아 늦게 붙였을 때만 호출 (높이가 바뀌므로 스크롤 보정용)
     */
    fun loadThumbnail(activity: AppCompatActivity, file: Attachment, target: ImageView, onLoaded: () -> Unit = {}) {
        target.tag = file.path
        thumbnails.get(file.path)?.let {
            target.scaleType = ImageView.ScaleType.CENTER_CROP
            target.setImageBitmap(it)
            return
        }
        // 받는 동안(또는 지워진 파일)은 작은 사진 아이콘만 가운데에
        target.scaleType = ImageView.ScaleType.CENTER
        target.setImageResource(R.drawable.ic_image)
        activity.lifecycleScope.launch {
            val bitmap = try {
                withContext(Dispatchers.IO) { decodeScaled(FileStore.download(file.path)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "thumbnail", e)
                null
            } ?: return@launch
            thumbnails.put(file.path, bitmap)
            if (target.tag == file.path) {
                target.scaleType = ImageView.ScaleType.CENTER_CROP
                target.setImageBitmap(bitmap)
                onLoaded()
            }
        }
    }

    private fun decodeScaled(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= THUMB_PX || bounds.outHeight / (sample * 2) >= THUMB_PX) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /**
     * 첨부를 지움: 프로젝트 파일함 기록 + (내가 올린 것이면) 저장소 파일.
     * 다른 사람이 올린 파일은 저장소 정책상 지울 수 없어 기록만 정리한다.
     */
    fun deleteUploaded(activity: AppCompatActivity, file: Attachment, projectId: String?) {
        if (projectId != null) {
            DataHub.current.filesOf(projectId).filter { it.attachment.path == file.path }.forEach { TeamRepository.deleteFile(it) }
        }
        if (file.uploadedBy != DataHub.uid) return
        activity.lifecycleScope.launch {
            try {
                FileStore.delete(file.path)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "delete", e)
            }
        }
    }

    /** 1.2 MB / 350 KB */
    fun formatSize(bytes: Long): String = when {
        bytes >= 1_048_576 -> String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }
}
