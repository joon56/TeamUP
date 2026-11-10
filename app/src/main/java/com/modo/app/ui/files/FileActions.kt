package com.modo.app.ui.files

import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.modo.app.R
import com.modo.app.data.Attachment
import com.modo.app.ui.common.toast
import java.util.Locale

/**
 * 파일 첨부 공통 동작 (Supabase Storage 연결은 8단계).
 * 연결 전에는 첨부 버튼을 숨긴다.
 */
object FileActions {

    /** Supabase 프로젝트가 설정돼 있으면 true */
    val isAvailable: Boolean get() = false

    fun pickForChat(activity: AppCompatActivity, roomId: String) = activity.toast(R.string.msg_coming_soon)

    fun loadThumbnail(activity: AppCompatActivity, file: Attachment, target: ImageView) {
        target.setImageResource(R.drawable.ic_image)
    }

    fun open(activity: AppCompatActivity, file: Attachment) = activity.toast(R.string.msg_coming_soon)

    /** 1.2 MB / 350 KB */
    fun formatSize(bytes: Long): String = when {
        bytes >= 1_048_576 -> String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }
}
