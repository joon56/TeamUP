package com.modo.app.logic

import com.modo.app.data.AppData
import com.modo.app.data.Project
import com.modo.app.data.Task
import com.modo.app.data.TaskStatus
import java.time.LocalDate
import java.time.LocalDateTime

/** 일정 탭·홈 "다가오는 일정"에 표시하는 항목 */
sealed class ScheduleItem {
    abstract val project: Project
    abstract val dateTime: LocalDateTime
    val date: LocalDate get() = dateTime.toLocalDate()

    data class TaskDue(val task: Task, override val project: Project) : ScheduleItem() {
        override val dateTime: LocalDateTime get() = task.dueDate.atTime(23, 59)
    }

    data class Meeting(override val project: Project, override val dateTime: LocalDateTime) : ScheduleItem()

    data class ProjectEnd(override val project: Project) : ScheduleItem() {
        override val dateTime: LocalDateTime get() = project.endDate.atTime(23, 59)
    }
}

enum class ScheduleGroup { OVERDUE, TODAY, NEXT_7_DAYS, LATER }

object Schedule {

    /** now 이후 가장 가까운 정기 회의. 프로젝트가 끝났으면 null */
    fun nextMeeting(project: Project, now: LocalDateTime): LocalDateTime? {
        val from = maxOf(now.toLocalDate(), project.startDate)
        for (offset in 0..7) {
            val date = from.plusDays(offset.toLong())
            if (date.isAfter(project.endDate)) return null
            if (date.dayOfWeek.value != project.meetingDay) continue
            val meeting = date.atTime(project.meetingTime)
            if (!meeting.isBefore(now)) return meeting
        }
        return null
    }

    /** now부터 days일 안의 정기 회의 목록 */
    fun meetings(project: Project, now: LocalDateTime, days: Long): List<LocalDateTime> {
        val result = mutableListOf<LocalDateTime>()
        val limit = now.plusDays(days)
        var next = nextMeeting(project, now)
        while (next != null && !next.isAfter(limit)) {
            result.add(next)
            next = nextMeeting(project, next.plusMinutes(1))
        }
        return result
    }

    fun groupOf(item: ScheduleItem, today: LocalDate): ScheduleGroup = when {
        item is ScheduleItem.TaskDue && TaskRules.isOverdue(item.task, today) -> ScheduleGroup.OVERDUE
        item.date == today -> ScheduleGroup.TODAY
        !item.date.isAfter(today.plusDays(7)) -> ScheduleGroup.NEXT_7_DAYS
        else -> ScheduleGroup.LATER
    }

    /** 미완료 업무 마감 + 2주 안의 정기 회의 + 남은 프로젝트 마감일을 그룹별로 */
    fun timeline(data: AppData, now: LocalDateTime): Map<ScheduleGroup, List<ScheduleItem>> {
        val today = now.toLocalDate()
        val items = mutableListOf<ScheduleItem>()
        for (project in data.projects) {
            for (task in data.tasksOf(project.id)) {
                if (task.status != TaskStatus.DONE) items.add(ScheduleItem.TaskDue(task, project))
            }
            for (meeting in meetings(project, now, 14)) {
                items.add(ScheduleItem.Meeting(project, meeting))
            }
            if (!project.endDate.isBefore(today)) items.add(ScheduleItem.ProjectEnd(project))
        }
        return items.sortedBy { it.dateTime }.groupBy { groupOf(it, today) }.toSortedMap()
    }

    /** 홈 "다가오는 일정": 오늘부터 days일 안의 회의·마감 (지연 제외), 최대 count개 */
    fun upcoming(data: AppData, now: LocalDateTime, days: Long = 3, count: Int = 3): List<ScheduleItem> {
        val limit = now.toLocalDate().plusDays(days)
        val groups = timeline(data, now)
        val soon = groups[ScheduleGroup.TODAY].orEmpty() + groups[ScheduleGroup.NEXT_7_DAYS].orEmpty()
        return soon.filter { !it.date.isAfter(limit) }.take(count)
    }
}
