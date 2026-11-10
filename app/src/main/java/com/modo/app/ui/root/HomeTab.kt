package com.modo.app.ui.root

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.Project
import com.modo.app.data.Task
import com.modo.app.data.TaskStatus
import com.modo.app.databinding.ItemAttentionBinding
import com.modo.app.databinding.ItemHomeAddBinding
import com.modo.app.databinding.ItemHomeProjectBinding
import com.modo.app.databinding.TabHomeBinding
import com.modo.app.logic.CalendarMath
import com.modo.app.logic.ProgressCalculator
import com.modo.app.logic.Schedule
import com.modo.app.logic.ScheduleItem
import com.modo.app.logic.TaskCategory
import com.modo.app.logic.TaskRules
import com.modo.app.logic.WeekCalculator
import com.modo.app.ui.common.Dialogs
import com.modo.app.ui.common.Fmt
import com.modo.app.ui.common.Items
import com.modo.app.ui.common.Labels
import com.modo.app.ui.common.addPressEffect
import com.modo.app.ui.common.colorOf
import com.modo.app.ui.nav.Nav
import com.modo.app.ui.project.ProjectEditActivity
import com.modo.app.ui.task.TaskEditActivity
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 루트 홈 (회의 피드백 2): 숫자 박스 대신
 * 주간 캘린더 한 줄 → 마감 임박 띠 → 진행 중 프로젝트 가로 카드 → 선택한 날 일정
 */
class HomeTab(private val activity: RootActivity, private val b: TabHomeBinding) {

    private var data = AppData()
    private var selected: LocalDate = LocalDate.now()
    private var weekAnchor: LocalDate = LocalDate.now()
    private var showAllAttention = false

    init {
        b.prevWeek.setOnClickListener { shiftWeek(-1) }
        b.nextWeek.setOnClickListener { shiftWeek(1) }
        b.thisWeek.setOnClickListener {
            weekAnchor = LocalDate.now()
            selected = weekAnchor
            render(data)
        }
        b.weekStrip.onDaySelected = { date ->
            selected = date
            render(data)
        }
        b.weekStrip.onSwipe = { shiftWeek(it) }
        b.attentionMore.setOnClickListener {
            showAllAttention = !showAllAttention
            render(data)
        }
    }

    private fun shiftWeek(delta: Int) {
        val today = LocalDate.now()
        weekAnchor = weekAnchor.plusWeeks(delta.toLong())
        val days = CalendarMath.weekDays(weekAnchor)
        selected = if (today in days) today else days.first()
        render(data)
    }

    fun render(data: AppData) {
        this.data = data
        val today = LocalDate.now()
        renderGreeting(data, today)
        renderWeek(data, today)
        renderAttention(data, today)
        renderProjects(data, today)
        renderDay(data, today)
    }

    private fun renderGreeting(data: AppData, today: LocalDate) {
        b.greeting.text = activity.getString(R.string.home_hello, data.myName)
        val mine = data.allTasks.filter { it.assigneeId == data.myUid }
        val dueToday = mine.count { it.dueDate == today && it.status != TaskStatus.DONE }
        val doing = mine.count { TaskRules.category(it, today) == TaskCategory.IN_PROGRESS }
        b.summary.text = if (dueToday + doing == 0) activity.getString(R.string.home_today_free)
        else activity.getString(R.string.home_today_count, dueToday, doing)
    }

    private fun renderWeek(data: AppData, today: LocalDate) {
        val days = CalendarMath.weekDays(weekAnchor)
        b.weekLabel.text = Fmt.weekOfMonth(activity, days.first())
        b.thisWeek.isVisible = today !in days
        b.weekStrip.setDays(days, CalendarMath.markers(data, days, today), selected, today)
    }

    /** 내 업무 중 지연 또는 D-2 이내 (회의 피드백 2: 알림처럼 위에서 강조) */
    private fun renderAttention(data: AppData, today: LocalDate) {
        val items = data.projects.flatMap { project ->
            data.tasksOf(project.id)
                .filter { it.assigneeId == data.myUid && TaskRules.needsAttention(it, today) }
                .map { it to project }
        }.sortedWith(compareBy({ it.first.dueDate }, { it.first.priority.ordinal }))
        b.attentionCard.isVisible = items.isNotEmpty()
        if (items.isEmpty()) return
        b.attentionTitle.text = activity.getString(R.string.dot_join, activity.getString(R.string.attention_title), items.size.toString())
        val shown = if (showAllAttention) items else items.take(ATTENTION_LIMIT)
        b.attentionMore.isVisible = items.size > ATTENTION_LIMIT
        b.attentionMore.text = if (showAllAttention) activity.getString(R.string.action_close)
        else activity.getString(R.string.attention_more, items.size - ATTENTION_LIMIT)
        b.attentionList.removeAllViews()
        for ((task, project) in shown) addAttentionRow(task, project, today)
    }

