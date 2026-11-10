package com.modo.app.ui.project

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.PopupMenu
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.modo.app.R
import com.modo.app.data.Project
import com.modo.app.data.ProjectMode
import com.modo.app.data.TeamRepository
import com.modo.app.ui.common.Dialogs
import com.modo.app.ui.common.Labels
import com.modo.app.ui.common.colorOf
import com.modo.app.ui.common.failureToast
import com.modo.app.ui.common.toast
import com.modo.app.ui.files.FilesActivity

/** 프로젝트 화면 ⋯ 메뉴와 섹션들이 함께 쓰는 동작 */
object ProjectActions {

    private const val MENU_MODE = 1
    private const val MENU_EDIT = 2
    private const val MENU_SHARE = 3
    private const val MENU_FILES = 4
    private const val MENU_DELETE = 5
    private const val MENU_LEAVE = 6

    /** 카톡 등으로 초대 코드 보내기 (공유 시트) */
    fun shareInvite(context: Context, project: Project) {
        val text = context.getString(R.string.invite_share_text, project.name, project.inviteCode)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, context.getString(R.string.invite_share_title)))
    }

    fun copyInvite(context: Context, project: Project) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.invite_code), project.inviteCode))
        context.toast(R.string.msg_copied)
    }

    fun confirmDelete(activity: Activity, project: Project, onDone: () -> Unit) {
        Dialogs.confirm(
            activity, R.string.confirm_delete_project_title, R.string.confirm_delete_project_body, R.string.action_delete,
        ) {
            TeamRepository.deleteProject(project, activity.failureToast())
            onDone()
        }
    }

    fun confirmLeave(activity: Activity, project: Project, onDone: () -> Unit) {
        Dialogs.confirm(
            activity, R.string.confirm_leave_title, R.string.confirm_leave_body, R.string.menu_leave_project,
        ) {
            TeamRepository.leaveProject(project, activity.failureToast())
            onDone()
        }
    }

    /** 화면 모드(플로우 시안 1~4) 고르기. 팀 전체에 적용 */
    fun chooseMode(activity: Activity, project: Project) {
        val modes = ProjectMode.entries
        val items = modes.map { mode ->
            SpannableStringBuilder().apply {
                val title = Labels.mode(activity, mode)
                append(title)
                setSpan(StyleSpan(Typeface.BOLD), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                append("\n")
                val start = length
                append(Labels.modeDescription(activity, mode))
                setSpan(RelativeSizeSpan(0.86f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(activity.colorOf(R.color.text_secondary)), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }.toTypedArray<CharSequence>()
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.mode_dialog_title)
            .setSingleChoiceItems(items, modes.indexOf(project.mode)) { dialog, which ->
                val mode = modes[which]
                if (mode != project.mode) {
                    TeamRepository.setMode(project.id, mode, activity.failureToast())
                    activity.toast(activity.getString(R.string.mode_changed, Labels.mode(activity, mode)))
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /** ⋯ 메뉴: 화면 모드 / 프로젝트 설정 / 초대 코드 공유 / 파일함 / 삭제(팀장) 또는 나가기 */
    fun showMenu(activity: Activity, anchor: View, project: Project, myUid: String, onRemoved: () -> Unit = {}) {
        val popup = PopupMenu(activity, anchor)
        popup.menu.add(0, MENU_MODE, 0, R.string.more_mode)
        popup.menu.add(0, MENU_EDIT, 1, R.string.more_settings)
        popup.menu.add(0, MENU_SHARE, 2, R.string.menu_share_code)
        popup.menu.add(0, MENU_FILES, 3, R.string.more_files)
        if (project.ownerUid == myUid) {
            popup.menu.add(0, MENU_DELETE, 4, R.string.menu_delete_project)
        } else {
            popup.menu.add(0, MENU_LEAVE, 4, R.string.menu_leave_project)
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_MODE -> chooseMode(activity, project)
                MENU_EDIT -> ProjectEditActivity.start(activity, project.id)
                MENU_SHARE -> shareInvite(activity, project)
                MENU_FILES -> FilesActivity.start(activity, project.id)
                MENU_DELETE -> confirmDelete(activity, project, onRemoved)
                MENU_LEAVE -> confirmLeave(activity, project, onRemoved)
            }
            true
        }
        popup.show()
    }
}
