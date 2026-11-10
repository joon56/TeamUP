package com.modo.app.logic

import com.modo.app.data.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.random.Random

class SmallRulesTest {

    @Test
    fun inviteCode_hasSixUnambiguousChars() {
        val code = InviteCode.generate(Random(42))
        assertEquals(6, code.length)
        assertTrue(InviteCode.isValid(code))
        assertFalse(code.any { it in "IO01" })
    }

    @Test
    fun inviteCode_normalizesUserInput() {
        assertEquals("ABC2D3", InviteCode.normalize(" ab-c 2d3 "))
        assertFalse(InviteCode.isValid("ABC"))
        assertFalse(InviteCode.isValid("ABCDE0"))
    }

    @Test
    fun greeting_byHour() {
        assertEquals(Greeting.MORNING, greetingOf(7))
        assertEquals(Greeting.AFTERNOON, greetingOf(13))
        assertEquals(Greeting.EVENING, greetingOf(19))
        assertEquals(Greeting.NIGHT, greetingOf(23))
        assertEquals(Greeting.NIGHT, greetingOf(3))
    }

    private val project = Project(
        id = "p", name = "p",
        startDate = LocalDate.of(2026, 11, 2), endDate = LocalDate.of(2026, 12, 15),
        meetingDay = 3, meetingTime = LocalTime.of(19, 0),
    )

    @Test
    fun nextMeeting_laterToday() {
        val now = LocalDateTime.of(2026, 11, 11, 18, 0) // 수요일 18:00
        assertEquals(LocalDateTime.of(2026, 11, 11, 19, 0), Schedule.nextMeeting(project, now))
    }

    @Test
    fun nextMeeting_nextWeek_whenTodaysMeetingPassed() {
        val now = LocalDateTime.of(2026, 11, 11, 20, 0)
        assertEquals(LocalDateTime.of(2026, 11, 18, 19, 0), Schedule.nextMeeting(project, now))
    }

    @Test
    fun nextMeeting_beforeProjectStart_isFirstMeeting() {
        val now = LocalDateTime.of(2026, 10, 1, 9, 0)
        assertEquals(LocalDateTime.of(2026, 11, 4, 19, 0), Schedule.nextMeeting(project, now))
    }

    @Test
    fun nextMeeting_nullAfterProjectEnd() {
        assertNull(Schedule.nextMeeting(project, LocalDateTime.of(2026, 12, 16, 9, 0)))
    }
}
