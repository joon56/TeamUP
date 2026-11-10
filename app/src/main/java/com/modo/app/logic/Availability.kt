package com.modo.app.logic

import java.time.LocalTime

/**
 * 일주일 가능 시간 (when2meet 방식).
 * days[요일-1]의 비트 s가 1이면 그 요일 s번째 30분 칸(00:00부터)이 가능. 요일은 1=월 … 7=일.
 * Firestore에는 숫자 7개 배열로 저장한다.
 */
data class WeekAvailability(val days: List<Long> = EMPTY_DAYS) {

    init {
        require(days.size == 7) { "days must have 7 entries" }
    }

    fun isFree(day: Int, slot: Int): Boolean = (days[day - 1] shr slot) and 1L == 1L

    fun with(day: Int, slot: Int, free: Boolean): WeekAvailability = withRange(day, slot, slot + 1, free)

    /** [from, toExclusive) 칸을 한 번에 칠하거나 지움 */
    fun withRange(day: Int, from: Int, toExclusive: Int, free: Boolean): WeekAvailability {
        var bits = days[day - 1]
        for (slot in from.coerceAtLeast(0) until toExclusive.coerceAtMost(SLOTS)) {
            bits = if (free) bits or (1L shl slot) else bits and (1L shl slot).inv()
        }
        return WeekAvailability(days.toMutableList().also { it[day - 1] = bits })
    }

    val isEmpty: Boolean get() = days.all { it == 0L }

    /** 두 가능 시간을 합침 (어느 쪽이든 가능한 칸) */
    operator fun plus(other: WeekAvailability): WeekAvailability = WeekAvailability(days.zip(other.days) { a, b -> a or b })

    fun freeSlotCount(): Int = days.sumOf { java.lang.Long.bitCount(it) }

    companion object {
        /** 하루 30분 칸 수 */
        const val SLOTS = 48
        val EMPTY_DAYS: List<Long> = List(7) { 0L }

        /** Firestore 값(숫자 7개 배열) → 가능 시간. 모양이 다르면 null */
        fun fromAny(value: Any?): WeekAvailability? {
            val list = value as? List<*> ?: return null
            if (list.size != 7) return null
            val days = list.map { (it as? Number)?.toLong() ?: return null }
            return WeekAvailability(days)
        }

        /** 평일(월~금) 저녁 18:00~22:00 — 가능 시간 빠른 입력 */
        fun weekdayEvenings(): WeekAvailability =
            (1..5).fold(WeekAvailability()) { week, day -> week.withRange(day, slotOf(LocalTime.of(18, 0)), slotOf(LocalTime.of(22, 0)), true) }

        fun slotTime(slot: Int): LocalTime = LocalTime.of((slot / 2) % 24, (slot % 2) * 30)

        /** 시각이 속한 칸 (19:20 → 19:00 칸) */
        fun slotOf(time: LocalTime): Int = time.hour * 2 + time.minute / 30
    }
}
