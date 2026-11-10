package com.modo.app.ui.member

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.ChatRepository
import com.modo.app.data.Member
import com.modo.app.data.TaskStatus
import com.modo.app.data.TeamRepository
import com.modo.app.databinding.ActivityMemberDetailBinding
import com.modo.app.logic.ProgressCalculator
import com.modo.app.logic.TaskRules
import com.modo.app.ui.chat.ChatRoomActivity
import com.modo.app.ui.common.Dialogs
import com.modo.app.ui.common.Items
import com.modo.app.ui.common.Labels
import com.modo.app.ui.common.applySystemBarsPadding
import com.modo.app.ui.common.colorOf
import com.modo.app.ui.common.ensureSignedIn
import com.modo.app.ui.common.failureToast
import com.modo.app.ui.common.tintOf
import com.modo.app.ui.common.toast
import com.modo.app.ui.meeting.AvailabilityActivity
import com.modo.app.ui.meeting.MeetingFinderActivity
import com.modo.app.ui.task.TaskEditActivity
import java.time.LocalDate

/** 팀원 상세: 프로필, 완료 현황, 담당 업무 목록 */
class MemberDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMemberDetailBinding
    private lateinit var projectId: String
    private lateinit var memberId: String

    /** 한 번이라도 팀원 정보를 받았는지 (그 뒤에 없어지면 삭제된 것) */
    private var seenMember = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureSignedIn()) return
        val pid = intent.getStringExtra(EXTRA_PROJECT_ID)
        val mid = intent.getStringExtra(EXTRA_MEMBER_ID)
        if (pid == null || mid == null) {
            finish()
            return
        }
        projectId = pid
        memberId = mid
        enableEdgeToEdge()
        binding = ActivityMemberDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        // 상단 바: 뒤로 / 이름 / 수정
        binding.topBar.title.setText(R.string.loading)
        binding.topBar.back.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.topBar.action1.isVisible = true
        binding.topBar.action1.setImageResource(R.drawable.ic_edit)
        binding.topBar.action1.contentDescription = getString(R.string.action_edit)
        binding.topBar.action1.setOnClickListener {
            val member = currentMember() ?: return@setOnClickListener
            Dialogs.showMember(this, member) { name, role ->
                TeamRepository.updateMember(member, name, role, failureToast())
            }
        }

        binding.remove.setOnClickListener {
            val member = currentMember() ?: return@setOnClickListener
            if (member.isAppUser) {
                // 앱 사용자 내보내기 (팀장만)
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.confirm_kick_title, member.name))
                    .setMessage(R.string.confirm_kick_body)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.member_kick) { _, _ ->
                        TeamRepository.removeMember(member, failureToast())
                        finish()
                    }
                    .show()
            } else {
                Dialogs.confirm(
                    this, R.string.confirm_remove_member_title, R.string.confirm_remove_member_body, R.string.action_delete,
                ) {
                    TeamRepository.removeMember(member, failureToast())
                    finish()
                }
            }
        }
        binding.directChat.setOnClickListener {
            val member = currentMember() ?: return@setOnClickListener
            val otherUid = member.uid ?: return@setOnClickListener
            val roomId = ChatRepository.ensureDirectRoom(otherUid, member.name, failureToast())
            ChatRoomActivity.start(this, roomId)
        }
        binding.transferLead.setOnClickListener {
            val member = currentMember() ?: return@setOnClickListener
            val project = TeamRepository.current.project(projectId) ?: return@setOnClickListener
            val newOwner = member.uid ?: return@setOnClickListener
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.confirm_transfer_title, member.name))
                .setMessage(R.string.confirm_transfer_body)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.member_transfer_lead) { _, _ ->
                    TeamRepository.transferLeadership(project, newOwner, failureToast())
                    toast(getString(R.string.msg_transferred, member.name))
                }
                .show()
        }

        TeamRepository.data.observe(this) { data -> render(data) }
    }

    private fun currentMember(): Member? = TeamRepository.current.member(projectId, memberId)

    private fun render(data: AppData) {
        val project = data.project(projectId)
        val member = data.member(projectId, memberId)
        if (project == null || member == null) {
            // 팀원 목록은 프로젝트보다 조금 늦게 도착하므로, 목록을 받은 뒤에도 없을 때만 닫음
            val membersLoaded = data.members.containsKey(projectId)
            val missing = seenMember || (!data.loading && (project == null || membersLoaded))
            if (missing && !isFinishing) {
                toast(R.string.member_not_found)
                finish()
            }
            return
        }
        seenMember = true

        val isMe = member.id == data.myUid
        val color = Labels.avatarColor(this, member.avatarColor, member.name)

        // 프로필
        binding.topBar.title.text = member.name
        binding.avatarBg.imageTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x40))
        binding.initial.text = member.name.take(1)
        binding.initial.setTextColor(color)
        binding.name.text = if (isMe) getString(R.string.name_with_me, member.name) else member.name
        binding.role.text = member.role
        binding.role.isVisible = member.role.isNotBlank()

        val appUser = member.isAppUser
        val memberIsLeader = appUser && member.uid == project.ownerUid
        binding.kind.text = when {
            memberIsLeader -> getString(R.string.members_leader)
            appUser && member.tag.isNotEmpty() -> member.handle
            appUser -> getString(R.string.member_app_user)
            else -> getString(R.string.members_guest)
        }
        binding.kind.backgroundTintList = tintOf(if (appUser) R.color.primary_container else R.color.surface_high)
        binding.kind.setTextColor(colorOf(if (appUser) R.color.on_primary_container else R.color.text_secondary))

        // 1:1 채팅은 앱 사용자에게만, 팀장 넘기기는 내가 팀장일 때 다른 앱 사용자에게만
        val iAmLeader = project.ownerUid == data.myUid
        binding.directChat.isVisible = appUser && !isMe
        binding.transferLead.isVisible = iAmLeader && appUser && !isMe
        binding.actions.isVisible = binding.directChat.isVisible || binding.transferLead.isVisible

        // 가능 시간: 나와 앱을 안 쓰는 팀원은 바로 편집, 다른 팀원은 팀 전체 히트맵으로
        val week = member.availability
        binding.availabilitySummary.text = if (week == null || week.isEmpty) getString(R.string.member_availability_none)
        else getString(R.string.me_availability_summary, AvailabilityActivity.hours(week))
        binding.availabilityCard.setOnClickListener {
            if (isMe || !appUser) AvailabilityActivity.startForMember(this, projectId, member.id)
            else MeetingFinderActivity.start(this, projectId)
        }

        // 완료 현황
        val tasks = data.tasksOf(projectId).filter { it.assigneeId == member.id }
        val done = tasks.count { it.status == TaskStatus.DONE }
        val rate = ProgressCalculator.rate(tasks)
        binding.statAssigned.text = tasks.size.toString()
        binding.statDone.text = done.toString()
        binding.statRate.text = if (rate == null) DASH else getString(R.string.percent, rate)
        binding.progress.setIndicatorColor(color)
        binding.progress.setProgressCompat(rate ?: 0, true)

        // 앱 안 쓰는 팀원은 누구나 삭제, 앱 사용자는 팀장만 내보내기 (팀장 자신·나는 제외)
        binding.remove.isVisible = !isMe && !memberIsLeader && (!appUser || iAmLeader)
        binding.remove.setText(if (appUser) R.string.member_kick else R.string.member_remove)

        // 담당 업무 목록
        val today = LocalDate.now()
        binding.taskList.removeAllViews()
        for (task in TaskRules.sortForList(tasks)) {
            Items.addTask(
                binding.taskList, task, data, today, showProject = false,
                onToggle = { checked ->
                    TeamRepository.setTaskStatus(
                        task, if (checked) TaskStatus.DONE else TaskStatus.TODO, failureToast(),
                    )
                },
                onClick = { TaskEditActivity.start(this, projectId, task.id) },
            )
        }
        if (tasks.isEmpty()) Items.addEmpty(binding.taskList, R.string.member_no_tasks)
    }

    companion object {
        const val EXTRA_PROJECT_ID = "project_id"
        const val EXTRA_MEMBER_ID = "member_id"

        private const val DASH = "–"

        fun start(context: Context, projectId: String, memberId: String) {
            context.startActivity(
                Intent(context, MemberDetailActivity::class.java)
                    .putExtra(EXTRA_PROJECT_ID, projectId)
                    .putExtra(EXTRA_MEMBER_ID, memberId)
            )
        }
    }
}
