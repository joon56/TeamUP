package com.teamup.app.ui.member

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.teamup.app.R
import com.teamup.app.data.AppData
import com.teamup.app.data.Member
import com.teamup.app.data.TaskStatus
import com.teamup.app.data.TeamRepository
import com.teamup.app.databinding.ActivityMemberDetailBinding
import com.teamup.app.logic.ProgressCalculator
import com.teamup.app.logic.TaskRules
import com.teamup.app.ui.common.Dialogs
import com.teamup.app.ui.common.Items
import com.teamup.app.ui.common.applySystemBarsPadding
import com.teamup.app.ui.common.colorOf
import com.teamup.app.ui.common.ensureSignedIn
import com.teamup.app.ui.common.failureToast
import com.teamup.app.ui.common.tintOf
import com.teamup.app.ui.common.toast
import com.teamup.app.ui.task.TaskEditActivity
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
            Dialogs.confirm(
                this, R.string.confirm_remove_member_title, R.string.confirm_remove_member_body, R.string.action_delete,
            ) {
                TeamRepository.removeMember(member, failureToast())
                finish()
            }
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
        val color = Items.avatarColor(this, member.name)

        // 프로필
        binding.topBar.title.text = member.name
        binding.avatarBg.imageTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x40))
        binding.initial.text = member.name.take(1)
        binding.initial.setTextColor(color)
        binding.name.text = if (isMe) getString(R.string.name_with_me, member.name) else member.name
        binding.role.text = member.role
        binding.role.isVisible = member.role.isNotBlank()

        val appUser = member.uid != null
        binding.kind.setText(if (appUser) R.string.member_app_user else R.string.member_manual)
        binding.kind.backgroundTintList = tintOf(if (appUser) R.color.primary_container else R.color.surface_high)
        binding.kind.setTextColor(colorOf(if (appUser) R.color.on_primary_container else R.color.text_secondary))

        // 완료 현황
        val tasks = data.tasksOf(projectId).filter { it.assigneeId == member.id }
        val done = tasks.count { it.status == TaskStatus.DONE }
        val rate = ProgressCalculator.rate(tasks)
        binding.statAssigned.text = tasks.size.toString()
        binding.statDone.text = done.toString()
        binding.statRate.text = if (rate == null) DASH else getString(R.string.percent, rate)
        binding.progress.setIndicatorColor(color)
        binding.progress.setProgressCompat(rate ?: 0, true)

        // 프로젝트를 만든 사람과 나 자신은 여기서 삭제할 수 없음
        val isOwner = member.id == project.ownerUid || member.uid == project.ownerUid
        binding.remove.isVisible = !isOwner && !isMe

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
