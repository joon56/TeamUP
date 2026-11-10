package com.modo.app.ui.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.ChatRepository
import com.modo.app.data.DataHub
import com.modo.app.data.FileMeta
import com.modo.app.data.TeamRepository
import com.modo.app.databinding.ActivityFilesBinding
import com.modo.app.databinding.ItemAttachmentBinding
import com.modo.app.logic.FileRules
import com.modo.app.ui.common.Dialogs
import com.modo.app.ui.common.Fmt
import com.modo.app.ui.common.Items
import com.modo.app.ui.common.applySystemBarsPadding
import com.modo.app.ui.common.ensureSignedIn
import com.modo.app.ui.common.toast
import kotlinx.coroutines.launch

/** 프로젝트 파일함: 채팅·업무·회의록에 첨부한 파일과 직접 올린 파일 (누르면 열기, 내가 올린 파일은 삭제) */
class FilesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFilesBinding
    private lateinit var projectId: String
    private lateinit var picker: AttachPicker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureSignedIn()) return
        projectId = intent.getStringExtra(EXTRA_PROJECT_ID) ?: return finish()
        enableEdgeToEdge()
        binding = ActivityFilesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        binding.topBar.title.setText(R.string.files_title)
        binding.topBar.back.setOnClickListener { finish() }
        picker = AttachPicker(this, "files.$projectId") { upload(it) }
        binding.upload.isVisible = FileActions.isAvailable
        binding.upload.setOnClickListener { picker.showMenu(it) }

        DataHub.data.observe(this) { render(it) }
    }

    override fun onDestroy() {
        if (::picker.isInitialized) picker.release()
        super.onDestroy()
    }

    private fun render(data: AppData) {
        val project = data.project(projectId)
        if (project == null) {
            if (!data.loading) finish()
            return
        }
        binding.topBar.title.text = getString(R.string.dot_join, getString(R.string.files_title), project.name)
        val files = data.filesOf(projectId).sortedByDescending { it.createdAt }
        binding.list.removeAllViews()
        if (files.isEmpty()) {
            Items.addEmpty(binding.list, R.string.files_empty)
            return
        }
        for (file in files) addRow(file)
    }

    private fun addRow(file: FileMeta) {
        val row = ItemAttachmentBinding.inflate(LayoutInflater.from(this), binding.list, false)
        val meta = getString(
            R.string.files_meta, FileActions.formatSize(file.attachment.size), file.uploadedByName.ifBlank { "-" },
            getString(R.string.dot_join, sourceLabel(file.source), Fmt.relativeTime(this, file.createdAt)),
        )
        AttachmentsEditor.bindRow(this, row, file.attachment, meta)
        // 내가 올린 파일만 지울 수 있음 (저장소 정책)
        row.remove.isVisible = file.attachment.uploadedBy == DataHub.uid
        row.remove.setImageResource(R.drawable.ic_delete)
        row.remove.contentDescription = getString(R.string.action_delete)
        row.remove.setOnClickListener {
            Dialogs.confirm(this, R.string.files_delete_confirm, sourceNote(file.source), R.string.action_delete) {
                // 올린 곳에서도 사라지게: 파일만 담은 채팅 메시지는 지우고, 업무·회의록은 첨부 목록에서 뺌
                if (file.source == "chat" && file.sourceId.isNotEmpty()) {
                    ChatRepository.deleteMessage(ChatRepository.roomIdForProject(projectId), file.sourceId)
                }
                TeamRepository.removeAttachment(file)
                FileActions.deleteUploaded(this, file.attachment, projectId)
                toast(R.string.files_deleted)
            }
        }
        binding.list.addView(row.root)
    }

    private fun sourceNote(source: String): Int? = when (source) {
        "chat" -> R.string.files_delete_note_chat
        "task" -> R.string.files_delete_note_task
        "minutes" -> R.string.files_delete_note_minutes
        else -> null
    }

    private fun sourceLabel(source: String): String = getString(
        when (source) {
            "chat" -> R.string.files_source_chat
            "task" -> R.string.files_source_task
            "minutes" -> R.string.files_source_minutes
            else -> R.string.files_source_direct
        }
    )

    private fun upload(uri: Uri) {
        val picked = FileActions.describe(this, uri)
        binding.uploadStatus.isVisible = true
        binding.uploadStatus.text = getString(R.string.file_uploading, picked.name, 0)
        lifecycleScope.launch {
            val attachment = FileActions.upload(this@FilesActivity, picked, FileRules.Folder.Project(projectId)) {
                binding.uploadStatus.text = getString(R.string.file_uploading, picked.name, it)
            }
            binding.uploadStatus.isVisible = false
            if (attachment != null) TeamRepository.addFile(projectId, attachment, "direct", "")
        }
    }

    companion object {
        private const val EXTRA_PROJECT_ID = "project_id"

        fun start(context: Context, projectId: String) {
            context.startActivity(Intent(context, FilesActivity::class.java).putExtra(EXTRA_PROJECT_ID, projectId))
        }
    }
}
