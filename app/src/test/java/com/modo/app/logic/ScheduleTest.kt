package com.modo.app.logic

import com.modo.app.data.AppData
import com.modo.app.data.Project
import com.modo.app.data.Task
import com.modo.app.data.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduleTest {

    private val project = Project(
        id = "p", name = "앱 개발",
        startDate = LocalDate.of(2026, 11, 2), endDate = LocalDate.of(2026, 12, 15),
        meetingDay = 3, meetingTime = LocalTime.of(19, 0),
    )
    private val now = LocalDateTime.of(2026, 11, 9, 10, 0) // 월요일

    private fun task(id: String, due: LocalDate, status: TaskStatus = TaskStatus.TODO) =
        Task(id = id, projectId = "p", title = id, dueDate = due, status = status)

    private fun data(vararg tasks: Task) = AppData(
        myUid = "me", projects = listOf(project), tasks = mapOf("p" to tasks.toList()), loading = false,
    )

    @Test
    fun timeline_groupsOverdueTodayAndUpcoming_excludingDone() {
        val groups = Schedule.timeline(
            data(
                task("late", LocalDate.of(2026, 11, 6)),
                task("today", LocalDate.of(2026, 11, 9)),
                task("soon", LocalDate.of(2026, 11, 12)),
                task("done", LocalDate.of(2026, 11, 12), TaskStatus.DONE),
            ),
            now,
        )
        assertEquals(listOf("late"), groups[ScheduleGroup.OVERDUE]!!.map { (it as ScheduleItem.TaskDue).task.id })
        assertEquals(listOf("today"), groups[ScheduleGroup.TODAY]!!.map { (it as ScheduleItem.TaskDue).task.id })
        val next = groups[ScheduleGroup.NEXT_7_DAYS]!!
        assertTrue(next.any { it is ScheduleItem.Meeting && it.dateTime == LocalDateTime.of(2026, 11, 11, 19, 0) })
        assertTrue(next.none { it is ScheduleItem.TaskDue && it.task.id == "done" })
    }

    @Test
    fun meetings_listsWeeklyMeetingsWithinRange() {
        val list = Schedule.meetings(project, now, 14)
        assertEquals(
            listOf(LocalDateTime.of(2026, 11, 11, 19, 0), LocalDateTime.of(2026, 11, 18, 19, 0)),
            list,
        )
    }

    @Test
    fun upcoming_takesSoonestItemsWithinThreeDays() {
        val items = Schedule.upcoming(data(task("soon", LocalDate.of(2026, 11, 10)), task("far", LocalDate.of(2026, 11, 20))), now)
        assertEquals(2, items.size)
        assertTrue(items[0] is ScheduleItem.TaskDue)
        assertTrue(items[1] is ScheduleItem.Meeting)
    }
}
