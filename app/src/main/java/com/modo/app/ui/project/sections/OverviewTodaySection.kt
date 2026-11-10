package com.modo.app.ui.project.sections

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.Project
import com.modo.app.data.Task
import com.modo.app.data.TaskStatus
import com.modo.app.data.TeamRepository
import com.modo.app.databinding.ItemAttentionBinding
import com.modo.app.databinding.SectionOverviewTodayBinding
import com.modo.app.logic.ProgressCalculator
import com.modo.app.logic.Schedule
import com.modo.app.logic.TaskCategory
import com.modo.app.logic.TaskRules
import com.modo.app.logic.WeekCalculator
import com.modo.app.ui.common.Fmt
import com.modo.app.ui.common.Items
import com.modo.app.ui.common.Labels
import com.modo.app.ui.common.colorOf
import com.modo.app.ui.common.failureToast
import com.modo.app.ui.meeting.MeetingFinderActivity
import com.modo.app.ui.project.ProjectActivity
import com.modo.app.ui.task.TaskEditActivity
import java.time.LocalDate
import java.time.LocalDateTime

/** 시안 1 "오늘 중심": 이 프로젝트에서 지금 내가 해야 할 일을 먼저 */
class OverviewTodaySection(private val host: ProjectActivity, inflater: LayoutInflater, parent: ViewGroup) : Section {

    private val b = SectionOverviewTodayBinding.inflate(inflater, parent, false)
    override val root: View get() = b.root
    private var projectId = ""

    init {
        b.addTask.setOnClickListener { TaskEditActivity.start(host, projectId, null) }
    }

    override fun render(data: AppData, project: Project) {
        projectId = project.id
        val today = LocalDate.now()
        val tasks = data.tasksOf(project.id)
        renderToday(data, tasks, today)
        renderWeek(project, tasks, today)
        renderMeeting(project, today)
        renderDueSoon(data, tasks, today)
        renderPinned(data, project)
    }

    /** 내 업무 중 오늘 마감·지연·진행 중 */
    private fun renderToday(data: AppData, tasks: List<Task>, today: LocalDate) {
        b.todayList.removeAllViews()
        val mine = tasks.filter { task ->
            task.assigneeId == data.myUid && task.status != TaskStatus.DONE &&
                (!task.dueDate.isAfter(today) || TaskRules.category(task, today) == TaskCategory.IN_PROGRESS)
        }.sortedWith(compareBy<Task>({ it.dueDate }, { it.priority.ordinal }))
        if (mine.isEmpty()) {
            Items.addEmpty(b.todayList, R.string.overview_today_empty)
            return
        }
        for (task in mine) {
            Items.addTask(
                b.todayList, task, data, today, showProject = false,
                onToggle = { checked ->
                    TeamRepository.setTaskStatus(task, if (checked) TaskStatus.DONE else TaskStatus.TODO, host.failureToast())
                },
                onClick = { TaskEditActivity.start(host, task.projectId, task.id) },
            )
        }
    }

    private fun renderWeek(project: Project, tasks: List<Task>, today: LocalDate) {
        val week = WeekCalculator.weekOf(project, today)
        val rate = ProgressCalculator.weekRate(project, tasks, week)
        b.weekRate.text = Fmt.percent(host, rate)
        b.weekProgress.setIndicatorColor(host.colorOf(Labels.projectColor(project.colorIndex)))
        b.weekProgress.progress = rate ?: 0
        val range = WeekCalculator.rangeOf(project, week)
        b.weekRange.text = host.getString(
            R.string.overview_week_range, week,
            host.getString(R.string.range_format, Fmt.dateShort(host, range.start), Fmt.dateShort(host, range.endInclusive)),
        )
    }

    private fun renderMeeting(project: Project, today: LocalDate) {
        if (!project.meetingDecided) {
            b.meetingWhen.setText(R.string.meeting_undecided)
            b.meetingSub.setText(R.string.overview_find_meeting)
            b.meetingSub.setTextColor(host.colorOf(R.color.primary))
            b.meetingCard.setOnClickListener { MeetingFinderActivity.start(host, project.id) }
            return
        }
        val next = Schedule.nextMeeting(project, LocalDateTime.now())
        b.meetingWhen.text = if (next == null) host.getString(R.string.overview_no_meeting)
        else "${Fmt.relativeDay(host, next.toLocalDate(), today)} ${Fmt.time(host, next.toLocalTime())}"
        b.meetingSub.text = host.getString(
            R.string.schedule_meeting_regular, Fmt.dayTime(host, project.meetingDay, project.meetingTime), project.meetingDurationMin,
        )
        b.meetingSub.setTextColor(host.colorOf(R.color.text_tertiary))
        b.meetingCard.setOnClickListener { host.showTab(com.modo.app.ui.project.ProjectTab.SCHEDULE) }
    }

    /** 팀 전체에서 3일 안 마감 + 지연 */
    private fun renderDueSoon(data: AppData, tasks: List<Task>, today: LocalDate) {
        b.dueSoonCard.removeAllViews()
        val soon = tasks.filter { TaskRules.isOverdue(it, today) || TaskRules.isDueSoon(it, today, 3) }
            .sortedWith(compareBy<Task>({ it.dueDate }, { it.priority.ordinal }))
            .take(6)
        if (soon.isEmpty()) {
            val empty = android.widget.TextView(host).apply {
                setText(R.string.overview_due_soon_empty)
                setTextAppearance(R.style.TextAppearance_Modo_BodySecondary)
                val pad = resources.getDimensionPixelSize(R.dimen.gap_m)
                setPadding(pad, pad, pad, pad)
            }
            b.dueSoonCard.addView(empty)
            return
        }
        for (task in soon) {
            val row = ItemAttentionBinding.inflate(LayoutInflater.from(host), b.dueSoonCard, false)
            val overdue = TaskRules.isOverdue(task, today)
            val urgent = overdue || TaskRules.dDay(task.dueDate, today) == 0
            row.dday.text = Fmt.dDay(host, task.dueDate, today)
            row.dday.backgroundTintList = ColorStateList.valueOf(
                host.colorOf(if (urgent) R.color.danger_container else R.color.warning_container)
            )
            row.dday.setTextColor(host.colorOf(if (urgent) R.color.danger else R.color.warning))
            row.title.text = task.title
            row.project.text = host.getString(
                R.string.dot_join,
                Labels.assignee(host, data, task.projectId, task.assigneeId),
                Labels.category(host, TaskRules.category(task, today)),
            )
            row.root.setOnClickListener { TaskEditActivity.start(host, task.projectId, task.id) }
            b.dueSoonCard.addView(row.root)
        }
    }

    private fun renderPinned(data: AppData, project: Project) {
        val pinned = data.noticesOf(project.id).firstOrNull { it.pinned }
        b.pinnedCard.isVisible = pinned != null
        if (pinned == null) return
        b.pinnedTitle.text = pinned.title.ifBlank { pinned.body.lineSequence().firstOrNull().orEmpty() }
        b.pinnedBody.text = pinned.body
        b.pinnedCard.setOnClickListener { host.showTab(com.modo.app.ui.project.ProjectTab.CHAT) }
    }
}
