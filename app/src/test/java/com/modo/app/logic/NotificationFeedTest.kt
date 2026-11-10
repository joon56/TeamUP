package com.modo.app.logic

import com.modo.app.data.AppData
import com.modo.app.data.ChatMessage
import com.modo.app.data.Invite
import com.modo.app.data.Member
import com.modo.app.data.InviteStatus
import com.modo.app.data.MessageType
import com.modo.app.data.Notice
import com.modo.app.data.Project
import com.modo.app.data.Task
import com.modo.app.data.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NotificationFeedTest {

    private val today = LocalDate.of(2026, 11, 11)
    private val dayStart = 1_000_000L
    private val now = dayStart + 3_600_000L
    private val project = Project(id = "p", name = "P", startDate = today.minusDays(10), endDate = today.plusDays(30))

    private fun task(id: String, due: LocalDate, assignee: String?, status: TaskStatus = TaskStatus.TODO) =
        Task(id = id, projectId = "p", title = id, dueDate = due, assigneeId = assignee, status = status)

    private fun data(
        tasks: List<Task> = emptyList(),
        notices: List<Notice> = emptyList(),
        invites: List<Invite> = emptyList(),
        mentions: List<ChatMessage> = emptyList(),
    ) = AppData(
        myUid = "me", projects = listOf(project), tasks = mapOf("p" to tasks), notices = mapOf("p" to notices),
        receivedInvites = invites, mentions = mentions, loading = false,
    )

    @Test
    fun deadlines_onlyMyTasksThatNeedAttention() {
        val items = NotificationFeed.build(
            data(
                tasks = listOf(
                    task("late", today.minusDays(1), "me"),
                    task("soon", today.plusDays(2), "me"),
                    task("far", today.plusDays(5), "me"),
                    task("others", today, "you"),
                    task("done", today, "me", TaskStatus.DONE),
                )
            ),
            today, dayStart, now,
        )
        val ids = items.filterIsInstance<NotificationItem.DeadlineItem>().map { it.task.id }
        assertEquals(setOf("late", "soon"), ids.toSet())
        assertTrue(items.filterIsInstance<NotificationItem.DeadlineItem>().first { it.task.id == "late" }.overdue)
    }

    @Test
    fun notices_skipMineAndOldOnes() {
        val fresh = Notice("n1", "p", "공지", "", authorUid = "you", authorName = "you", createdAt = now - 1000)
        val mine = Notice("n2", "p", "내 공지", "", authorUid = "me", authorName = "me", createdAt = now - 1000)
        val old = Notice("n3", "p", "옛 공지", "", authorUid = "you", authorName = "you", createdAt = now - 20L * 86_400_000L)
        val items = NotificationFeed.build(data(notices = listOf(fresh, mine, old)), today, dayStart, now)
        assertEquals(listOf("n1"), items.filterIsInstance<NotificationItem.NoticeItem>().map { it.notice.id })
    }

    @Test
    fun pendingInvitesComeFirst_andUnseenCountUsesReadTime() {
        val invite = Invite("i", "p2", "Q", "you", "you", "me", "me", InviteStatus.PENDING, createdAt = dayStart - 10)
        val accepted = invite.copy(id = "j", status = InviteStatus.ACCEPTED)
        val mention = ChatMessage("m", "p_p", "you", "you", MessageType.TEXT, "@me", mentions = listOf("me"), createdAt = now - 5)
        val items = NotificationFeed.build(
            data(tasks = listOf(task("soon", today, "me")), invites = listOf(invite, accepted), mentions = listOf(mention)),
            today, dayStart, now,
        )
        assertTrue(items.first() is NotificationItem.InviteItem)
        assertEquals(1, items.count { it is NotificationItem.InviteItem })
        // 오늘 이미 알림을 봤다면 마감 항목(오늘 0시 기준)과 그 전 초대는 본 것으로, 이후 멘션만 새것
        assertEquals(1, NotificationFeed.unseenCount(items, readAt = dayStart + 1))
        assertEquals(3, NotificationFeed.unseenCount(items, readAt = 0))
    }

    // ── 회의 추천 (팀원 시간 모으는 중인 프로젝트) ──

    private val collecting = project.copy(meetingDecided = false, meetingDurationMin = 60)
    private val evening = WeekAvailability().withRange(3, 38, 42, true) // 수 19:00~21:00

    private fun meetingData(p: Project, members: List<Member>) = AppData(
        myUid = "me", projects = listOf(p), members = mapOf("p" to members), loading = false,
    )

    private fun member(id: String, week: WeekAvailability?, at: Long) =
        Member(id = id, projectId = "p", name = id, uid = id, availability = week, availabilityAt = at)

    @Test
    fun meeting_newTopRecommendation_isAnItem_atLatestInputTime() {
        val data = meetingData(collecting, listOf(member("me", evening, 10L), member("you", evening, 20L)))
        val items = NotificationFeed.build(data, today, dayStart, now) { null }
        val item = items.filterIsInstance<NotificationItem.MeetingItem>().single()
        assertEquals(MeetingSlot(3, 38, 2), item.recommendation.slot)
        assertEquals(20L, item.at)
    }

    @Test
    fun meeting_skippedWhenSeen_decided_orTooFewInputs() {
        val two = listOf(member("me", evening, 10L), member("you", evening, 20L))
        val seen = NotificationFeed.build(meetingData(collecting, two), today, dayStart, now) { "3-38-2" }
        assertTrue(seen.none { it is NotificationItem.MeetingItem })

        val decided = NotificationFeed.build(meetingData(collecting.copy(meetingDecided = true), two), today, dayStart, now) { null }
        assertTrue(decided.none { it is NotificationItem.MeetingItem })

        val one = listOf(member("me", evening, 10L), member("you", null, 0L))
        val alone = NotificationFeed.build(meetingData(collecting, one), today, dayStart, now) { null }
        assertTrue(alone.none { it is NotificationItem.MeetingItem })
    }
}
