package com.modo.app.logic

import com.modo.app.data.AppData
import com.modo.app.data.Project
import com.modo.app.data.TaskStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** 날짜 칸에 찍을 표시 */
data class DayMarkers(
    val meeting: Boolean = false,
    val deadline: Boolean = false,
    val overdue: Boolean = false,
    /** 그날 일정이 있는 프로젝트 색 번호 (최대 3개) */
    val colors: List<Int> = emptyList(),
) {
    val isEmpty: Boolean get() = !meeting && !deadline
}

/** 주간 캘린더(홈)·월간 캘린더(일정 탭) 계산 */
object CalendarMath {

    fun weekStart(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)

    fun weekDays(date: LocalDate): List<LocalDate> {
        val start = weekStart(date)
        return (0L..6L).map { start.plusDays(it) }
    }

    /** 월간 달력 6주 × 7일 (월요일 시작) */
    fun monthGrid(month: YearMonth): List<LocalDate> {
        val start = weekStart(month.atDay(1))
        return (0L until 42L).map { start.plusDays(it) }
    }

    fun hasMeetingOn(project: Project, date: LocalDate): Boolean =
        project.meetingDecided &&
            !date.isBefore(project.startDate) && !date.isAfter(project.endDate) &&
            date.dayOfWeek.value == project.meetingDay

    /** 그날의 회의·업무 마감·프로젝트 종료 (시간순) */
    fun itemsOn(data: AppData, date: LocalDate): List<ScheduleItem> {
        val items = mutableListOf<ScheduleItem>()
        for (project in data.projects) {
            if (hasMeetingOn(project, date)) items.add(ScheduleItem.Meeting(project, date.atTime(project.meetingTime)))
            for (task in data.tasksOf(project.id)) {
                if (task.dueDate == date) items.add(ScheduleItem.TaskDue(task, project))
            }
            if (project.endDate == date) items.add(ScheduleItem.ProjectEnd(project))
        }
        return items.sortedWith(compareBy<ScheduleItem>({ it.dateTime }, { it is ScheduleItem.ProjectEnd }))
    }

    fun markers(data: AppData, dates: List<LocalDate>, today: LocalDate): Map<LocalDate, DayMarkers> =
        dates.associateWith { date ->
            var meeting = false
            var deadline = false
            var overdue = false
            val colors = linkedSetOf<Int>()
            for (project in data.projects) {
                var used = false
                if (hasMeetingOn(project, date)) {
                    meeting = true
                    used = true
                }
                for (task in data.tasksOf(project.id)) {
                    if (task.dueDate != date || task.status == TaskStatus.DONE) continue
                    deadline = true
                    used = true
                    if (TaskRules.isOverdue(task, today)) overdue = true
                }
                if (used) colors.add(project.colorIndex)
            }
            DayMarkers(meeting, deadline, overdue, colors.take(3))
        }
}
