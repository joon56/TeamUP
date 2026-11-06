package com.teamup.app.ui.project

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import com.teamup.app.R
import com.teamup.app.data.AppData
import com.teamup.app.data.Project
import com.teamup.app.data.Task
import com.teamup.app.data.TaskStatus
import com.teamup.app.data.TeamRepository
import com.teamup.app.databinding.ActivityProjectDetailBinding
import com.teamup.app.logic.ProgressCalculator
import com.teamup.app.logic.TaskRules
import com.teamup.app.logic.WeekCalculator
import com.teamup.app.ui.common.Dialogs
import com.teamup.app.ui.common.Fmt
import com.teamup.app.ui.common.Items
import com.teamup.app.ui.common.Labels
import com.teamup.app.ui.common.applySystemBarsPadding
import com.teamup.app.ui.common.colorOf
import com.teamup.app.ui.common.ensureSignedIn
import com.teamup.app.ui.common.failureToast
import com.teamup.app.ui.common.toast
import com.teamup.app.ui.member.MemberDetailActivity
import com.teamup.app.ui.task.TaskEditActivity
import java.time.LocalDate

/** 프로젝트 상세: 정보·초대 코드 + [개요 / 업무 / 팀원] 세그먼트 */
class ProjectDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProjectDetailBinding
    private lateinit var projectId: String

    /** 지금 보고 있는 세그먼트 (화면 회전 시 유지) */
    private var segment = SEGMENT_OVERVIEW

    /** 한 번이라도 프로젝트를 받았는지 (그 뒤에 없어지면 삭제·나가기로 판단) */
    private var seenProject = false
    private var notFoundCheckPosted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureSignedIn()) return
        val id = intent.getStringExtra(EXTRA_PROJECT_ID)
        if (id == null) {
            finish()
            return
        }
        projectId = id
        enableEdgeToEdge()
        binding = ActivityProjectDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        segment = savedInstanceState?.getInt(KEY_SEGMENT) ?: SEGMENT_OVERVIEW

        // 상단 바: 뒤로 / 프로젝트 이름 / ⋯ 메뉴
        binding.topBar.title.setText(R.string.loading)
        binding.topBar.back.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.topBar.action1.isVisible = true
        binding.topBar.action1.setImageResource(R.drawable.ic_more)
        binding.topBar.action1.contentDescription = getString(R.string.action_more)
        binding.topBar.action1.setOnClickListener { anchor ->
            val project = currentProject() ?: return@setOnClickListener
            ProjectActions.showMenu(this, anchor, project, TeamRepository.current.myUid) { finish() }
        }

        // 초대 코드 복사 / 공유
        binding.copyCode.setOnClickListener { currentProject()?.let { ProjectActions.copyInvite(this, it) } }
        binding.shareCode.setOnClickListener { currentProject()?.let { ProjectActions.shareInvite(this, it) } }

        // 세그먼트 (RadioGroup): 선택된 내용만 보이게
        binding.segment.setOnCheckedChangeListener { _, checkedId ->
            segment = when (checkedId) {
                R.id.segTasks -> SEGMENT_TASKS
                R.id.segMembers -> SEGMENT_MEMBERS
                else -> SEGMENT_OVERVIEW
            }
            showSegment()
        }
        binding.segment.check(
            when (segment) {
                SEGMENT_TASKS -> R.id.segTasks
                SEGMENT_MEMBERS -> R.id.segMembers
                else -> R.id.segOverview
            }
        )
        showSegment()

        binding.addMember.setOnClickListener {
            Dialogs.showMember(this, null) { name, role ->
                TeamRepository.addMember(projectId, name, role, failureToast())
            }
        }
        binding.fab.setOnClickListener { TaskEditActivity.start(this, projectId, null) }

        // 아래로 스크롤하면 [업무 추가] 버튼을 아이콘만 남게 줄이고, 위로 올리면 다시 펼침
        binding.scroll.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                when {
                    scrollY > oldScrollY + 4 -> binding.fab.shrink()
                    scrollY < oldScrollY - 4 -> binding.fab.extend()
                    else -> Unit
                }
            }
        )

        TeamRepository.data.observe(this) { data -> render(data) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SEGMENT, segment)
    }

    private fun currentProject(): Project? = TeamRepository.current.project(projectId)

    private fun showSegment() {
        binding.overviewPane.isVisible = segment == SEGMENT_OVERVIEW
        binding.tasksPane.isVisible = segment == SEGMENT_TASKS
        binding.membersPane.isVisible = segment == SEGMENT_MEMBERS
    }

    private fun render(data: AppData) {
        val project = data.project(projectId)
        if (project == null) {
            if (seenProject) {
                // 다른 팀원이 삭제했거나 내가 나간 프로젝트
                if (!isFinishing) {
                    toast(R.string.project_not_found)
                    finish()
                }
            } else if (!data.loading && !notFoundCheckPosted) {
                // 방금 만들었거나 코드로 참여한 프로젝트는 서버에서 조금 늦게 도착할 수 있어 잠시 기다림
                notFoundCheckPosted = true
                binding.root.postDelayed({
                    if (!seenProject && !isFinishing) {
                        toast(R.string.project_not_found)
                        finish()
                    }
                }, NOT_FOUND_GRACE_MS)
            }
            return
        }
        seenProject = true

        val today = LocalDate.now()
        val color = colorOf(Labels.projectColor(project.colorIndex))
        val tasks = data.tasksOf(project.id)

        binding.topBar.title.text = project.name
        renderHeader(project, today, color)
        renderOverview(project, tasks, today, color)
        renderTasks(data, project, tasks, today)
        renderMembers(data, project, tasks)
    }

    // ───────────────────────── 정보 카드 ─────────────────────────

    private fun renderHeader(project: Project, today: LocalDate, color: Int) {
        Items.tintIconBox(binding.projectIcon, color)
        binding.projectName.text = project.name
        binding.description.text = project.description
        binding.description.isVisible = project.description.isNotBlank()

        val range = getString(
            R.string.range_format,
            Fmt.dateShort(this, project.startDate),
            Fmt.dateShort(this, project.endDate),
        )
        val dDay = if (project.endDate.isBefore(today)) getString(R.string.project_finished)
        else Fmt.dDay(this, project.endDate, today)
        binding.period.text = getString(R.string.dot_join, range, dDay)
        binding.meeting.text = getString(
            R.string.detail_meeting,
            Fmt.weekday(this, project.meetingDay, long = true),
            Fmt.time(this, project.meetingTime),
        )
        binding.inviteCode.text = project.inviteCode
    }

    // ───────────────────────── 개요 ─────────────────────────

    private fun renderOverview(project: Project, tasks: List<Task>, today: LocalDate, color: Int) {
        // 이번 주 진행률 (원형)
        val currentWeek = WeekCalculator.weekOf(project, today)
        val weekTasks = ProgressCalculator.tasksInWeek(project, tasks, currentWeek)
        val weekRate = ProgressCalculator.rate(weekTasks)
        binding.weekRing.setIndicatorColor(color)
        binding.weekRing.setProgressCompat(weekRate ?: 0, true)
        binding.weekRate.text = if (weekRate == null) DASH else getString(R.string.percent, weekRate)
        binding.weekLabel.text = getString(R.string.week_label, currentWeek)

        val range = WeekCalculator.rangeOf(project, currentWeek)
        val rangeText = getString(
            R.string.range_format,
            Fmt.dateShort(this, range.start),
            Fmt.dateShort(this, range.endInclusive),
        )
        val weekDone = weekTasks.count { it.status == TaskStatus.DONE }
        val weekSummary = if (weekTasks.isEmpty()) getString(R.string.no_tasks)
        else getString(R.string.done_of_total, weekDone, weekTasks.size)
        binding.weekRange.text = getString(R.string.dot_join, rangeText, weekSummary)

        // 전체 진행률 (막대)
        val overall = ProgressCalculator.rate(tasks)
        val done = tasks.count { it.status == TaskStatus.DONE }
        binding.overallProgress.setIndicatorColor(color)
        binding.overallProgress.setProgressCompat(overall ?: 0, true)
        binding.overallText.text = if (overall == null) getString(R.string.no_tasks)
        else getString(
            R.string.dot_join,
            getString(R.string.percent, overall),
            getString(R.string.done_of_total, done, tasks.size),
        )

        // 상태별 업무 2×2
        val counts = ProgressCalculator.statusCounts(tasks, today)
        binding.statTodo.text = counts.todo.toString()
        binding.statInProgress.text = counts.inProgress.toString()
        binding.statDone.text = counts.done.toString()
        binding.statOverdue.text = counts.overdue.toString()

        // 주차별 진행률 (이번 주 강조)
        binding.weekList.removeAllViews()
        for (week in ProgressCalculator.weeklyProgress(project, tasks)) {
            Items.addWeek(binding.weekList, week, week.index == currentWeek, color)
        }
    }

    // ───────────────────────── 업무 (주차별) ─────────────────────────

    private fun renderTasks(data: AppData, project: Project, tasks: List<Task>, today: LocalDate) {
        val pane = binding.tasksPane
        pane.removeAllViews()
        if (tasks.isEmpty()) {
            Items.addEmpty(pane, R.string.tasks_empty)
            return
        }
        val currentWeek = WeekCalculator.weekOf(project, today)
        // 마감일이 속한 주차로 묶고 주차 순서대로
        val byWeek = tasks.groupBy { WeekCalculator.weekOf(project, it.dueDate) }.toSortedMap()
        for ((week, weekTasks) in byWeek) {
            val range = WeekCalculator.rangeOf(project, week)
            val rangeText = getString(
                R.string.range_format,
                Fmt.dateShort(this, range.start),
                Fmt.dateShort(this, range.endInclusive),
            )
            var header = getString(R.string.dot_join, getString(R.string.week_label, week), rangeText)
            if (week == currentWeek) header = getString(R.string.dot_join, header, getString(R.string.week_current))
            Items.addHeader(pane, header)

            for (task in TaskRules.sortForList(weekTasks)) {
                Items.addTask(
                    pane, task, data, today, showProject = false,
                    onToggle = { checked ->
                        TeamRepository.setTaskStatus(
                            task, if (checked) TaskStatus.DONE else TaskStatus.TODO, failureToast(),
                        )
                    },
                    onClick = { TaskEditActivity.start(this, project.id, task.id) },
                )
            }
        }
        // 맨 위 주차 제목의 위쪽 여백은 없앰
        pane.getChildAt(0)?.let { first -> first.setPadding(first.paddingLeft, 0, first.paddingRight, first.paddingBottom) }
    }

    // ───────────────────────── 팀원 ─────────────────────────

    private fun renderMembers(data: AppData, project: Project, tasks: List<Task>) {
        binding.memberList.removeAllViews()
        val stats = ProgressCalculator.memberStats(data.membersOf(project.id), tasks)
        for (stat in stats) {
            val member = stat.member
            Items.addMember(binding.memberList, member, stat, isMe = member.id == data.myUid) {
                MemberDetailActivity.start(this, project.id, member.id)
            }
        }
    }

    companion object {
        const val EXTRA_PROJECT_ID = "project_id"

        private const val KEY_SEGMENT = "segment"
        private const val SEGMENT_OVERVIEW = 0
        private const val SEGMENT_TASKS = 1
        private const val SEGMENT_MEMBERS = 2
        private const val DASH = "–"
        private const val NOT_FOUND_GRACE_MS = 5000L

        fun start(context: Context, projectId: String) {
            context.startActivity(Intent(context, ProjectDetailActivity::class.java).putExtra(EXTRA_PROJECT_ID, projectId))
        }
    }
}
