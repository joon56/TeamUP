package com.modo.app.ui.project.sections

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.InviteRepository
import com.modo.app.data.InviteStatus
import com.modo.app.data.Project
import com.modo.app.databinding.ItemActivityBinding
import com.modo.app.databinding.SectionMembersBinding
import com.modo.app.logic.ProgressCalculator
import com.modo.app.ui.common.Fmt
import com.modo.app.ui.common.Items
import com.modo.app.ui.common.Labels
import com.modo.app.ui.common.failureToast
import com.modo.app.ui.member.InviteActivity
import com.modo.app.ui.member.MemberDetailActivity
import com.modo.app.ui.project.ProjectActions
import com.modo.app.ui.project.ProjectActivity

/** 팀원: 팀장·태그·진행률 목록, 보낸 초대(취소), 초대 코드 */
class MembersSection(private val host: ProjectActivity, inflater: LayoutInflater, parent: ViewGroup) : Section {

    private val b = SectionMembersBinding.inflate(inflater, parent, false)
    override val root: View get() = b.root
    private var project: Project? = null

    init {
        b.invite.setOnClickListener { project?.let { InviteActivity.start(host, it.id) } }
        b.copyCode.setOnClickListener { project?.let { ProjectActions.copyInvite(host, it) } }
        b.shareCode.setOnClickListener { project?.let { ProjectActions.shareInvite(host, it) } }
    }

    override fun render(data: AppData, project: Project) {
        this.project = project
        val members = data.membersOf(project.id)
        b.memberCount.text = host.getString(R.string.members_count, members.size)
        b.memberList.removeAllViews()
        addMembers(b.memberList, data, project, host)
        b.code.text = project.inviteCode

        val pending = data.invitesOf(project.id).filter { it.status == InviteStatus.PENDING }
        b.pendingTitle.isVisible = pending.isNotEmpty()
        b.pendingList.isVisible = pending.isNotEmpty()
        b.pendingList.removeAllViews()
        for (invite in pending) {
            val row = ItemActivityBinding.inflate(LayoutInflater.from(host), b.pendingList, false)
            row.icon.setImageResource(R.drawable.ic_mail)
            val color = Labels.avatarColor(host, -1, invite.toName)
            row.icon.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x30))
            row.icon.imageTintList = ColorStateList.valueOf(color)
            row.text.text = invite.toName
            row.time.text = host.getString(R.string.dot_join, Fmt.relativeTime(host, invite.createdAt), host.getString(R.string.members_cancel_invite))
            row.time.setOnClickListener { InviteRepository.cancel(invite, host.failureToast()) }
            b.pendingList.addView(row.root)
        }
    }

    companion object {
        /** 진행률 순서가 아니라 합류 순서. 팀장이 맨 위 */
        fun addMembers(container: LinearLayout, data: AppData, project: Project, host: ProjectActivity) {
            val stats = ProgressCalculator.memberStats(data.membersOf(project.id), data.tasksOf(project.id))
                .sortedBy { if (it.member.uid == project.ownerUid) 0 else 1 }
            for (stat in stats) {
                Items.addMember(
                    container, stat.member, stat, isMe = stat.member.uid == data.myUid,
                    isLeader = stat.member.uid != null && stat.member.uid == project.ownerUid,
                ) { MemberDetailActivity.start(host, project.id, stat.member.id) }
            }
        }
    }
}
