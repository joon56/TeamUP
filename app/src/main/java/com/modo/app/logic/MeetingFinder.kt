package com.modo.app.logic

import com.modo.app.data.Member

/** 요일(1=월…7=일), 시작 칸, 길이(30분 칸 수) */
data class MeetingSlot(val day: Int, val start: Int, val length: Int) {
    val end: Int get() = start + length

    /** 추천이 바뀌었는지 비교할 때 쓰는 값 ("요일-시작칸-길이") */
    val key: String get() = "$day-$start-$length"

    fun overlaps(other: MeetingSlot): Boolean = day == other.day && start < other.end && other.start < end
}

data class Recommendation(
    val slot: MeetingSlot,
    /** 가능한 사람 id (입력 순서 유지) */
    val availableIds: List<String>,
    val unavailableIds: List<String>,
) {
    val total: Int get() = availableIds.size + unavailableIds.size
}

/**
 * 팀원 가능 시간을 모아 회의 시간을 추천한다.
 * 가능 시간을 아직 입력하지 않은 팀원은 호출하는 쪽에서 빼고 넘긴다.
 */
object MeetingFinder {

    /** 탐색 범위 기본값: 08:00 ~ 24:00 */
    const val FIRST_SLOT = 16
    const val LAST_SLOT = WeekAvailability.SLOTS
    private val EVENING = 36..44 // 18:00 ~ 22:00 시작

    /** 가능 시간을 입력한 팀원만 (id → 가능 시간) */
    fun people(members: List<Member>): Map<String, WeekAvailability> =
        members.mapNotNull { m -> m.availability?.takeUnless { it.isEmpty }?.let { m.id to it } }.toMap()

    /** 회의 길이(분) → 30분 칸 수 (올림, 최소 1칸) */
    fun lengthSlots(durationMin: Int): Int = ((durationMin + 29) / 30).coerceAtLeast(1)

    /**
     * 가능 인원이 많은 순으로 서로 겹치지 않는 [top]개.
     * 동점이면 평일 → 저녁 시작 → 이른 요일 → 이른 시간 순. 아무도 안 되는 시간은 제외.
     * 2순위부터는 인원이 같을 때 아직 안 고른 요일을 먼저 (같은 날 연달은 시간만 나오지 않게).
     */
    fun recommend(
        people: Map<String, WeekAvailability>,
        lengthSlots: Int,
        top: Int = 3,
        firstSlot: Int = FIRST_SLOT,
        lastSlot: Int = LAST_SLOT,
    ): List<Recommendation> {
        if (people.isEmpty() || lengthSlots <= 0) return emptyList()
        val candidates = mutableListOf<Recommendation>()
        for (day in 1..7) {
            for (start in firstSlot..(lastSlot - lengthSlots)) {
                val slot = MeetingSlot(day, start, lengthSlots)
                val available = mutableListOf<String>()
                val unavailable = mutableListOf<String>()
                for ((id, week) in people) {
                    val free = (start until start + lengthSlots).all { week.isFree(day, it) }
                    if (free) available.add(id) else unavailable.add(id)
                }
                if (available.isNotEmpty()) candidates.add(Recommendation(slot, available, unavailable))
            }
        }
        val sorted = candidates.sortedWith(
            compareByDescending<Recommendation> { it.availableIds.size }
                .thenBy { if (it.slot.day <= 5) 0 else 1 }
                .thenBy { if (it.slot.start in EVENING) 0 else 1 }
                .thenBy { it.slot.day }
                .thenBy { it.slot.start }
        )
        val picked = mutableListOf<Recommendation>()
        while (picked.size < top) {
            val open = sorted.filter { c -> picked.none { it.slot.overlaps(c.slot) } }
            val best = open.firstOrNull() ?: break
            val usedDays = picked.map { it.slot.day }.toSet()
            picked.add(open.firstOrNull { it.availableIds.size == best.availableIds.size && it.slot.day !in usedDays } ?: best)
        }
        return picked
    }

    /** [요일-1][칸] = 그 칸이 가능한 사람 수 */
    fun heatmap(people: Map<String, WeekAvailability>): Array<IntArray> {
        val map = Array(7) { IntArray(WeekAvailability.SLOTS) }
        for (week in people.values) {
            for (day in 1..7) for (slot in 0 until WeekAvailability.SLOTS) {
                if (week.isFree(day, slot)) map[day - 1][slot]++
            }
        }
        return map
    }
}
