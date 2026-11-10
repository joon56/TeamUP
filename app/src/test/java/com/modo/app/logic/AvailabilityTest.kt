package com.modo.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class AvailabilityTest {

    @Test
    fun withAndIsFree_setOnlyTheGivenSlot() {
        val week = WeekAvailability().with(day = 2, slot = 38, free = true) // 화 19:00
        assertTrue(week.isFree(2, 38))
        assertFalse(week.isFree(2, 37))
        assertFalse(week.isFree(3, 38))
        assertEquals(1, week.freeSlotCount())
        assertFalse(week.isFree(2, 38).not())
        assertTrue(week.with(2, 38, false).isEmpty)
    }

    @Test
    fun withRange_paintsHalfOpenRange_andLastSlotWorks() {
        val week = WeekAvailability().withRange(day = 7, from = 46, toExclusive = 48, free = true)
        assertTrue(week.isFree(7, 46))
        assertTrue(week.isFree(7, 47)) // 23:30~24:00 (비트 47)
        assertEquals(2, week.freeSlotCount())
    }

    @Test
    fun fromAny_readsFirestoreNumbers_andRejectsWrongShape() {
        val week = WeekAvailability.fromAny(listOf(1L, 2, 0, 0, 0, 0, 4.0))!!
        assertTrue(week.isFree(1, 0))
        assertTrue(week.isFree(2, 1))
        assertTrue(week.isFree(7, 2))
        assertNull(WeekAvailability.fromAny(listOf(1L, 2L)))
        assertNull(WeekAvailability.fromAny("x"))
        assertNull(WeekAvailability.fromAny(null))
    }

    @Test
    fun slotTime_convertsHalfHours() {
        assertEquals(LocalTime.of(0, 0), WeekAvailability.slotTime(0))
        assertEquals(LocalTime.of(19, 30), WeekAvailability.slotTime(39))
        assertEquals(38, WeekAvailability.slotOf(LocalTime.of(19, 0)))
        assertEquals(38, WeekAvailability.slotOf(LocalTime.of(19, 20)))
    }

    @Test
    fun weekdayEvenings_fillsMonToFri18to22() {
        val week = WeekAvailability.weekdayEvenings()
        assertTrue(week.isFree(1, 36))
        assertTrue(week.isFree(5, 43))
        assertFalse(week.isFree(5, 44))
        assertFalse(week.isFree(1, 35))
        assertFalse(week.isFree(6, 38))
        assertEquals(5 * 8, week.freeSlotCount())
    }

    @Test
    fun plus_keepsSlotsFreeInEither() {
        val a = WeekAvailability().withRange(1, 10, 12, true)
        val b = WeekAvailability().withRange(1, 11, 13, true).withRange(7, 0, 1, true)
        val sum = a + b
        assertEquals(listOf(10, 11, 12), (0 until 48).filter { sum.isFree(1, it) })
        assertTrue(sum.isFree(7, 0))
        assertEquals(4, sum.freeSlotCount())
    }
}
