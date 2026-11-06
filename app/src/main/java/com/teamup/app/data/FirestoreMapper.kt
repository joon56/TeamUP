package com.teamup.app.data

import java.time.LocalDate
import java.time.LocalTime

/**
 * Firestore 문서(Map) ↔ 모델 변환.
 * Firestore는 정수를 Long으로 돌려주므로 Number로 받아서 변환한다.
 * 형식이 잘못된 문서는 null을 돌려줘서 화면에서 건너뛴다.
 */
object FirestoreMapper {

    fun projectToMap(p: Project): Map<String, Any?> = mapOf(
        "name" to p.name,
        "description" to p.description,
        "startDate" to p.startDate.toString(),
        "endDate" to p.endDate.toString(),
        "colorIndex" to p.colorIndex,
        "meetingDay" to p.meetingDay,
        "meetingTime" to p.meetingTime.toString(),
        "inviteCode" to p.inviteCode,
        "ownerUid" to p.ownerUid,
        "memberUids" to p.memberUids,
        "createdAt" to p.createdAt,
        "updatedAt" to p.updatedAt,
    )

    fun projectFromMap(id: String, m: Map<String, Any?>): Project? = try {
        Project(
            id = id,
            name = m["name"] as? String ?: "",
            description = m["description"] as? String ?: "",
            startDate = LocalDate.parse(m["startDate"] as String),
            endDate = LocalDate.parse(m["endDate"] as String),
            colorIndex = (m["colorIndex"] as? Number)?.toInt() ?: 0,
            meetingDay = (m["meetingDay"] as? Number)?.toInt() ?: 3,
            meetingTime = (m["meetingTime"] as? String)?.let { LocalTime.parse(it) } ?: LocalTime.of(19, 0),
            inviteCode = m["inviteCode"] as? String ?: "",
            ownerUid = m["ownerUid"] as? String ?: "",
            memberUids = (m["memberUids"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: 0L,
        )
    } catch (e: Exception) {
        null
    }

    fun memberToMap(m: Member): Map<String, Any?> = mapOf(
        "name" to m.name,
        "role" to m.role,
        "uid" to m.uid,
        "joinedAt" to m.joinedAt,
    )

    fun memberFromMap(id: String, projectId: String, m: Map<String, Any?>): Member? {
        val name = m["name"] as? String ?: return null
        return Member(
            id = id,
            projectId = projectId,
            name = name,
            role = m["role"] as? String ?: "",
            uid = m["uid"] as? String,
            joinedAt = (m["joinedAt"] as? Number)?.toLong() ?: 0L,
        )
    }

    fun taskToMap(t: Task): Map<String, Any?> = mapOf(
        "title" to t.title,
        "description" to t.description,
        "assigneeId" to t.assigneeId,
        "dueDate" to t.dueDate.toString(),
        "priority" to t.priority.name,
        "status" to t.status.name,
        "createdBy" to t.createdBy,
        "createdAt" to t.createdAt,
        "updatedAt" to t.updatedAt,
        "completedAt" to t.completedAt,
    )

    fun taskFromMap(id: String, projectId: String, m: Map<String, Any?>): Task? = try {
        Task(
            id = id,
            projectId = projectId,
            title = m["title"] as? String ?: "",
            description = m["description"] as? String ?: "",
            assigneeId = m["assigneeId"] as? String,
            dueDate = LocalDate.parse(m["dueDate"] as String),
            priority = Priority.entries.firstOrNull { it.name == m["priority"] } ?: Priority.MEDIUM,
            status = TaskStatus.entries.firstOrNull { it.name == m["status"] } ?: TaskStatus.TODO,
            createdBy = m["createdBy"] as? String ?: "",
            createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: 0L,
            completedAt = (m["completedAt"] as? Number)?.toLong(),
        )
    } catch (e: Exception) {
        null
    }
}