    private fun addAttentionRow(task: Task, project: Project, today: LocalDate) {
        val row = ItemAttentionBinding.inflate(LayoutInflater.from(activity), b.attentionList, false)
        val overdue = TaskRules.isOverdue(task, today)
        val dDay = TaskRules.dDay(task.dueDate, today)
        val urgent = overdue || dDay == 0
        row.dday.text = Fmt.dDay(activity, task.dueDate, today)
        row.dday.backgroundTintList = ColorStateList.valueOf(activity.colorOf(if (urgent) R.color.danger else R.color.warning))
        row.dday.setTextColor(activity.colorOf(R.color.on_primary))
        row.title.text = task.title
        row.project.text = if (overdue) activity.getString(R.string.dot_join, project.name, activity.getString(R.string.status_overdue))
        else project.name
        row.root.setOnClickListener { TaskEditActivity.start(activity, task.projectId, task.id) }
        b.attentionList.addView(row.root)
    }

    private fun renderProjects(data: AppData, today: LocalDate) {
        val ongoing = data.projects.filter { !it.endDate.isBefore(today) }
        b.projectCount.text = ongoing.size.toString()
        b.projectRow.removeAllViews()
        val now = LocalDateTime.now()
        for (project in ongoing) {
            val card = ItemHomeProjectBinding.inflate(LayoutInflater.from(activity), b.projectRow, false)
            val color = activity.colorOf(Labels.projectColor(project.colorIndex))
            Items.tintIconBox(card.icon, color)
            card.mode.text = Labels.mode(activity, project.mode)
            card.mode.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x30))
            card.mode.setTextColor(color)
            card.name.text = project.name
            val dDay = Fmt.dDay(activity, project.endDate, today)
            card.subject.text = if (project.subject.isBlank()) dDay else activity.getString(R.string.dot_join, project.subject, dDay)
            val tasks = data.tasksOf(project.id)
            val rate = ProgressCalculator.weekRate(project, tasks, WeekCalculator.weekOf(project, today))
            card.progress.setIndicatorColor(color)
            card.progress.progress = rate ?: 0
            card.rate.text = if (rate == null) activity.getString(R.string.this_week_none) else activity.getString(R.string.percent, rate)
            val next = Schedule.nextMeeting(project, now)
            card.meta.text = when {
                !project.meetingDecided -> activity.getString(R.string.meeting_undecided)
                next == null -> activity.getString(R.string.project_finished)
                else -> activity.getString(
                    R.string.next_meeting_format,
                    "${Fmt.relativeDay(activity, next.toLocalDate(), today)} ${Fmt.time(activity, next.toLocalTime())}",
                )
            }
            card.root.setOnClickListener { Nav.openProject(activity, project.id) }
            card.root.addPressEffect()
            b.projectRow.addView(card.root)
        }
        // 프로젝트가 없으면 가로 줄 대신 안내 카드 (만들기 · 코드로 참여)
        b.projectScroll.isVisible = ongoing.isNotEmpty()
        b.emptyProjects.isVisible = ongoing.isEmpty()
        if (ongoing.isEmpty()) {
            val handle = data.profile?.takeIf { it.tag.isNotEmpty() }?.handle ?: activity.getString(R.string.me_tag_pending)
            b.emptyBody.text = activity.getString(R.string.home_first_body, handle)
            b.emptyNew.setOnClickListener { ProjectEditActivity.start(activity, null) }
            b.emptyJoin.setOnClickListener { Dialogs.showJoin(activity) { projectId -> Nav.openProject(activity, projectId) } }
            return
        }
        val add = ItemHomeAddBinding.inflate(LayoutInflater.from(activity), b.projectRow, false)
        add.root.setOnClickListener { chooseAdd() }
        b.projectRow.addView(add.root)
    }

    private fun chooseAdd() {
        MaterialAlertDialogBuilder(activity)
            .setItems(arrayOf(activity.getString(R.string.switcher_new_project), activity.getString(R.string.home_join_code))) { _, which ->
                if (which == 0) ProjectEditActivity.start(activity, null)
                else Dialogs.showJoin(activity) { projectId -> Nav.openProject(activity, projectId) }
            }
            .show()
    }

    private fun renderDay(data: AppData, today: LocalDate) {
        val label = when (selected) {
            today -> activity.getString(R.string.dot_join, activity.getString(R.string.today), Fmt.date(activity, selected))
            else -> Fmt.date(activity, selected)
        }
        b.dayTitle.text = activity.getString(R.string.day_schedule_title, label)
        b.dayList.removeAllViews()
        val items = CalendarMath.itemsOn(data, selected)
        if (items.isEmpty()) {
            Items.addEmpty(b.dayList, R.string.day_schedule_empty)
            return
        }
        for (item in items) {
            Items.addSchedule(b.dayList, item, today) {
                when (item) {
                    is ScheduleItem.TaskDue -> TaskEditActivity.start(activity, item.task.projectId, item.task.id)
                    else -> Nav.openProject(activity, item.project.id)
                }
            }
        }
    }

    fun saveState(out: Bundle) {
        out.putString(KEY_SELECTED, selected.toString())
        out.putString(KEY_WEEK, weekAnchor.toString())
    }

    fun restoreState(state: Bundle) {
        state.getString(KEY_SELECTED)?.let { selected = LocalDate.parse(it) }
        state.getString(KEY_WEEK)?.let { weekAnchor = LocalDate.parse(it) }
    }

    companion object {
        private const val ATTENTION_LIMIT = 3
        private const val KEY_SELECTED = "home_selected"
        private const val KEY_WEEK = "home_week"
    }
}
