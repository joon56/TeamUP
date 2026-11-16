package com.modo.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class AlarmRulesTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val due = AlarmRules.dueAt(LocalDate.of(2026, 11, 20), LocalTime.of(18, 0), zone)
    private val minute = 60_000L

    private fun alarm(option: AlarmOption, trigger: Long, repeat: Boolean = true, count: Int = 0) =
        TaskAlarm("t1", "p1", "발표 자료", "모프", due, trigger, option, repeat = repeat, repeatCount = count)

    @Test
    fun `마감 시간이 없으면 그날 23시 59분`() {
        val withTime = AlarmRules.dueAt(LocalDate.of(2026, 11, 20), LocalTime.of(23, 59), zone)
        assertEquals(withTime, AlarmRules.dueAt(LocalDate.of(2026, 11, 20), null, zone))
    }

    @Test
    fun `선택지마다 마감에서 그만큼 앞선 시각`() {
        assertEquals(due - 30 * minute, AlarmRules.triggerAt(due, AlarmOption.MIN_30))
        assertEquals(due - 60 * minute, AlarmRules.triggerAt(due, AlarmOption.HOUR_1))
        assertEquals(due - 180 * minute, AlarmRules.triggerAt(due, AlarmOption.HOUR_3))
        assertEquals(12345L, AlarmRules.triggerAt(due, AlarmOption.CUSTOM, 12345L))
    }

    @Test
    fun `이미 지난 선택지는 고를 수 없고 직접 설정은 항상 가능`() {
        val now = due - 45 * minute // 마감 45분 전
        assertEquals(listOf(AlarmOption.MIN_30, AlarmOption.CUSTOM), AlarmRules.availableOptions(due, now))
        assertEquals(listOf(AlarmOption.CUSTOM), AlarmRules.availableOptions(due, due + minute))
        assertEquals(AlarmOption.entries.toList(), AlarmRules.availableOptions(due, due - 500 * minute))
    }

    @Test
    fun `마감이 바뀌면 N분 전 알람도 따라가고 지난 시각이면 해제`() {
        val now = due - 300 * minute
        val moved = AlarmRules.rebase(alarm(AlarmOption.HOUR_1, due - 60 * minute, count = 2), due + 120 * minute, now)!!
        assertEquals(due + 60 * minute, moved.triggerAt)
        assertEquals(due + 120 * minute, moved.dueAt)
        assertEquals(0, moved.repeatCount)
        // 마감을 앞당겨서 알람 시각이 이미 지났으면 해제
        assertNull(AlarmRules.rebase(alarm(AlarmOption.HOUR_3, due - 180 * minute), now + 10 * minute, now))
    }

    @Test
    fun `직접 설정한 알람은 마감이 바뀌어도 그대로`() {
        val now = due - 300 * minute
        val custom = alarm(AlarmOption.CUSTOM, now + 5 * minute)
        assertEquals(now + 5 * minute, AlarmRules.rebase(custom, due + 60 * minute, now)!!.triggerAt)
        assertNull(AlarmRules.rebase(alarm(AlarmOption.CUSTOM, now - minute), due, now))
    }

    @Test
    fun `완료 전까지 10분 간격으로 최대 3번 다시 울림`() {
        val now = due
        val first = AlarmRules.nextRepeat(alarm(AlarmOption.MIN_30, due - 30 * minute), now)!!
        assertEquals(now + 10 * minute, first.triggerAt)
        assertEquals(1, first.repeatCount)
        assertNull(AlarmRules.nextRepeat(alarm(AlarmOption.MIN_30, due, count = 3), now))
        assertNull(AlarmRules.nextRepeat(alarm(AlarmOption.MIN_30, due, repeat = false), now))
    }

    @Test
    fun `10분 뒤 다시는 횟수를 늘리지 않음`() {
        val snoozed = AlarmRules.snooze(alarm(AlarmOption.MIN_30, due, count = 1), due)
        assertEquals(due + 10 * minute, snoozed.triggerAt)
        assertEquals(1, snoozed.repeatCount)
    }

    @Test
    fun `매일 알림은 오늘 시각이 지났으면 내일`() {
        val eight = LocalTime.of(8, 0)
        assertEquals(LocalDateTime.of(2026, 11, 16, 8, 0), AlarmRules.nextDaily(LocalDateTime.of(2026, 11, 16, 7, 59), eight))
        assertEquals(LocalDateTime.of(2026, 11, 17, 8, 0), AlarmRules.nextDaily(LocalDateTime.of(2026, 11, 16, 8, 0), eight))
    }

    @Test
    fun `재생 시간 표시`() {
        assertEquals("0:12", AlarmRules.clock(12))
        assertEquals("1:05", AlarmRules.clock(65))
    }

    // ───────── AlertRules ─────────

    private val today = LocalDate.of(2026, 11, 16)
    private fun task(id: String, date: LocalDate, time: LocalTime? = null) = DigestTask(id, "p1", id, "모프", date, time)

    @Test
    fun `마감 알림은 지연 오늘 내일로 나뉘고 그 뒤 마감은 빠짐`() {
        val digest = AlertRules.digest(
            listOf(
                task("late", today.minusDays(2)), task("b", today, LocalTime.of(18, 0)), task("a", today, LocalTime.of(9, 0)),
                task("tomorrow", today.plusDays(1)), task("far", today.plusDays(5)),
            ),
            today,
        )
        assertEquals(listOf("late"), digest.overdue.map { it.taskId })
        assertEquals(listOf("a", "b"), digest.today.map { it.taskId })
        assertEquals(listOf("tomorrow"), digest.tomorrow.map { it.taskId })
        assertEquals(4, digest.count)
        assertTrue(AlertRules.digest(listOf(task("far", today.plusDays(5))), today).isEmpty)
    }

    @Test
    fun `새 메시지 배너 조건`() {
        val now = 1_000_000L
        val seen = mapOf("r1" to now - 50_000, "r2" to now - 50_000, "r3" to now - 50_000, "r4" to now - 50_000, "old" to now - 900_000)
        val rooms = listOf(
            RoomTip("r1", now - 1_000, "other", 2),        // 새 메시지 → 알림
            RoomTip("r2", now - 1_000, "me", 0),           // 내가 보냄
            RoomTip("r3", now - 1_000, "other", 3),        // 지금 보고 있는 방
            RoomTip("r4", now - 50_000, "other", 1),       // 새 메시지 없음
            RoomTip("old", now - 600_000, "other", 5),     // 밀려 있던 옛 메시지
            RoomTip("first", now - 1_000, "other", 1),     // 처음 보는 방(기준 없음)
        )
        assertEquals(listOf("r1"), AlertRules.roomsToAlert(seen, rooms, "me", "r3", now).map { it.roomId })
    }

    @Test
    fun `처음 불러온 업무는 배정 알림을 띄우지 않음`() {
        assertTrue(AlertRules.newlyAssigned(null, setOf("a", "b")).isEmpty())
        assertEquals(setOf("c"), AlertRules.newlyAssigned(setOf("a", "b"), setOf("a", "b", "c")))
        assertFalse(AlertRules.newlyAssigned(setOf("a"), setOf("a")).isNotEmpty())
    }
}
