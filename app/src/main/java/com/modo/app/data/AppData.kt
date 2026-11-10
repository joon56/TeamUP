package com.modo.app.data

/** 화면이 그릴 때 쓰는 전체 데이터 스냅샷. Firestore가 바뀔 때마다 새로 만들어짐 */
data class AppData(
    val myUid: String = "",
    val myName: String = "",
    val profile: UserProfile? = null,
    val projects: List<Project> = emptyList(),
    /** projectId → 팀원 목록 */
    val members: Map<String, List<Member>> = emptyMap(),
    /** projectId → 업무 목록 */
    val tasks: Map<String, List<Task>> = emptyMap(),
    /** projectId → 최근 공지 (최신순) */
    val notices: Map<String, List<Notice>> = emptyMap(),
    /** projectId → 회의록 (최신 회의순) */
    val minutes: Map<String, List<Minutes>> = emptyMap(),
    /** projectId → 파일 (최신순) */
    val files: Map<String, List<FileMeta>> = emptyMap(),
    /** projectId → 그 프로젝트에서 보낸 초대 */
    val projectInvites: Map<String, List<Invite>> = emptyMap(),
    /** 나에게 온 초대 */
    val receivedInvites: List<Invite> = emptyList(),
    /** 내가 참여한 채팅방 (최근 대화순) */
    val rooms: List<ChatRoom> = emptyList(),
    /** 나를 멘션한 메시지 (최신순) */
    val mentions: List<ChatMessage> = emptyList(),
    val loading: Boolean = true,
) {
    val allTasks: List<Task> get() = tasks.values.flatten()

    fun project(id: String?): Project? = projects.firstOrNull { it.id == id }
    fun membersOf(projectId: String): List<Member> = members[projectId].orEmpty()
    fun tasksOf(projectId: String): List<Task> = tasks[projectId].orEmpty()
    fun noticesOf(projectId: String): List<Notice> = notices[projectId].orEmpty()
    fun minutesOf(projectId: String): List<Minutes> = minutes[projectId].orEmpty()
    fun filesOf(projectId: String): List<FileMeta> = files[projectId].orEmpty()
    fun invitesOf(projectId: String): List<Invite> = projectInvites[projectId].orEmpty()
    fun task(projectId: String, taskId: String): Task? = tasksOf(projectId).firstOrNull { it.id == taskId }

    fun member(projectId: String, memberId: String?): Member? {
        if (memberId == null) return null
        return membersOf(projectId).firstOrNull { it.id == memberId }
    }

    /** 이 프로젝트에서 나를 가리키는 팀원 */
    fun me(projectId: String): Member? = membersOf(projectId).firstOrNull { it.uid == myUid }

    fun isLeader(projectId: String): Boolean = project(projectId)?.ownerUid == myUid

    fun room(roomId: String): ChatRoom? = rooms.firstOrNull { it.id == roomId }

    val unreadChats: Int get() = rooms.sumOf { it.unreadFor(myUid) }

    val pendingInvites: List<Invite> get() = receivedInvites.filter { it.status == InviteStatus.PENDING }
}
