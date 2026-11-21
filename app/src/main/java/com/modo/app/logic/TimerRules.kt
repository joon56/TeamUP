package com.modo.app.logic

/**
 * 돌고 있는 회의 타이머 1개. 화면을 닫아도 계속 가도록 기기에 저장한다.
 * @param endAt 끝나는 시각 (밀리초)
 * @param totalMs 처음에 맞춘 길이 (밀리초)
 */
data class MeetingTimer(val endAt: Long, val totalMs: Long) {
    /** 처음에 맞춘 길이 (분) */
    val minutes: Int get() = (totalMs / 60_000L).toInt()
}

/** 회의 타이머 계산. 안드로이드와 무관한 순수 Kotlin이라 단위 테스트로 검증한다 */
object TimerRules {

    /** 버튼으로 바로 고를 수 있는 길이 (분) */
    val PRESET_MINUTES = listOf(10, 30, 60)

    fun start(minutes: Int, now: Long): MeetingTimer {
        val total = minutes * 60_000L
        return MeetingTimer(endAt = now + total, totalMs = total)
    }

    /** 남은 시간 (밀리초). 끝났으면 0 */
    fun remaining(timer: MeetingTimer, now: Long): Long = (timer.endAt - now).coerceAtLeast(0)

    /** 지난 비율 0~100 (진행 막대용) */
    fun percent(timer: MeetingTimer, now: Long): Int {
        if (timer.totalMs <= 0) return 100
        val passed = timer.totalMs - remaining(timer, now)
        return (passed * 100 / timer.totalMs).toInt().coerceIn(0, 100)
    }

    /** 남은 시간 표시: "29:59", 한 시간 이상이면 "1:30:05". 초는 올림 (조금이라도 남았으면 0:01) */
    fun clock(remainingMs: Long): String {
        val seconds = (remainingMs.coerceAtLeast(0) + 999) / 1000
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** 시간 선택창(시 · 분)에서 고른 값을 분으로 */
    fun minutesOf(hour: Int, minute: Int): Int = hour * 60 + minute
}
