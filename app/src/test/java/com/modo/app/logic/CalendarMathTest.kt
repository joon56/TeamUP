package com.modo.app.logic

import com.modo.app.data.AppData
import com.modo.app.data.Project
import com.modo.app.data.Task
import com.modo.app.data.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class CalendarMathTest {

    private val wed = LocalDate.of(2026, 11, 11) // 수요일

    @Test
    fun weekStart_isMonday() {
        assertEquals(LocalDate.of(2026, 11, 9), CalendarMath.weekStart(wed))
        assertEquals(LocalDate.of(2026, 11, 9), CalendarMath.weekStart(LocalDate.of(2026, 11, 15))) // 일요일
        assertEquals(7, CalendarMath.weekDays(wed).size)
    }

    @Test
    fun monthGrid_startsOnMondayAndHas42Days() {
        val grid = CalendarMath.monthGrid(YearMonth.of(2026, 11))
        assertEquals(42, grid.size)
        assertEquals(LocalDate.of(2026, 10, 26), grid.first()) // 11/1(일)이 들어 있는 주의 월요일
        assertTrue(LocalDate.of(2026, 11, 30) in grid)
    }

    private val project = Project(
        id = "p", name = "P", startDate = LocalDate.of(2026, 11, 2), endDate = LocalDate.of(2026, 11, 20),
        meetingDay = 2, meetingTime = LocalTime.of(19, 0),
    )

    @Test
    fun itemsOn_meetingOnMeetingDayInsideProjectRange() {
        val data = AppData(projects = listOf(project))
        assertTrue(CalendarMath.itemsOn(data, LocalDate.of(2026, 11, 10)).any { it is ScheduleItem.Meeting }) // 화
        assertFalse(CalendarMath.itemsOn(data, LocalDate.of(2026, 11, 11)).any { it is ScheduleItem.Meeting })
        assertFalse(CalendarMath.itemsOn(data, LocalDate.of(2026, 11, 24)).any { it is ScheduleItem.Meeting }) // 끝난 뒤
    }

    @Test
    fun itemsOn_undecidedMeetingIsHidden() {
        val data = AppData(projects = listOf(project.copy(meetingDecided = false)))
        assertFalse(CalendarMath.itemsOn(data, LocalDate.of(2026, 11, 10)).any { it is ScheduleItem.Meeting })
    }

    @Test
    fun itemsOn_includesDeadlinesAndProjectEnd_sortedByTime() {
        val task = Task(id = "t", projectId = "p", title = "t", dueDate = LocalDate.of(2026, 11, 20), status = TaskStatus.TODO)
        val data = AppData(projects = listOf(project), tasks = mapOf("p" to listOf(task)))
        val items = CalendarMath.itemsOn(data, LocalDate.of(2026, 11, 20))
        assertTrue(items.any { it is ScheduleItem.TaskDue })
        assertTrue(items.any { it is ScheduleItem.ProjectEnd })
    }

    @Test
    fun markers_flagMeetingsDeadlinesAndOverdue() {
        val late = Task(id = "a", projectId = "p", title = "a", dueDate = LocalDate.of(2026, 11, 9), status = TaskStatus.TODO)
        val data = AppData(projects = listOf(project), tasks = mapOf("p" to listOf(late)))
        val markers = CalendarMath.markers(data, CalendarMath.weekDays(wed), today = wed)
        assertTrue(markers.getValue(LocalDate.of(2026, 11, 9)).overdue)
        assertTrue(markers.getValue(LocalDate.of(2026, 11, 10)).meeting)
        assertFalse(markers.getValue(LocalDate.of(2026, 11, 12)).deadline)
    }
}
