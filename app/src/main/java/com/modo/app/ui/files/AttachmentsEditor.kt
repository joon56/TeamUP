package com.modo.app.ui.files

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.modo.app.R
import com.modo.app.data.Attachment
import com.modo.app.data.TeamRepository
import com.modo.app.databinding.ItemAttachmentBinding
import com.modo.app.logic.FileRules
import kotlinx.coroutines.launch

/**
 * 업무·회의록 수정 화면의 "첨부 파일" 묶음.
 * 고르면 바로 올리고 목록에 붙인다. 저장할 때 commit()으로 프로젝트 파일함에 기록하고 뺀 파일을 정리하며,
 * 저장하지 않고 나가면 discard()로 이번에 올린 파일을 지운다.
 */
class AttachmentsEditor(
    private val activity: AppCompatActivity,
    private val list: LinearLayout,
    private val addButton: View,
    private val status: TextView,
    key: String,
    private val projectId: () -> String,
    private val onChanged: () -> Unit,
) {
    private val items = mutableListOf<Attachment>()
    private val added = mutableListOf<Attachment>()
    private val removed = mutableListOf<Attachment>()
    private val picker = AttachPicker(activity, key) { uri -> upload(uri) }
    private var uploading = 0

    val attachments: List<Attachment> get() = items.toList()

    init {
        addButton.isVisible = FileActions.isAvailable
        addButton.setOnClickListener { picker.showMenu(it) }
        status.isVisible = false
    }

    fun set(attachments: List<Attachment>) {
        items.clear()
        items += attachments
        render()
    }

    private fun upload(uri: android.net.Uri) {
        val picked = FileActions.describe(activity, uri)
        uploading++
        showStatus(picked.name, 0)
        activity.lifecycleScope.launch {
            val attachment = FileActions.upload(activity, picked, FileRules.Folder.Project(projectId())) { showStatus(picked.name, it) }
            uploading--
            if (uploading == 0) status.isVisible = false
            if (attachment != null) {
                items += attachment
                added += attachment
                render()
                onChanged()
            }
        }
    }

    private fun showStatus(name: String, percent: Int) {
        status.isVisible = true
        status.text = activity.getString(R.string.file_uploading, name, percent)
    }

    private fun render() {
        list.removeAllViews()
        list.isVisible = items.isNotEmpty()
        for (attachment in items) {
            val row = ItemAttachmentBinding.inflate(LayoutInflater.from(activity), list, false)
            bindRow(activity, row, attachment)
            row.remove.setOnClickListener {
                items.remove(attachment)
                // 이번에 올린 것은 바로 지우고, 원래 있던 것은 저장할 때 정리
                if (added.remove(attachment)) FileActions.deleteUploaded(activity, attachment, null) else removed += attachment
                render()
                onChanged()
            }
            list.addView(row.root)
        }
    }

    /** 저장한 뒤: 새 첨부는 프로젝트 파일함에 기록, 뺀 첨부는 정리 */
    fun commit(projectId: String, source: String, sourceId: String) {
        added.filter { it in items }.forEach { TeamRepository.addFile(projectId, it, source, sourceId) }
        removed.forEach { FileActions.deleteUploaded(activity, it, projectId) }
        added.clear()
        removed.clear()
    }

    /** 저장하지 않고 나감: 이번에 올린 파일 지우기 */
    fun discard() {
        added.forEach { FileActions.deleteUploaded(activity, it, null) }
        added.clear()
    }

    fun release() = picker.release()

    companion object {
        /** 첨부 한 줄 (아이콘 · 이름 · 크기, 누르면 열기). 빼기 버튼은 부르는 쪽에서 */
        fun bindRow(activity: AppCompatActivity, row: ItemAttachmentBinding, attachment: Attachment, meta: String? = null) {
            row.icon.setImageResource(if (attachment.isImage) R.drawable.ic_image else R.drawable.ic_doc)
            row.name.text = attachment.name
            row.meta.text = meta ?: FileActions.formatSize(attachment.size)
            row.row.setOnClickListener { FileActions.open(activity, attachment) }
        }
    }
}
