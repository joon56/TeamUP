package com.modo.app.data

import com.modo.app.logic.WeekAvailability
import java.time.LocalDate
import java.time.LocalTime

/**
 * Firestore 문서(Map) ↔ 모델 변환.
 * Firestore는 정수를 Long으로 돌려주므로 Number로 받아서 변환한다.
 * 형식이 잘못된 문서는 null을 돌려줘서 화면에서 건너뛴다.
 * v0.1 문서에 없는 필드는 기본값을 쓴다.
 */
object FirestoreMapper {

    // ───────────────────────── 읽기 도우미 ─────────────────────────

    private fun Map<String, Any?>.str(key: String, default: String = ""): String = this[key] as? String ?: default
    private fun Map<String, Any?>.strOrNull(key: String): String? = this[key] as? String
    private fun Map<String, Any?>.int(key: String, default: Int): Int = (this[key] as? Number)?.toInt() ?: default
    private fun Map<String, Any?>.long(key: String, default: Long = 0L): Long = (this[key] as? Number)?.toLong() ?: default
    private fun Map<String, Any?>.bool(key: String, default: Boolean): Boolean = this[key] as? Boolean ?: default
    private fun Map<String, Any?>.strings(key: String): List<String> =
        (this[key] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asMap(): Map<String, Any?>? = this as? Map<String, Any?>

    private inline fun <reified E : Enum<E>> enumOf(value: Any?, default: E): E =
        enumValues<E>().firstOrNull { it.name == value } ?: default

    private fun availabilityToList(week: WeekAvailability?): List<Long>? = week?.days

    // ───────────────────────── 프로젝트 ─────────────────────────

    fun projectToMap(p: Project): Map<String, Any?> = mapOf(
        "name" to p.name,
        "description" to p.description,
        "subject" to p.subject,
        "startDate" to p.startDate.toString(),
        "endDate" to p.endDate.toString(),
        "colorIndex" to p.colorIndex,
        "meetingDay" to p.meetingDay,
        "meetingTime" to p.meetingTime.toString(),
        "meetingDurationMin" to p.meetingDurationMin,
        "meetingDecided" to p.meetingDecided,
        "mode" to p.mode.name,
        "inviteCode" to p.inviteCode,
        "ownerUid" to p.ownerUid,
        "memberUids" to p.memberUids,
        "createdAt" to p.createdAt,
        "updatedAt" to p.updatedAt,
    )

    fun projectFromMap(id: String, m: Map<String, Any?>): Project? = try {
        Project(
            id = id,
            name = m.str("name"),
            description = m.str("description"),
            subject = m.str("subject"),
            startDate = LocalDate.parse(m["startDate"] as String),
            endDate = LocalDate.parse(m["endDate"] as String),
            colorIndex = m.int("colorIndex", 0),
            meetingDay = m.int("meetingDay", 3),
            meetingTime = m.strOrNull("meetingTime")?.let { LocalTime.parse(it) } ?: LocalTime.of(19, 0),
            meetingDurationMin = m.int("meetingDurationMin", 60),
            meetingDecided = m.bool("meetingDecided", true),
            mode = enumOf(m["mode"], ProjectMode.TODAY),
            inviteCode = m.str("inviteCode"),
            ownerUid = m.str("ownerUid"),
            memberUids = m.strings("memberUids"),
            createdAt = m.long("createdAt"),
            updatedAt = m.long("updatedAt"),
        )
    } catch (e: Exception) {
        null
    }

    // ───────────────────────── 팀원 ─────────────────────────

    fun memberToMap(m: Member): Map<String, Any?> = mapOf(
        "name" to m.name,
        "role" to m.role,
        "uid" to m.uid,
        "joinedAt" to m.joinedAt,
        "tag" to m.tag,
        "avatarColor" to m.avatarColor,
        "availability" to availabilityToList(m.availability),
        "availabilityAt" to m.availabilityAt,
    )

    fun memberFromMap(id: String, projectId: String, m: Map<String, Any?>): Member? {
        val name = m["name"] as? String ?: return null
        return Member(
            id = id,
            projectId = projectId,
            name = name,
            role = m.str("role"),
            uid = m.strOrNull("uid"),
            joinedAt = m.long("joinedAt"),
            tag = m.str("tag"),
            avatarColor = m.int("avatarColor", -1),
            availability = WeekAvailability.fromAny(m["availability"]),
            availabilityAt = m.long("availabilityAt"),
        )
    }

    // ───────────────────────── 첨부 ─────────────────────────

    fun attachmentToMap(a: Attachment): Map<String, Any?> = mapOf(
        "name" to a.name, "size" to a.size, "mime" to a.mime, "path" to a.path, "uploadedBy" to a.uploadedBy,
    )

    fun attachmentFromMap(value: Any?): Attachment? {
        val m = value.asMap() ?: return null
        val path = m["path"] as? String ?: return null
        return Attachment(m.str("name"), m.long("size"), m.str("mime", "application/octet-stream"), path, m.str("uploadedBy"))
    }

    private fun attachmentsFrom(value: Any?): List<Attachment> =
        (value as? List<*>)?.mapNotNull { attachmentFromMap(it) } ?: emptyList()

    // ───────────────────────── 업무 ─────────────────────────

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
        "attachments" to t.attachments.map { attachmentToMap(it) },
        "sourceRoomId" to t.sourceRoomId,
        "sourceMessageId" to t.sourceMessageId,
    )

