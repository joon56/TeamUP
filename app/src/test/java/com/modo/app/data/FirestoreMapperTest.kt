package com.modo.app.data

import com.modo.app.logic.WeekAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class FirestoreMapperTest {

    @Test
    fun project_roundTrip() {
        val p = Project(
            id = "p1", name = "데이터분석 팀플", description = "설명", subject = "데이터분석",
            startDate = LocalDate.of(2026, 11, 2), endDate = LocalDate.of(2026, 12, 15),
            colorIndex = 3, meetingDay = 5, meetingTime = LocalTime.of(18, 30),
            meetingDurationMin = 90, meetingDecided = false, mode = ProjectMode.CHAT,
            inviteCode = "K7Q2MX", ownerUid = "u1", memberUids = listOf("u1", "u2"),
            createdAt = 10L, updatedAt = 20L,
        )
        assertEquals(p, FirestoreMapper.projectFromMap("p1", FirestoreMapper.projectToMap(p)))
    }

    @Test
    fun project_readsFirestoreLongsAsInts() {
        val map = mapOf<String, Any?>(
            "name" to "x", "startDate" to "2026-11-02", "endDate" to "2026-11-30",
            "colorIndex" to 2L, "meetingDay" to 1L, "meetingTime" to "09:00",
        )
        val p = FirestoreMapper.projectFromMap("id", map)!!
        assertEquals(2, p.colorIndex)
        assertEquals(1, p.meetingDay)
        assertEquals(LocalTime.of(9, 0), p.meetingTime)
    }

    @Test
    fun project_fromV01Document_getsV02Defaults() {
        val map = mapOf<String, Any?>("name" to "옛 프로젝트", "startDate" to "2026-11-02", "endDate" to "2026-11-30")
        val p = FirestoreMapper.projectFromMap("id", map)!!
        assertEquals(ProjectMode.TODAY, p.mode)
        assertEquals("", p.subject)
        assertEquals(60, p.meetingDurationMin)
        assertTrue(p.meetingDecided)
    }

    @Test
    fun project_invalidDocument_isNull() {
        assertNull(FirestoreMapper.projectFromMap("id", mapOf("name" to "no dates")))
    }

    @Test
    fun task_roundTrip_withNullAssignee() {
        val t = Task(
            id = "t1", projectId = "p1", title = "자료 조사", description = "",
            assigneeId = null, dueDate = LocalDate.of(2026, 11, 20),
            priority = Priority.HIGH, status = TaskStatus.IN_PROGRESS,
            createdBy = "u1", createdAt = 1L, updatedAt = 2L, completedAt = null,
        )
        assertEquals(t, FirestoreMapper.taskFromMap("t1", "p1", FirestoreMapper.taskToMap(t)))
    }

    @Test
    fun task_roundTrip_withAttachmentsAndSourceMessage() {
        val t = Task(
            id = "t1", projectId = "p1", title = "발표 자료", dueDate = LocalDate.of(2026, 11, 20),
            attachments = listOf(Attachment("draft.pdf", 1024L, "application/pdf", "projects/p1/x/draft.pdf", "u1")),
            sourceRoomId = "p_p1", sourceMessageId = "m9",
        )
        assertEquals(t, FirestoreMapper.taskFromMap("t1", "p1", FirestoreMapper.taskToMap(t)))
    }

    @Test
    fun member_roundTrip() {
        val m = Member(id = "m1", projectId = "p1", name = "민수", role = "디자인", uid = null, joinedAt = 5L)
        assertEquals(m, FirestoreMapper.memberFromMap("m1", "p1", FirestoreMapper.memberToMap(m)))
    }

    @Test
    fun member_roundTrip_withTagAndAvailability() {
        val week = WeekAvailability().withRange(2, 38, 42, true)
        val m = Member(
            id = "u2", projectId = "p1", name = "유진", role = "개발", uid = "u2", joinedAt = 5L,
            tag = "4821", avatarColor = 3, availability = week, availabilityAt = 9L,
        )
        assertEquals(m, FirestoreMapper.memberFromMap("u2", "p1", FirestoreMapper.memberToMap(m)))
    }

    @Test
    fun notice_roundTrip_withPoll() {
        val n = Notice(
            id = "n1", projectId = "p1", title = "중간발표", body = "목요일까지 자료 올려 주세요", authorUid = "u1",
            authorName = "유진", pinned = true,
            poll = Poll("회의 언제?", listOf(PollOption("a", "화"), PollOption("b", "수")), multiple = true),
            createdAt = 7L,
        )
        assertEquals(n, FirestoreMapper.noticeFromMap("n1", "p1", FirestoreMapper.noticeToMap(n)))
    }

    @Test
    fun minutes_roundTrip_withSummary() {
        val m = Minutes(
            id = "x", projectId = "p1", title = "3주차 회의", meetingDate = LocalDate.of(2026, 11, 10),
            body = "본문", transcript = "받아쓰기",
            summary = MinutesSummary(listOf("주제 확정"), listOf(ActionItem("설문 만들기", "민수", "2026-11-14", taskId = "t9")), overview = "설문 주제를 정함"),
            attendeeIds = listOf("u1", "u2"),
            attachments = listOf(Attachment("a.png", 10L, "image/png", "projects/p1/y/a.png", "u1")),
            createdBy = "u1", createdByName = "유진", createdAt = 1L, updatedAt = 2L,
        )
        assertEquals(m, FirestoreMapper.minutesFromMap("x", "p1", FirestoreMapper.minutesToMap(m)))
    }

    @Test
    fun invite_roundTrip() {
        val i = Invite("i1", "p1", "데이터분석", "u1", "유진", "u2", "민수", InviteStatus.PENDING, 3L)
        assertEquals(i, FirestoreMapper.inviteFromMap("i1", FirestoreMapper.inviteToMap(i)))
    }

    @Test
    fun profile_fromMap_readsTagAndAvailability() {
        val map = mapOf<String, Any?>(
            "name" to "유진", "email" to "yujin@modo.test", "tag" to "4821", "school" to "모프대",
            "avatarColor" to 2L, "availability" to listOf(0L, 3L, 0L, 0L, 0L, 0L, 0L), "notifReadAt" to 9L,
        )
        val p = FirestoreMapper.profileFromMap("u1", map)
        assertEquals("유진#4821", p.handle)
        assertEquals(2, p.avatarColor)
        assertTrue(p.availability!!.isFree(2, 1))
        assertEquals(9L, p.notifReadAt)
    }

    @Test
    fun chatRoom_fromMap_readsUnreadAndLastMessage() {
        val map = mapOf<String, Any?>(
            "type" to "DIRECT", "memberUids" to listOf("a", "b"), "names" to mapOf("a" to "유진", "b" to "민수"),
            "lastMessage" to mapOf("text" to "안녕", "type" to "TEXT", "senderUid" to "a", "senderName" to "유진", "at" to 5L),
            "updatedAt" to 5L, "unread" to mapOf("b" to 2L),
        )
        val room = FirestoreMapper.roomFromMap("d_a_b", map)!!
        assertEquals(RoomType.DIRECT, room.type)
        assertEquals(2, room.unreadFor("b"))
        assertEquals(0, room.unreadFor("a"))
        assertEquals("안녕", room.lastMessage!!.text)
    }

    @Test
    fun chatMessage_roundTrip_withFile() {
        val m = ChatMessage(
            id = "m1", roomId = "p_p1", senderUid = "u1", senderName = "유진", type = MessageType.FILE,
            text = "", file = Attachment("a.pdf", 5L, "application/pdf", "projects/p1/z/a.pdf", "u1"),
            refId = null, mentions = listOf("u2"), createdAt = 11L,
        )
        assertEquals(m, FirestoreMapper.messageFromMap("m1", "p_p1", FirestoreMapper.messageToMap(m)))
    }
}
