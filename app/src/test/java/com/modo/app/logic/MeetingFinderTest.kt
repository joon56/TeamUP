package com.modo.app.logic

import com.modo.app.data.Member
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingFinderTest {

    private fun free(vararg ranges: Triple<Int, Int, Int>): WeekAvailability =
        ranges.fold(WeekAvailability()) { week, (day, from, to) -> week.withRange(day, from, to, true) }

    @Test
    fun recommend_picksSlotWhereMostPeopleAreFree() {
        val people = mapOf(
            "a" to free(Triple(2, 36, 42)), // 화 18:00~21:00
            "b" to free(Triple(2, 38, 42)), // 화 19:00~21:00
            "c" to free(Triple(2, 38, 40), Triple(4, 20, 24)), // 화 19:00~20:00, 목 10:00~12:00
        )
        val top = MeetingFinder.recommend(people, lengthSlots = 2)
        val best = top.first()
        assertEquals(MeetingSlot(day = 2, start = 38, length = 2), best.slot)
        assertEquals(listOf("a", "b", "c"), best.availableIds)
        assertTrue(best.unavailableIds.isEmpty())
        assertEquals(3, best.total)
    }

    @Test
    fun recommend_resultsDoNotOverlap_andSkipNobodyFree() {
        val people = mapOf(
            "a" to free(Triple(1, 36, 44)), // 월 18:00~22:00
            "b" to free(Triple(1, 36, 44)),
        )
        val top = MeetingFinder.recommend(people, lengthSlots = 2, top = 3)
        assertEquals(3, top.size)
        for (i in top.indices) for (j in top.indices) {
            if (i < j && top[i].slot.day == top[j].slot.day) {
                val overlap = top[i].slot.start < top[j].slot.end && top[j].slot.start < top[i].slot.end
                assertTrue("겹치면 안 됨: ${top[i].slot} / ${top[j].slot}", !overlap)
            }
        }
        assertTrue(top.all { it.availableIds.size == 2 })
    }

    @Test
    fun recommend_tieBreak_weekdayThenEveningThenEarlier() {
        val everyone = free(Triple(6, 20, 22), Triple(3, 20, 22), Triple(3, 38, 40)) // 토 10시, 수 10시, 수 19시
        val people = mapOf("a" to everyone, "b" to everyone)
        val top = MeetingFinder.recommend(people, lengthSlots = 2, top = 3)
        // 1순위는 평일 저녁, 같은 인원이면 다음 후보는 다른 요일(토)부터
        assertEquals(listOf(MeetingSlot(3, 38, 2), MeetingSlot(6, 20, 2), MeetingSlot(3, 20, 2)), top.map { it.slot })
    }

    @Test
    fun recommend_tiedAlternatives_preferOtherDays() {
        val week = free(Triple(1, 38, 44), Triple(3, 38, 44)) // 월·수 19:00~22:00
        val people = mapOf("a" to week, "b" to week)
        val top = MeetingFinder.recommend(people, lengthSlots = 2, top = 3)
        assertEquals(listOf(MeetingSlot(1, 38, 2), MeetingSlot(3, 38, 2), MeetingSlot(1, 40, 2)), top.map { it.slot })
    }

    @Test
    fun recommend_moreAvailableBeatsDayVariety() {
        val a = free(Triple(1, 38, 44), Triple(3, 38, 40))
        val b = free(Triple(1, 38, 44))
        val top = MeetingFinder.recommend(mapOf("a" to a, "b" to b), lengthSlots = 2, top = 2)
        assertEquals(listOf(MeetingSlot(1, 38, 2), MeetingSlot(1, 40, 2)), top.map { it.slot })
    }

    @Test
    fun recommend_reportsWhoCannotCome() {
        val people = mapOf(
            "a" to free(Triple(5, 30, 32)),
            "b" to free(Triple(5, 30, 32)),
            "c" to WeekAvailability(),
        )
        val best = MeetingFinder.recommend(people, lengthSlots = 2).first()
        assertEquals(listOf("a", "b"), best.availableIds)
        assertEquals(listOf("c"), best.unavailableIds)
    }

    @Test
    fun recommend_emptyWhenNobodyEntered() {
        assertTrue(MeetingFinder.recommend(emptyMap(), lengthSlots = 2).isEmpty())
    }

    @Test
    fun heatmap_countsPeoplePerSlot() {
        val people = mapOf("a" to free(Triple(1, 0, 2)), "b" to free(Triple(1, 1, 3)))
        val map = MeetingFinder.heatmap(people)
        assertEquals(1, map[0][0])
        assertEquals(2, map[0][1])
        assertEquals(1, map[0][2])
        assertEquals(0, map[1][1])
    }

    @Test
    fun people_skipsMembersWithoutAvailability() {
        val week = free(Triple(2, 38, 40))
        val members = listOf(
            Member(id = "a", projectId = "p", name = "A", availability = week),
            Member(id = "b", projectId = "p", name = "B", availability = WeekAvailability()),
            Member(id = "c", projectId = "p", name = "C"),
        )
        assertEquals(mapOf("a" to week), MeetingFinder.people(members))
    }

    @Test
    fun lengthSlots_roundsUpToHalfHours() {
        assertEquals(1, MeetingFinder.lengthSlots(30))
        assertEquals(2, MeetingFinder.lengthSlots(60))
        assertEquals(3, MeetingFinder.lengthSlots(75))
        assertEquals(1, MeetingFinder.lengthSlots(0))
    }

    @Test
    fun slotKey_identifiesDayStartAndLength() {
        assertEquals("3-38-2", MeetingSlot(3, 38, 2).key)
    }
}