    fun taskFromMap(id: String, projectId: String, m: Map<String, Any?>): Task? = try {
        Task(
            id = id,
            projectId = projectId,
            title = m.str("title"),
            description = m.str("description"),
            assigneeId = m.strOrNull("assigneeId"),
            dueDate = LocalDate.parse(m["dueDate"] as String),
            priority = enumOf(m["priority"], Priority.MEDIUM),
            status = enumOf(m["status"], TaskStatus.TODO),
            createdBy = m.str("createdBy"),
            createdAt = m.long("createdAt"),
            updatedAt = m.long("updatedAt"),
            completedAt = (m["completedAt"] as? Number)?.toLong(),
            attachments = attachmentsFrom(m["attachments"]),
            sourceRoomId = m.strOrNull("sourceRoomId"),
            sourceMessageId = m.strOrNull("sourceMessageId"),
        )
    } catch (e: Exception) {
        null
    }

    // ───────────────────────── 공지·투표 ─────────────────────────

    fun noticeToMap(n: Notice): Map<String, Any?> = mapOf(
        "projectId" to n.projectId,
        "title" to n.title,
        "body" to n.body,
        "authorUid" to n.authorUid,
        "authorName" to n.authorName,
        "pinned" to n.pinned,
        "poll" to n.poll?.let { poll ->
            mapOf(
                "question" to poll.question,
                "options" to poll.options.map { mapOf("id" to it.id, "text" to it.text) },
                "multiple" to poll.multiple,
            )
        },
        "createdAt" to n.createdAt,
    )

    fun noticeFromMap(id: String, projectId: String, m: Map<String, Any?>): Notice? {
        val authorUid = m["authorUid"] as? String ?: return null
        val poll = m["poll"].asMap()?.let { p ->
            val options = (p["options"] as? List<*>)?.mapNotNull { o ->
                val om = o.asMap() ?: return@mapNotNull null
                PollOption(om.str("id"), om.str("text"))
            } ?: emptyList()
            Poll(p.str("question"), options, p.bool("multiple", false))
        }
        return Notice(
            id = id, projectId = projectId, title = m.str("title"), body = m.str("body"),
            authorUid = authorUid, authorName = m.str("authorName"), pinned = m.bool("pinned", false),
            poll = poll, createdAt = m.long("createdAt"),
        )
    }

    fun voteFromMap(uid: String, m: Map<String, Any?>): Vote = Vote(uid, m.strings("optionIds"), m.long("at"))

    // ───────────────────────── 회의록 ─────────────────────────

    fun minutesToMap(m: Minutes): Map<String, Any?> = mapOf(
        "title" to m.title,
        "meetingDate" to m.meetingDate.toString(),
        "body" to m.body,
        "transcript" to m.transcript,
        "summary" to m.summary?.let { s ->
            mapOf(
                "decisions" to s.decisions,
                "actions" to s.actions.map { mapOf("title" to it.title, "assignee" to it.assignee, "due" to it.due, "taskId" to it.taskId) },
                "overview" to s.overview,
            )
        },
        "attendeeIds" to m.attendeeIds,
        "attachments" to m.attachments.map { attachmentToMap(it) },
        "createdBy" to m.createdBy,
        "createdByName" to m.createdByName,
        "createdAt" to m.createdAt,
        "updatedAt" to m.updatedAt,
    )

    fun minutesFromMap(id: String, projectId: String, m: Map<String, Any?>): Minutes? = try {
        Minutes(
            id = id,
            projectId = projectId,
            title = m.str("title"),
            meetingDate = LocalDate.parse(m["meetingDate"] as String),
            body = m.str("body"),
            transcript = m.str("transcript"),
            summary = m["summary"].asMap()?.let { s ->
                MinutesSummary(
                    decisions = s.strings("decisions"),
                    actions = (s["actions"] as? List<*>)?.mapNotNull { a ->
                        val am = a.asMap() ?: return@mapNotNull null
                        ActionItem(am.str("title"), am.str("assignee"), am.str("due"), am.str("taskId"))
                    } ?: emptyList(),
                    overview = s.str("overview"),
                )
            },
            attendeeIds = m.strings("attendeeIds"),
            attachments = attachmentsFrom(m["attachments"]),
            createdBy = m.str("createdBy"),
            createdByName = m.str("createdByName"),
            createdAt = m.long("createdAt"),
            updatedAt = m.long("updatedAt"),
        )
    } catch (e: Exception) {
        null
    }

