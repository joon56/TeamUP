package com.modo.app.logic

import com.modo.app.data.Member
import com.modo.app.data.Project
import com.modo.app.data.Task
import com.modo.app.data.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ProgressCalculatorTest {

    private val project = Project(
        id = "p1", name = "앱 개발",
        startDate = LocalDate.of(2026, 11, 2), endDate = LocalDate.of(2026, 12, 15),
        meetingDay = 3,
    )

    private fun task(id: String, due: LocalDate, status: TaskStatus, assignee: String? = null) =
        Task(id = id, projectId = "p1", title = id, dueDate = due, status = status, assigneeId = assignee)

    @Test
    fun rate_isNull_whenNoTasks() {
        assertNull(ProgressCalculator.rate(emptyList()))
    }

    @Test
    fun rate_isFlooredPercentOfDoneTasks() {
        val d = LocalDate.of(2026, 11, 5)
        val tasks = listOf(task("a", d, TaskStatus.DONE), task("b", d, TaskStatus.TODO), task("c", d, TaskStatus.IN_PROGRESS))
        assertEquals(33, ProgressCalculator.rate(tasks))
        assertEquals(100, ProgressCalculator.rate(listOf(task("a", d, TaskStatus.DONE))))
        assertEquals(0, ProgressCalculator.rate(listOf(task("a", d, TaskStatus.TODO))))
    }

    @Test
    fun weekRate_countsOnlyTasksDueInThatWeek() {
        val tasks = listOf(
            task("w1", LocalDate.of(2026, 11, 6), TaskStatus.DONE),
            task("w2a", LocalDate.of(2026, 11, 12), TaskStatus.DONE),
            task("w2b", LocalDate.of(2026, 11, 13), TaskStatus.TODO),
        )
        assertEquals(100, ProgressCalculator.weekRate(project, tasks, 1))
        assertEquals(50, ProgressCalculator.weekRate(project, tasks, 2))
        assertNull(ProgressCalculator.weekRate(project, tasks, 3))
    }

    @Test
    fun weeklyProgress_coversAllWeeksOfProject() {
        val tasks = listOf(task("w2", LocalDate.of(2026, 11, 12), TaskStatus.DONE))
        val weeks = ProgressCalculator.weeklyProgress(project, tasks)
        assertEquals(6, weeks.size)
        assertEquals(1, weeks[1].total)
        assertEquals(1, weeks[1].done)
        assertEquals(0, weeks[0].total)
    }

    @Test
    fun weeklyProgress_extendsToTasksDueAfterEndDate() {
        // 첫 회의 11-04 + 56일 = 12-30 → 9주차
        val tasks = listOf(task("late", LocalDate.of(2026, 12, 30), TaskStatus.TODO))
        assertEquals(9, ProgressCalculator.weeklyProgress(project, tasks).size)
    }

    @Test
    fun memberStats_countsAssignedTasksPerMember() {
        val members = listOf(Member("u1", "p1", "유진"), Member("u2", "p1", "민수"))
        val d = LocalDate.of(2026, 11, 5)
        val tasks = listOf(
            task("a", d, TaskStatus.DONE, "u1"),
            task("b", d, TaskStatus.TODO, "u1"),
            task("c", d, TaskStatus.DONE, "u2"),
            task("d", d, TaskStatus.TODO, null),
        )
        val stats = ProgressCalculator.memberStats(members, tasks)
        assertEquals(2, stats[0].total)
        assertEquals(1, stats[0].done)
        assertEquals(50, stats[0].rate)
        assertEquals(100, stats[1].rate)
    }

    @Test
    fun statusCounts_overdueIsItsOwnBucket() {
        val today = LocalDate.of(2026, 11, 10)
        val tasks = listOf(
            task("a", LocalDate.of(2026, 11, 9), TaskStatus.TODO),
            task("b", LocalDate.of(2026, 11, 9), TaskStatus.DONE),
            task("c", LocalDate.of(2026, 11, 12), TaskStatus.IN_PROGRESS),
            task("d", LocalDate.of(2026, 11, 11), TaskStatus.TODO),
        )
        val counts = ProgressCalculator.statusCounts(tasks, today)
        assertEquals(4, counts.total)
        assertEquals(1, counts.todo)
        assertEquals(1, counts.inProgress)
        assertEquals(1, counts.done)
        assertEquals(1, counts.overdue)
    }
}
