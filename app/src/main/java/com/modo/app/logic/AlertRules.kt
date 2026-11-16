package com.modo.app.logic

import java.time.LocalDate
import java.time.LocalTime

/** 아침 마감 알림에 들어갈 업무 (기기에 스냅샷으로 저장해 두고, 앱이 꺼져 있어도 알림을 만든다) */
data class DigestTask(
    val taskId: String,
    val projectId: String,
    val title: String,
    val projectName: String,
    val dueDate: LocalDate,
    val dueTime: LocalTime? = null,
    val description: String = "",
)

data class Digest(val overdue: List<DigestTask>, val today: List<DigestTask>, val tomorrow: List<DigestTask>) {
    val isEmpty: Boolean get() = overdue.isEmpty() && today.isEmpty() && tomorrow.isEmpty()
    val count: Int get() = overdue.size + today.size + tomorrow.size
}

/** 채팅방의 마지막 메시지 요약 (새 메시지 알림을 띄울지 판단용) */
data class RoomTip(val roomId: String, val lastAt: Long, val senderUid: String, val unread: Int)

/** 알림을 띄울지 정하는 규칙들 */
object AlertRules {

    /** 마감 알림 내용: 지연 · 오늘 · 내일 마감으로 나눔 (마감 빠른 순) */
    fun digest(tasks: List<DigestTask>, today: LocalDate): Digest {
        val sorted = tasks.sortedWith(compareBy({ it.dueDate }, { it.dueTime ?: AlarmRules.DEFAULT_DUE_TIME }))
        return Digest(
            overdue = sorted.filter { it.dueDate.isBefore(today) },
            today = sorted.filter { it.dueDate == today },
            tomorrow = sorted.filter { it.dueDate == today.plusDays(1) },
        )
    }

    /**
     * 새 메시지 배너를 띄울 채팅방.
     * - 지난번에 본 시각보다 새 메시지가 있고
     * - 내가 보낸 것이 아니고, 안 읽은 메시지가 있으며
     * - 지금 열어 보고 있는 방이 아니고
     * - 방금(recentMillis 안에) 온 메시지 (앱을 켤 때 밀린 옛 메시지는 띄우지 않음)
     */
    fun roomsToAlert(
        seen: Map<String, Long>,
        rooms: List<RoomTip>,
        myUid: String,
        activeRoomId: String?,
        now: Long,
        recentMillis: Long = 120_000L,
    ): List<RoomTip> = rooms.filter { room ->
        val before = seen[room.roomId]
        before != null && room.lastAt > before &&
            room.senderUid != myUid && room.unread > 0 &&
            room.roomId != activeRoomId &&
            now - room.lastAt <= recentMillis
    }

    /** 새로 나에게 배정된 업무 id (처음 불러온 목록에는 알리지 않도록 before가 null이면 빈 목록) */
    fun newlyAssigned(before: Set<String>?, now: Set<String>): Set<String> = if (before == null) emptySet() else now - before
}
