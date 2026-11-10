package com.modo.app.data

import com.modo.app.logic.UserTag
import com.modo.app.logic.WeekAvailability
import java.time.LocalDate
import java.time.LocalTime

enum class TaskStatus { TODO, IN_PROGRESS, DONE }

enum class Priority { HIGH, MEDIUM, LOW }

/** 프로젝트 화면 모드 = 플로우 시안 1~4 */
enum class ProjectMode { TODAY, PROGRESS, WORKSPACE, CHAT }

data class Project(
    val id: String,
    val name: String,
    val description: String = "",
    /** 과목명 */
    val subject: String = "",
    val startDate: LocalDate,
    val endDate: LocalDate,
    val colorIndex: Int = 0,
    /** 정기 회의 요일. 1=월 ... 7=일 (java.time.DayOfWeek.value 와 같음) */
    val meetingDay: Int = 3,
    val meetingTime: LocalTime = LocalTime.of(19, 0),
    val meetingDurationMin: Int = 60,
    /** false = 팀원 가능 시간을 모으는 중 (회의 시간 미정) */
    val meetingDecided: Boolean = true,
    val mode: ProjectMode = ProjectMode.TODAY,
    val inviteCode: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

data class Member(
    /** 앱 사용자는 uid와 같고, 앱을 안 쓰는 팀원은 임의 id */
    val id: String,
    val projectId: String,
    val name: String,
    /** 맡은 일 (예: 발표, 자료조사) */
    val role: String = "",
    /** null 이면 앱을 쓰지 않는 팀원 */
    val uid: String? = null,
    val joinedAt: Long = 0,
    val tag: String = "",
    /** 아바타 색 번호. -1이면 이름으로 정함 */
    val avatarColor: Int = -1,
    /** 이 프로젝트에서의 가능 시간. 입력 안 했으면 null */
    val availability: WeekAvailability? = null,
    /** 가능 시간을 마지막으로 바꾼 시각 (회의 추천이 바뀌었다는 알림의 시각) */
    val availabilityAt: Long = 0,
) {
    val isAppUser: Boolean get() = uid != null
    val handle: String get() = UserTag.display(name, tag)
}

/** 업무·회의록·채팅에 붙은 파일 (실제 파일은 Supabase Storage의 path) */
data class Attachment(
    val name: String,
    val size: Long,
    val mime: String,
    val path: String,
    val uploadedBy: String = "",
) {
    val isImage: Boolean get() = mime.startsWith("image/")
}

data class Task(
    val id: String,
    val projectId: String,
    val title: String,
    val description: String = "",
    /** 담당자 Member.id. 담당자 없으면 null */
    val assigneeId: String? = null,
    val dueDate: LocalDate,
    val priority: Priority = Priority.MEDIUM,
    val status: TaskStatus = TaskStatus.TODO,
    val createdBy: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val completedAt: Long? = null,
    val attachments: List<Attachment> = emptyList(),
    /** 채팅 메시지에서 만든 업무면 원래 메시지 위치 */
    val sourceRoomId: String? = null,
    val sourceMessageId: String? = null,
)

data class PollOption(val id: String, val text: String)

data class Poll(val question: String, val options: List<PollOption>, val multiple: Boolean = false)

data class Notice(
    val id: String,
    val projectId: String,
    val title: String,
    val body: String,
    val authorUid: String,
    val authorName: String,
    val pinned: Boolean = false,
    val poll: Poll? = null,
    val createdAt: Long = 0,
)

data class Vote(val uid: String, val optionIds: List<String>, val at: Long = 0)

/** 회의록 할 일 후보. taskId: 보드에 업무로 등록했으면 그 업무 id (중복 등록 방지) */
data class ActionItem(val title: String, val assignee: String = "", val due: String = "", val taskId: String = "")

/** AI 요약: 결정 사항 · 할 일 후보 · 3~5문장 요약 */
data class MinutesSummary(val decisions: List<String>, val actions: List<ActionItem>, val overview: String = "")

data class Minutes(
    val id: String,
    val projectId: String,
    val title: String,
    val meetingDate: LocalDate,
    val body: String = "",
    /** 녹음 받아쓰기 */
    val transcript: String = "",
    val summary: MinutesSummary? = null,
    val attendeeIds: List<String> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val createdBy: String = "",
    val createdByName: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

enum class InviteStatus { PENDING, ACCEPTED, DECLINED, CANCELED }

data class Invite(
    val id: String,
    val projectId: String,
    val projectName: String,
    val fromUid: String,
    val fromName: String,
    val toUid: String,
    val toName: String,
    val status: InviteStatus,
    val createdAt: Long,
)

/** 프로젝트 파일 목록 항목 */
data class FileMeta(
    val id: String,
    val projectId: String,
    val attachment: Attachment,
    val uploadedByName: String,
    /** task | minutes | chat | direct */
    val source: String,
    val sourceId: String = "",
    val createdAt: Long = 0,
)

/** 다른 사람이 검색으로 볼 수 있는 공개 프로필 */
data class PublicProfile(
    val uid: String,
    val name: String,
    val tag: String,
    val avatarColor: Int = -1,
    val school: String = "",
) {
    val handle: String get() = UserTag.display(name, tag)
}

data class UserProfile(
    val uid: String,
    val name: String,
    val email: String,
    val tag: String = "",
    val school: String = "",
    val avatarColor: Int = -1,
    /** 평소 가능 시간 (새 프로젝트에 합류할 때 기본값으로 복사) */
    val availability: WeekAvailability? = null,
    /** 알림 화면을 마지막으로 본 시각 */
    val notifReadAt: Long = 0,
    val createdAt: Long = 0,
) {
    val handle: String get() = UserTag.display(name, tag)

    fun toPublic() = PublicProfile(uid, name, tag, avatarColor, school)
}

enum class RoomType { PROJECT, DIRECT }

enum class MessageType { TEXT, IMAGE, FILE, NOTICE, POLL, TASK, SYSTEM }

data class LastMessage(
    val text: String,
    val type: MessageType,
    val senderUid: String,
    val senderName: String,
    val at: Long,
)

data class ChatRoom(
    val id: String,
    val type: RoomType,
    val projectId: String? = null,
    val memberUids: List<String> = emptyList(),
    /** uid → 이름 (1:1 방 제목, 보낸 사람 표시) */
    val names: Map<String, String> = emptyMap(),
    val lastMessage: LastMessage? = null,
    val updatedAt: Long = 0,
    val unread: Map<String, Long> = emptyMap(),
) {
    fun unreadFor(uid: String): Int = (unread[uid] ?: 0L).toInt()

    /** 1:1 방의 상대 uid */
    fun otherUid(myUid: String): String? = memberUids.firstOrNull { it != myUid }
}

data class ChatMessage(
    val id: String,
    val roomId: String,
    val senderUid: String,
    val senderName: String,
    val type: MessageType,
    val text: String,
    val file: Attachment? = null,
    /** 공지·투표·업무 메시지가 가리키는 문서 id */
    val refId: String? = null,
    val mentions: List<String> = emptyList(),
    val createdAt: Long = 0,
)
