package com.modo.app.logic

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** 업무 알람을 언제 울릴지 고르는 방식 */
enum class AlarmOption(val minutesBefore: Int) { MIN_30(30), HOUR_1(60), HOUR_3(180), CUSTOM(0) }

/** 알람 소리: 기기 기본 알람음 · 앱에 들어 있는 알람음(res/raw) · 내가 고른 노래 */
enum class AlarmSoundKind { SYSTEM, MODO, SONG }

/** 기기에 저장하는 업무 알람 1개. 알람은 이 기기에서만 울린다 */
data class TaskAlarm(
    val taskId: String,
    val projectId: String,
    val title: String,
    val projectName: String,
    /** 업무 마감 시각 (밀리초) */
    val dueAt: Long,
    /** 알람이 울릴 시각 (밀리초) */
    val triggerAt: Long,
    val option: AlarmOption,
    val soundKind: AlarmSoundKind = AlarmSoundKind.SYSTEM,
    val soundUri: String = "",
    val soundName: String = "",
    /** 완료 처리 전까지 다시 울릴지 */
    val repeat: Boolean = true,
    /** 이미 다시 울린 횟수 */
    val repeatCount: Int = 0,
)

/** 업무 알람 계산. 안드로이드와 무관한 순수 Kotlin이라 단위 테스트로 검증한다 */
object AlarmRules {

    /** 마감 시간을 안 정한 업무는 그날 끝까지 */
    val DEFAULT_DUE_TIME: LocalTime = LocalTime.of(23, 59)

    const val REPEAT_INTERVAL_MIN = 10
    const val MAX_REPEATS = 3
    const val SNOOZE_MIN = 10
    const val RING_SECONDS = 30

    fun dueAt(date: LocalDate, time: LocalTime?, zone: ZoneId): Long =
        LocalDateTime.of(date, time ?: DEFAULT_DUE_TIME).atZone(zone).toInstant().toEpochMilli()

    /** 선택지에 따른 알람 시각. CUSTOM이면 직접 고른 시각 */
    fun triggerAt(dueAt: Long, option: AlarmOption, customAt: Long = 0): Long =
        if (option == AlarmOption.CUSTOM) customAt else dueAt - option.minutesBefore * 60_000L

    /** 지금 고를 수 있는 선택지. 이미 지난 시각은 뺀다 (직접 설정은 항상 가능) */
    fun availableOptions(dueAt: Long, now: Long): List<AlarmOption> {
        val result = mutableListOf<AlarmOption>()
        for (option in AlarmOption.entries) {
            if (option == AlarmOption.CUSTOM || triggerAt(dueAt, option) > now) result.add(option)
        }
        return result
    }

    /**
     * 업무 마감이 바뀌면 "마감 N분 전" 알람도 따라 옮긴다. 직접 설정한 알람은 그대로 둔다.
     * 옮긴 시각이 이미 지났으면 null (알람 해제).
     */
    fun rebase(alarm: TaskAlarm, newDueAt: Long, now: Long): TaskAlarm? {
        if (alarm.option == AlarmOption.CUSTOM) return if (alarm.triggerAt > now) alarm.copy(dueAt = newDueAt) else null
        val trigger = triggerAt(newDueAt, alarm.option)
        return if (trigger > now) alarm.copy(dueAt = newDueAt, triggerAt = trigger, repeatCount = 0) else null
    }

    /** 울렸는데 완료 처리를 안 했을 때: 10분 뒤 다시 (최대 3회). 더 안 울리면 null */
    fun nextRepeat(alarm: TaskAlarm, now: Long): TaskAlarm? =
        if (alarm.repeat && alarm.repeatCount < MAX_REPEATS) {
            alarm.copy(triggerAt = now + REPEAT_INTERVAL_MIN * 60_000L, repeatCount = alarm.repeatCount + 1)
        } else {
            null
        }

    /** [10분 뒤 다시]를 눌렀을 때 */
    fun snooze(alarm: TaskAlarm, now: Long): TaskAlarm = alarm.copy(triggerAt = now + SNOOZE_MIN * 60_000L)

    /** 매일 한 번 오는 알림(아침 마감 목록)이 다음에 올 시각 */
    fun nextDaily(now: LocalDateTime, time: LocalTime): LocalDateTime {
        val today = LocalDateTime.of(now.toLocalDate(), time)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    /** 재생 시간 표시: 12초 → "0:12" */
    fun clock(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
}
