package com.modo.app.logic

import java.util.Locale

/**
 * 첨부 파일 규칙 (Supabase Storage `modo-files` 버킷).
 * 저장소 키에는 한글을 쓸 수 없으므로 "폴더/UUID.확장자"로 저장하고, 원래 이름은 Firestore에 둔다.
 */
object FileRules {

    /** 무료 요금제 한 파일 최대 크기 */
    const val MAX_BYTES = 50L * 1024 * 1024

    sealed class Folder {
        /** 프로젝트 팀원이 함께 보는 파일 (채팅·업무·회의록·파일함) */
        data class Project(val projectId: String) : Folder()

        /** 1:1 채팅 */
        data class Direct(val roomId: String) : Folder()
    }

    private val safeKey = Regex("""^[A-Za-z0-9_/!.*'() &$=@;:+,?-]*$""")
    private val safeExt = Regex("^[a-z0-9]{1,8}$")

    fun key(folder: Folder, fileName: String, uuid: String): String {
        val dir = when (folder) {
            is Folder.Project -> "projects/${folder.projectId}"
            is Folder.Direct -> "dm/${folder.roomId}"
        }
        val ext = extension(fileName)
        return if (ext == null) "$dir/$uuid" else "$dir/$uuid.$ext"
    }

    /** "보고서.PDF" → "pdf". 영문·숫자 8자 이하만 */
    fun extension(fileName: String): String? =
        fileName.substringAfterLast('.', "").lowercase(Locale.ROOT).takeIf { it.matches(safeExt) }

    fun isSafeKey(key: String): Boolean = key.matches(safeKey)

    fun fits(bytes: Long): Boolean = bytes in 1..MAX_BYTES

    private val byExtension = mapOf(
        "pdf" to "application/pdf", "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png",
        "gif" to "image/gif", "webp" to "image/webp", "heic" to "image/heic", "txt" to "text/plain",
        "hwp" to "application/x-hwp", "hwpx" to "application/vnd.hancom.hwpx", "zip" to "application/zip",
        "doc" to "application/msword", "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel", "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint", "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    )

    /** 기기가 알려 준 형식이 없거나 octet-stream이면 확장자로 추정 (예: 한글 .hwp) */
    fun mimeOf(reported: String?, fileName: String): String {
        if (!reported.isNullOrBlank() && reported != "application/octet-stream") return reported
        return extension(fileName)?.let { byExtension[it] } ?: "application/octet-stream"
    }
}
