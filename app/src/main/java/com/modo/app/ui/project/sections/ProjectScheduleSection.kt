package com.modo.app.ui.project.sections

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.Project
import com.modo.app.databinding.SectionProjectScheduleBinding
import com.modo.app.logic.CalendarMath
import com.modo.app.logic.Schedule
import com.modo.app.logic.ScheduleItem
import com.modo.app.ui.common.Fmt
import com.modo.app.ui.common.Items
import com.modo.app.ui.meeting.MeetingFinderActivity
import com.modo.app.ui.project.ProjectActivity
import com.modo.app.ui.task.TaskEditActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** 프로젝트 일정: 다음 회의(또는 회의 시간 찾기) + 이 프로젝트만의 월간 달력 */
class ProjectScheduleSection(private val host: ProjectActivity, inflater: LayoutInflater, parent: ViewGroup) : Section {

    private val b = SectionProjectScheduleBinding.inflate(inflater, parent, false)
    override val root: View get() = b.root
    private var month: YearMonth = YearMonth.now()
    private var selected: LocalDate = LocalDate.now()
    private var lastData = AppData()
    private var lastProject: Project? = null

    init {
        b.prevMonth.setOnClickListener { moveMonth(-1) }
        b.nextMonth.setOnClickListener { moveMonth(1) }
        b.calendar.onDaySelected = { date ->
            selected = date
            month = YearMonth.from(date)
            rerender()
        }
    }

    private fun moveMonth(delta: Long) {
        month = month.plusMonths(delta)
        selected = if (YearMonth.now() == month) LocalDate.now() else month.atDay(1)
        rerender()
    }

    private fun rerender() {
        lastProject?.let { render(lastData, it) }
    }

    override fun render(data: AppData, project: Project) {
        lastData = data
        lastProject = project
        val today = LocalDate.now()
        val only = data.copy(projects = listOf(project))

        // 다음 회의
        b.findMeeting.setOnClickListener { MeetingFinderActivity.start(host, project.id) }
        if (!project.meetingDecided) {
            b.meetingWhen.setText(R.string.schedule_meeting_undecided_title)
            b.meetingRule.setText(R.string.schedule_meeting_undecided_body)
        } else {
            val next = Schedule.nextMeeting(project, LocalDateTime.now())
            b.meetingWhen.text = if (next == null) host.getString(R.string.overview_no_meeting)
            else "${Fmt.relativeDay(host, next.toLocalDate(), today)} ${Fmt.time(host, next.toLocalTime())}"
            b.meetingRule.text = host.getString(
                R.string.schedule_meeting_regular, Fmt.dayTime(host, project.meetingDay, project.meetingTime), project.meetingDurationMin,
            )
        }

        // 이 프로젝트 달력
        b.monthLabel.text = Fmt.month(host, month)
        b.calendar.setMonth(month, CalendarMath.markers(only, CalendarMath.monthGrid(month), today), selected, today)
        b.dayTitle.text = if (selected == today) host.getString(R.string.dot_join, host.getString(R.string.today), Fmt.date(host, selected))
        else Fmt.date(host, selected)
        b.dayList.removeAllViews()
        val items = CalendarMath.itemsOn(only, selected)
        if (items.isEmpty()) Items.addEmpty(b.dayList, R.string.day_schedule_empty)
        for (item in items) {
            Items.addSchedule(b.dayList, item, today) {
                if (item is ScheduleItem.TaskDue) TaskEditActivity.start(host, item.task.projectId, item.task.id)
            }
        }
    }
}
