package com.modo.app.ui.root

import android.os.Bundle
import androidx.core.view.isVisible
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.databinding.TabScheduleBinding
import com.modo.app.logic.CalendarMath
import com.modo.app.logic.Schedule
import com.modo.app.logic.ScheduleItem
import com.modo.app.ui.common.Fmt
import com.modo.app.ui.common.Items
import com.modo.app.ui.nav.Nav
import com.modo.app.ui.task.TaskEditActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** 루트 "일정" 탭: 전 프로젝트 월간 달력 + 선택한 날 + 다가오는 일정 */
class ScheduleTab(private val activity: RootActivity, private val b: TabScheduleBinding) {

    private var data = AppData()
    private var month: YearMonth = YearMonth.now()
    private var selected: LocalDate = LocalDate.now()

    init {
        b.prevMonth.setOnClickListener { moveMonth(-1) }
        b.nextMonth.setOnClickListener { moveMonth(1) }
        b.thisMonth.setOnClickListener {
            month = YearMonth.now()
            selected = LocalDate.now()
            render(data)
        }
        b.calendar.onDaySelected = { date ->
            selected = date
            month = YearMonth.from(date)
            render(data)
        }
    }

    private fun moveMonth(delta: Long) {
        month = month.plusMonths(delta)
        selected = if (YearMonth.now() == month) LocalDate.now() else month.atDay(1)
        render(data)
    }

    fun render(data: AppData) {
        this.data = data
        val today = LocalDate.now()
        b.monthLabel.text = Fmt.month(activity, month)
        b.thisMonth.isVisible = month != YearMonth.now()
        val days = CalendarMath.monthGrid(month)
        b.calendar.setMonth(month, CalendarMath.markers(data, days, today), selected, today)

        b.dayTitle.text = if (selected == today) activity.getString(R.string.dot_join, activity.getString(R.string.today), Fmt.date(activity, selected))
        else Fmt.date(activity, selected)
        b.dayList.removeAllViews()
        val items = CalendarMath.itemsOn(data, selected)
        if (items.isEmpty()) Items.addEmpty(b.dayList, R.string.day_schedule_empty)
        items.forEach { add(b.dayList, it, today) }

        b.upcomingList.removeAllViews()
        val upcoming = Schedule.upcoming(data, LocalDateTime.now(), days = 7, count = 8)
        if (upcoming.isEmpty()) Items.addEmpty(b.upcomingList, R.string.schedule_empty)
        upcoming.forEach { add(b.upcomingList, it, today) }
    }

    private fun add(container: android.widget.LinearLayout, item: ScheduleItem, today: LocalDate) {
        Items.addSchedule(container, item, today) {
            when (item) {
                is ScheduleItem.TaskDue -> TaskEditActivity.start(activity, item.task.projectId, item.task.id)
                else -> Nav.openProject(activity, item.project.id)
            }
        }
    }

    fun saveState(out: Bundle) {
        out.putString(KEY_MONTH, month.toString())
        out.putString(KEY_SELECTED, selected.toString())
    }

    fun restoreState(state: Bundle) {
        state.getString(KEY_MONTH)?.let { month = YearMonth.parse(it) }
        state.getString(KEY_SELECTED)?.let { selected = LocalDate.parse(it) }
    }

    companion object {
        private const val KEY_MONTH = "schedule_month"
        private const val KEY_SELECTED = "schedule_selected"
    }
}
