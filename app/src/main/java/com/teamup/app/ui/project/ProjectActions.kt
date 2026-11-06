package com.teamup.app.ui.project

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.PopupMenu
import com.teamup.app.R
import com.teamup.app.data.Project
import com.teamup.app.data.TeamRepository
import com.teamup.app.ui.common.Dialogs
import com.teamup.app.ui.common.failureToast
import com.teamup.app.ui.common.toast

/** 홈 카드 ⋯ 메뉴와 프로젝트 상세 화면이 함께 쓰는 동작 */
object ProjectActions {

    private const val MENU_EDIT = 1
    private const val MENU_SHARE = 2
    private const val MENU_DELETE = 3
    private const val MENU_LEAVE = 4

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

    /** ⋯ 메뉴: 수정 / 초대 코드 공유 / 삭제(만든 사람) 또는 나가기(그 외) */
    fun showMenu(activity: Activity, anchor: View, project: Project, myUid: String, onRemoved: () -> Unit = {}) {
        val popup = PopupMenu(activity, anchor)
        popup.menu.add(0, MENU_EDIT, 0, R.string.menu_edit_project)
        popup.menu.add(0, MENU_SHARE, 1, R.string.menu_share_code)
        if (project.ownerUid == myUid) {
            popup.menu.add(0, MENU_DELETE, 2, R.string.menu_delete_project)
        } else {
            popup.menu.add(0, MENU_LEAVE, 2, R.string.menu_leave_project)
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_EDIT -> ProjectEditActivity.start(activity, project.id)
                MENU_SHARE -> shareInvite(activity, project)
                MENU_DELETE -> confirmDelete(activity, project, onRemoved)
                MENU_LEAVE -> confirmLeave(activity, project, onRemoved)
            }
            true
        }
        popup.show()
    }
}