    // ───────────────────────── 초대 ─────────────────────────

    fun inviteToMap(i: Invite): Map<String, Any?> = mapOf(
        "projectId" to i.projectId,
        "projectName" to i.projectName,
        "fromUid" to i.fromUid,
        "fromName" to i.fromName,
        "toUid" to i.toUid,
        "toName" to i.toName,
        "status" to i.status.name,
        "createdAt" to i.createdAt,
    )

    fun inviteFromMap(id: String, m: Map<String, Any?>): Invite? {
        val projectId = m["projectId"] as? String ?: return null
        val toUid = m["toUid"] as? String ?: return null
        return Invite(
            id = id, projectId = projectId, projectName = m.str("projectName"),
            fromUid = m.str("fromUid"), fromName = m.str("fromName"), toUid = toUid, toName = m.str("toName"),
            status = enumOf(m["status"], InviteStatus.PENDING), createdAt = m.long("createdAt"),
        )
    }

    // ───────────────────────── 파일 목록 ─────────────────────────

    fun fileToMap(f: FileMeta): Map<String, Any?> = mapOf(
        "name" to f.attachment.name,
        "size" to f.attachment.size,
        "mime" to f.attachment.mime,
        "path" to f.attachment.path,
        "uploadedBy" to f.attachment.uploadedBy,
        "uploadedByName" to f.uploadedByName,
        "source" to f.source,
        "sourceId" to f.sourceId,
        "createdAt" to f.createdAt,
    )

    fun fileFromMap(id: String, projectId: String, m: Map<String, Any?>): FileMeta? {
        val attachment = attachmentFromMap(m) ?: return null
        return FileMeta(id, projectId, attachment, m.str("uploadedByName"), m.str("source", "direct"), m.str("sourceId"), m.long("createdAt"))
    }

    // ───────────────────────── 사용자 ─────────────────────────

    fun profileFromMap(uid: String, m: Map<String, Any?>): UserProfile = UserProfile(
        uid = uid,
        name = m.str("name"),
        email = m.str("email"),
        tag = m.str("tag"),
        school = m.str("school"),
        avatarColor = m.int("avatarColor", -1),
        availability = WeekAvailability.fromAny(m["availability"]),
        notifReadAt = m.long("notifReadAt"),
        createdAt = m.long("createdAt"),
    )

    fun publicProfileToMap(p: PublicProfile): Map<String, Any?> = mapOf(
        "uid" to p.uid, "name" to p.name, "tag" to p.tag, "avatarColor" to p.avatarColor, "school" to p.school,
    )

    fun publicProfileFromMap(m: Map<String, Any?>): PublicProfile? {
        val uid = m["uid"] as? String ?: return null
        return PublicProfile(uid, m.str("name"), m.str("tag"), m.int("avatarColor", -1), m.str("school"))
    }

    // ───────────────────────── 채팅 ─────────────────────────

    fun roomFromMap(id: String, m: Map<String, Any?>): ChatRoom? {
        val type = enumValues<RoomType>().firstOrNull { it.name == m["type"] } ?: return null
        val last = m["lastMessage"].asMap()?.let { l ->
            LastMessage(l.str("text"), enumOf(l["type"], MessageType.TEXT), l.str("senderUid"), l.str("senderName"), l.long("at"))
        }
        return ChatRoom(
            id = id,
            type = type,
            projectId = m.strOrNull("projectId"),
            memberUids = m.strings("memberUids"),
            names = m["names"].asMap()?.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }?.toMap() ?: emptyMap(),
            lastMessage = last,
            updatedAt = m.long("updatedAt"),
            unread = m["unread"].asMap()?.mapNotNull { (k, v) -> (v as? Number)?.let { k to it.toLong() } }?.toMap() ?: emptyMap(),
        )
    }

    fun lastMessageToMap(l: LastMessage): Map<String, Any?> = mapOf(
        "text" to l.text, "type" to l.type.name, "senderUid" to l.senderUid, "senderName" to l.senderName, "at" to l.at,
    )

    fun messageToMap(m: ChatMessage): Map<String, Any?> = mapOf(
        "senderUid" to m.senderUid,
        "senderName" to m.senderName,
        "type" to m.type.name,
        "text" to m.text,
        "file" to m.file?.let { attachmentToMap(it) },
        "refId" to m.refId,
        "mentions" to m.mentions,
        "createdAt" to m.createdAt,
    )

    fun messageFromMap(id: String, roomId: String, m: Map<String, Any?>): ChatMessage? {
        val sender = m["senderUid"] as? String ?: return null
        return ChatMessage(
            id = id, roomId = roomId, senderUid = sender, senderName = m.str("senderName"),
            type = enumOf(m["type"], MessageType.TEXT), text = m.str("text"),
            file = attachmentFromMap(m["file"]), refId = m.strOrNull("refId"),
            mentions = m.strings("mentions"), createdAt = m.long("createdAt"),
        )
    }
}
