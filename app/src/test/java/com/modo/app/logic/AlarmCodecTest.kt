package com.modo.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class AlarmCodecTest {

    @Test
    fun `알람 목록을 저장했다가 그대로 되돌림`() {
        val alarms = listOf(
            TaskAlarm("t1", "p1", "로그인 화면 UI 구현", "모프 7조", 2000L, 1000L, AlarmOption.HOUR_3, AlarmSoundKind.SONG, "content://media/1", "Lo-fi beats.mp3", true, 2),
            TaskAlarm("t2", "p2", "발표 \"리허설\"", "경영학원론", 9000L, 8000L, AlarmOption.CUSTOM, repeat = false),
        )
        assertEquals(alarms, AlarmCodec.alarmsFromJson(AlarmCodec.alarmsToJson(alarms)))
    }

    @Test
    fun `비었거나 깨진 값은 빈 목록`() {
        assertTrue(AlarmCodec.alarmsFromJson(null).isEmpty())
        assertTrue(AlarmCodec.alarmsFromJson("").isEmpty())
        assertTrue(AlarmCodec.alarmsFromJson("not json").isEmpty())
        assertTrue(AlarmCodec.alarmsFromJson("[{\"title\":\"id 없음\"}]").isEmpty())
        assertTrue(AlarmCodec.digestFromJson("{bad").isEmpty())
    }

    @Test
    fun `모르는 선택지는 직접 설정과 기본 알람음으로 읽음`() {
        val json = "[{\"taskId\":\"t1\",\"option\":\"WEEK\",\"soundKind\":\"??\",\"triggerAt\":5}]"
        val alarm = AlarmCodec.alarmsFromJson(json).single()
        assertEquals(AlarmOption.CUSTOM, alarm.option)
        assertEquals(AlarmSoundKind.SYSTEM, alarm.soundKind)
        assertEquals(5L, alarm.triggerAt)
        assertTrue(alarm.repeat)
    }

    @Test
    fun `마감 알림 스냅샷을 저장했다가 그대로 되돌림`() {
        val tasks = listOf(
            DigestTask("t1", "p1", "중간 보고서 제출", "캡스톤", LocalDate.of(2026, 11, 16), LocalTime.of(18, 30), "PDF로 제출"),
            DigestTask("t2", "p1", "발표 리허설", "캡스톤", LocalDate.of(2026, 11, 17)),
        )
        assertEquals(tasks, AlarmCodec.digestFromJson(AlarmCodec.digestToJson(tasks)))
    }
}
