package com.modo.app.data

import com.modo.app.logic.ProgressCalculator
import com.modo.app.logic.WeekCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SampleDataTest {

    private val labels = SampleData.Labels(
        projectNames = listOf("A", "B", "C"),
        projectDescriptions = listOf("", "", ""),
        leaderRole = "팀장",
        memberNames = listOf("민수", "서연", "지훈", "하은"),
        memberRoles = List(7) { "역할$it" },
        tasksA = List(7) { "a$it" },
        tasksB = List(9) { "b$it" },
        tasksC = List(5) { "c$it" },
    )

    private fun build(today: LocalDate): List<SampleData.SampleProject> {
        var n = 0
        return SampleData.build("me", "유진", today, labels, { "p${n++}" }, { "CODE$n" }, 0L)
    }

    @Test
    fun thisWeekRates_matchReferenceScreen_onAnyWeekday() {
        // 요일과 상관없이 75 / 40 / 20 이 나와야 함
        for (offset in 0L..6L) {
            val today = LocalDate.of(2026, 11, 2).plusDays(offset)
            val rates = build(today).map { s ->
                ProgressCalculator.weekRate(s.project, s.tasks, WeekCalculator.weekOf(s.project, today))
            }
            assertEquals("today=$today", listOf(75, 40, 20), rates)
        }
    }

    @Test
    fun totals_andAssigneesExist() {
        val samples = build(LocalDate.of(2026, 11, 6))
        val all = samples.flatMap { it.tasks }
        assertEquals(21, all.size)
        assertEquals(11, all.count { it.status == TaskStatus.DONE })
        for (s in samples) {
            val memberIds = s.members.map { it.id }.toSet()
            assertEquals(true, s.tasks.all { it.assigneeId in memberIds })
        }
    }
}
