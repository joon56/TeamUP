package com.teamup.app.logic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekCalculatorTest {

    // 2026-11-02 = 월요일, 회의 = 수요일(3) → 첫 회의 11-04
    private val start = LocalDate.of(2026, 11, 2)
    private val wednesday = 3

    @Test
    fun firstMeeting_isStartDay_whenStartIsMeetingDay() {
        val wed = LocalDate.of(2026, 11, 4)
        assertEquals(wed, WeekCalculator.firstMeeting(wed, wednesday))
    }

    @Test
    fun firstMeeting_isNextMeetingDay_afterStart() {
        assertEquals(LocalDate.of(2026, 11, 4), WeekCalculator.firstMeeting(start, wednesday))
    }

    @Test
    fun weekIndex_firstWeekRunsFromStartUntilDayBeforeSecondMeeting() {
        assertEquals(1, WeekCalculator.weekIndex(start, wednesday, LocalDate.of(2026, 11, 2)))
        assertEquals(1, WeekCalculator.weekIndex(start, wednesday, LocalDate.of(2026, 11, 4)))
        assertEquals(1, WeekCalculator.weekIndex(start, wednesday, LocalDate.of(2026, 11, 10)))
    }

    @Test
    fun weekIndex_changesOnMeetingDay() {
        assertEquals(2, WeekCalculator.weekIndex(start, wednesday, LocalDate.of(2026, 11, 11)))
        assertEquals(2, WeekCalculator.weekIndex(start, wednesday, LocalDate.of(2026, 11, 17)))
        assertEquals(3, WeekCalculator.weekIndex(start, wednesday, LocalDate.of(2026, 11, 18)))
    }

    @Test
    fun weekIndex_beforeStart_isFirstWeek() {
        assertEquals(1, WeekCalculator.weekIndex(start, wednesday, LocalDate.of(2026, 10, 20)))
    }

    @Test
    fun weekRange_firstWeekStartsAtProjectStart() {
        val range = WeekCalculator.weekRange(start, wednesday, 1)
        assertEquals(LocalDate.of(2026, 11, 2), range.start)
        assertEquals(LocalDate.of(2026, 11, 10), range.endInclusive)
    }

    @Test
    fun weekRange_laterWeeksStartAtMeetingDay() {
        val range = WeekCalculator.weekRange(start, wednesday, 2)
        assertEquals(LocalDate.of(2026, 11, 11), range.start)
        assertEquals(LocalDate.of(2026, 11, 17), range.endInclusive)
    }

    @Test
    fun totalWeeks_isWeekOfEndDate() {
        assertEquals(6, WeekCalculator.totalWeeks(start, LocalDate.of(2026, 12, 15), wednesday))
        assertEquals(1, WeekCalculator.totalWeeks(start, start, wednesday))
    }
}
