package com.modo.app.logic

import com.modo.app.data.AppData
import com.modo.app.data.Member
import com.modo.app.data.Minutes
import com.modo.app.data.Notice
import com.modo.app.data.Task
import com.modo.app.data.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ActivityFeedTest {

    private val day = LocalDate.of(2026, 11, 11)

    @Test
    fun mergesEventsNewestFirst_andSkipsUnfinishedTasks() {
        val data = AppData(
            members = mapOf("p" to listOf(Member("m1", "p", "민수", joinedAt = 100))),
            tasks = mapOf(
                "p" to listOf(
                    Task("t1", "p", "완료한 일", dueDate = day, status = TaskStatus.DONE, assigneeId = "m1", completedAt = 400),
                    Task("t2", "p", "진행 중", dueDate = day, status = TaskStatus.IN_PROGRESS, updatedAt = 900),
                )
            ),
            notices = mapOf("p" to listOf(Notice("n1", "p", "공지", "", "u", "유진", createdAt = 300))),
            minutes = mapOf("p" to listOf(Minutes("x", "p", "회의록", day, createdAt = 200))),
        )
        val events = ActivityFeed.build(data, "p")
        assertEquals(listOf(400L, 300L, 200L, 100L), events.map { it.at })
        assertEquals("민수", (events.first() as ActivityEvent.TaskCompleted).assigneeName)
    }

    @Test
    fun respectsLimit_andIgnoresMissingTimes() {
        val tasks = (1..20).map { Task("t$it", "p", "t$it", dueDate = day, status = TaskStatus.DONE, completedAt = it.toLong()) } +
            Task("old", "p", "old", dueDate = day, status = TaskStatus.DONE, completedAt = null)
        val events = ActivityFeed.build(AppData(tasks = mapOf("p" to tasks)), "p", limit = 5)
        assertEquals(listOf(20L, 19L, 18L, 17L, 16L), events.map { it.at })
    }
}
