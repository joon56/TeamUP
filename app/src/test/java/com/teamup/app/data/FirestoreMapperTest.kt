package com.teamup.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class FirestoreMapperTest {

    @Test
    fun project_roundTrip() {
        val p = Project(
            id = "p1", name = "데이터분석 팀플", description = "설명",
            startDate = LocalDate.of(2026, 11, 2), endDate = LocalDate.of(2026, 12, 15),
            colorIndex = 3, meetingDay = 5, meetingTime = LocalTime.of(18, 30),
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
    fun member_roundTrip() {
        val m = Member(id = "m1", projectId = "p1", name = "민수", role = "디자인", uid = null, joinedAt = 5L)
        assertEquals(m, FirestoreMapper.memberFromMap("m1", "p1", FirestoreMapper.memberToMap(m)))
    }
}
