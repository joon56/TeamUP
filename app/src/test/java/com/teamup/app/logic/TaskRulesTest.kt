package com.teamup.app.logic

import com.teamup.app.data.Priority
import com.teamup.app.data.Task
import com.teamup.app.data.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskRulesTest {

    private val today = LocalDate.of(2026, 11, 5)

    private fun task(
        due: LocalDate,
        status: TaskStatus = TaskStatus.TODO,
        assignee: String? = null,
        priority: Priority = Priority.MEDIUM,
        id: String = "t",
    ) = Task(id = id, projectId = "p", title = id, dueDate = due, status = status, assigneeId = assignee, priority = priority)

    @Test
    fun isOverdue_onlyWhenPastDueAndNotDone() {
        assertTrue(TaskRules.isOverdue(task(today.minusDays(1)), today))
        assertFalse(TaskRules.isOverdue(task(today.minusDays(1), TaskStatus.DONE), today))
        assertFalse(TaskRules.isOverdue(task(today), today))
    }

    @Test
    fun dDay_isDaysUntilDate() {
        assertEquals(0, TaskRules.dDay(today, today))
        assertEquals(3, TaskRules.dDay(today.plusDays(3), today))
        assertEquals(-2, TaskRules.dDay(today.minusDays(2), today))
    }

    @Test
    fun isDueSoon_withinThreeDaysAndNotDone() {
        assertTrue(TaskRules.isDueSoon(task(today), today))
        assertTrue(TaskRules.isDueSoon(task(today.plusDays(3)), today))
        assertFalse(TaskRules.isDueSoon(task(today.plusDays(4)), today))
        assertFalse(TaskRules.isDueSoon(task(today.minusDays(1)), today))
        assertFalse(TaskRules.isDueSoon(task(today, TaskStatus.DONE), today))
    }

    @Test
    fun postponeDate_movesOneWeek() {
        assertEquals(LocalDate.of(2026, 11, 10), TaskRules.postponeDate(LocalDate.of(2026, 11, 3), today))
    }

    @Test
    fun postponeDate_keepsMovingUntilNotPast() {
        assertEquals(LocalDate.of(2026, 11, 10), TaskRules.postponeDate(LocalDate.of(2026, 10, 13), today))
    }

    @Test
    fun filter_byStatusAndOwner() {
        val tasks = listOf(
            task(today.minusDays(1), TaskStatus.TODO, "me", id = "overdue"),
            task(today.plusDays(1), TaskStatus.IN_PROGRESS, "me", id = "doing"),
            task(today.plusDays(2), TaskStatus.DONE, "other", id = "done"),
            task(today.plusDays(2), TaskStatus.TODO, "other", id = "todo"),
        )
        assertEquals(listOf("overdue"), TaskRules.filter(tasks, TaskFilter.OVERDUE, false, "me", today).map { it.id })
        assertEquals(listOf("overdue", "todo"), TaskRules.filter(tasks, TaskFilter.TODO, false, "me", today).map { it.id })
        assertEquals(listOf("overdue", "doing"), TaskRules.filter(tasks, TaskFilter.ALL, true, "me", today).map { it.id })
    }

    @Test
    fun sortForList_openTasksByDueDateThenPriority_doneLast() {
        val tasks = listOf(
            task(today.plusDays(1), TaskStatus.DONE, id = "done"),
            task(today.plusDays(2), priority = Priority.HIGH, id = "later"),
            task(today.plusDays(1), priority = Priority.LOW, id = "soonLow"),
            task(today.plusDays(1), priority = Priority.HIGH, id = "soonHigh"),
        )
        assertEquals(listOf("soonHigh", "soonLow", "later", "done"), TaskRules.sortForList(tasks).map { it.id })
    }
}
