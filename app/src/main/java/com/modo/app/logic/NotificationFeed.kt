package com.modo.app.logic

import com.modo.app.data.AppData
import com.modo.app.data.ChatMessage
import com.modo.app.data.Invite
import com.modo.app.data.Notice
import com.modo.app.data.Project
import com.modo.app.data.Task
import java.time.LocalDate

/** 알림 화면 항목 (서버 푸시 없이, 받아 온 데이터에서 만들어 냄) */
sealed class NotificationItem {
    abstract val at: Long
    abstract val key: String

    data class InviteItem(val invite: Invite) : NotificationItem() {
        override val at: Long get() = invite.createdAt
        override val key: String get() = "invite:${invite.projectId}:${invite.id}"
    }

    /** 내 업무 마감 임박·지연. 시각은 "오늘 0시" → 하루에 한 번 새 알림으로 셈 */
    data class DeadlineItem(
        val task: Task,
        val project: Project,
        val overdue: Boolean,
        val dDay: Int,
        override val at: Long,
    ) : NotificationItem() {
        override val key: String get() = "deadline:${task.projectId}:${task.id}"
    }

    data class NoticeItem(val notice: Notice, val project: Project) : NotificationItem() {
        override val at: Long get() = notice.createdAt
        override val key: String get() = "notice:${notice.projectId}:${notice.id}"
    }

    data class MentionItem(val message: ChatMessage) : NotificationItem() {
        override val at: Long get() = message.createdAt
        override val key: String get() = "mention:${message.roomId}:${message.id}"
    }

    /** 회의 시간을 모으는 중인 프로젝트의 추천 1순위가 마지막으로 본 것과 다름. 시각은 가장 최근 가능 시간 입력 */
    data class MeetingItem(
        val project: Project,
        val recommendation: Recommendation,
        val people: Int,
        override val at: Long,
    ) : NotificationItem() {
        override val key: String get() = "meeting:${project.id}:${recommendation.slot.key}"
    }
}

object NotificationFeed {

    private const val DAY_MS = 86_400_000L
    const val NOTICE_DAYS = 14L

    /**
     * 받은 초대(대기 중)를 맨 위에, 나머지는 최신순.
     * meetingSeen: 프로젝트별로 회의 찾기 화면에서 마지막으로 본 추천 1순위 (MeetingSlot.key)
     */
    fun build(
        data: AppData,
        today: LocalDate,
        todayStartMillis: Long,
        nowMillis: Long,
        meetingSeen: (projectId: String) -> String? = { null },
    ): List<NotificationItem> {
        val items = mutableListOf<NotificationItem>()
        data.pendingInvites.forEach { items.add(NotificationItem.InviteItem(it)) }
        for (project in data.projects) {
            for (task in data.tasksOf(project.id)) {
                if (task.assigneeId != data.myUid || !TaskRules.needsAttention(task, today)) continue
                items.add(
                    NotificationItem.DeadlineItem(
                        task, project, TaskRules.isOverdue(task, today), TaskRules.dDay(task.dueDate, today), todayStartMillis,
                    )
                )
            }
            for (notice in data.noticesOf(project.id)) {
                if (notice.authorUid == data.myUid) continue
                if (nowMillis - notice.createdAt > NOTICE_DAYS * DAY_MS) continue
                items.add(NotificationItem.NoticeItem(notice, project))
            }
            meetingItem(data, project, meetingSeen(project.id))?.let { items.add(it) }
        }
        for (message in data.mentions) {
            if (message.senderUid != data.myUid) items.add(NotificationItem.MentionItem(message))
        }
        return items.sortedWith(
            compareBy<NotificationItem> { it !is NotificationItem.InviteItem }.thenByDescending { it.at }
        )
    }

    /** 두 명 이상 가능 시간을 입력했고, 지금 1순위가 마지막으로 본 1순위와 다를 때 */
    private fun meetingItem(data: AppData, project: Project, seenKey: String?): NotificationItem.MeetingItem? {
        if (project.meetingDecided) return null
        val members = data.membersOf(project.id)
        val people = MeetingFinder.people(members)
        if (people.size < 2) return null
        val top = MeetingFinder.recommend(people, MeetingFinder.lengthSlots(project.meetingDurationMin), top = 1).firstOrNull()
            ?: return null
        if (top.slot.key == seenKey) return null
        val at = members.filter { it.id in people }.maxOf { it.availabilityAt }
        return NotificationItem.MeetingItem(project, top, people.size, at)
    }

    /** 알림 화면을 마지막으로 본 뒤에 생긴 항목 수 (벨 배지) */
    fun unseenCount(items: List<NotificationItem>, readAt: Long): Int = items.count { it.at > readAt }
}
