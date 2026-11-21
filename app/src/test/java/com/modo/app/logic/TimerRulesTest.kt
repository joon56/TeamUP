package com.modo.app.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerRulesTest {

    private val minute = 60_000L

    @Test
    fun `시작할 때 끝나는 시각은 지금에서 고른 분만큼 뒤`() {
        val timer = TimerRules.start(minutes = 30, now = 1_000)
        assertEquals(1_000 + 30 * minute, timer.endAt)
        assertEquals(30 * minute, timer.totalMs)
        assertEquals(30, timer.minutes)
    }

    @Test
    fun `남은 시간은 끝나는 시각까지이고 지나면 0`() {
        val timer = TimerRules.start(10, now = 0)
        assertEquals(10 * minute, TimerRules.remaining(timer, now = 0))
        assertEquals(4 * minute, TimerRules.remaining(timer, now = 6 * minute))
        assertEquals(0, TimerRules.remaining(timer, now = 10 * minute))
        assertEquals(0, TimerRules.remaining(timer, now = 11 * minute))
    }

    @Test
    fun `지난 비율은 0에서 100까지`() {
        val timer = TimerRules.start(10, now = 0)
        assertEquals(0, TimerRules.percent(timer, now = 0))
        assertEquals(50, TimerRules.percent(timer, now = 5 * minute))
        assertEquals(100, TimerRules.percent(timer, now = 10 * minute))
        assertEquals(100, TimerRules.percent(timer, now = 20 * minute))
    }

    @Test
    fun `길이가 0이면 비율은 100`() {
        assertEquals(100, TimerRules.percent(MeetingTimer(endAt = 0, totalMs = 0), now = 0))
    }

    @Test
    fun `남은 시간 표시는 초를 올림해서 분 초로`() {
        assertEquals("30:00", TimerRules.clock(30 * minute))
        assertEquals("30:00", TimerRules.clock(30 * minute - 500)) // 0.5초 지남: 남은 1799.5초를 올림해 아직 30:00
        assertEquals("29:59", TimerRules.clock(30 * minute - 1_000))
    }

    @Test
    fun `1초보다 적게 남아도 끝나기 전에는 1초로 보임`() {
        assertEquals("0:01", TimerRules.clock(1))
        assertEquals("0:00", TimerRules.clock(0))
    }

    @Test
    fun `한 시간 이상이면 시 분 초`() {
        assertEquals("1:00:00", TimerRules.clock(60 * minute))
        assertEquals("1:30:05", TimerRules.clock(90 * minute + 5_000))
    }

    @Test
    fun `시간 선택창에서 고른 시 분을 분으로`() {
        assertEquals(1, TimerRules.minutesOf(hour = 0, minute = 1))
        assertEquals(90, TimerRules.minutesOf(hour = 1, minute = 30))
        assertEquals(0, TimerRules.minutesOf(hour = 0, minute = 0))
    }
}
