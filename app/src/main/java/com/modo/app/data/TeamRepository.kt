package com.modo.app.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.WriteBatch
import com.modo.app.logic.InviteCode
import com.modo.app.logic.TaskRules
import com.modo.app.logic.WeekAvailability
import java.time.LocalDate
import java.time.LocalTime

/**
 * 프로젝트와 그 하위 데이터(팀원·업무·공지·회의록·파일·보낸 초대)의 실시간 구독과 쓰기.
 * - 읽기: 리스너가 바뀐 내용을 [DataHub]에 넣고 publish → 화면은 observe 해서 다시 그림
 * - 쓰기: Firestore에 바로 기록. 오프라인이어도 로컬 캐시에 먼저 반영되고 리스너가 즉시 화면을 갱신함
 *
 * Firestore 구조
 *   inviteCodes/{code}            → projectId
 *   projects/{projectId}
 *   projects/{projectId}/members|tasks|notices|minutes|files|invites/{id}
 */
object TeamRepository {

    private const val TAG = "TeamRepository"

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    /** 기존 화면 코드 호환: 데이터는 DataHub에 모임 */
    val data get() = DataHub.data
    val current: AppData get() = DataHub.current

    private var uid = ""
    private var projectsListener: ListenerRegistration? = null
    private val childListeners = mutableMapOf<String, List<ListenerRegistration>>()

    private const val MAX_RETRIES = 6
    private const val RETRY_DELAY_MS = 1500L
    private const val NOTICE_LIMIT = 30L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val retryCounts = mutableMapOf<String, Int>()

    fun projectRef(projectId: String) = db.collection("projects").document(projectId)
    private fun membersRef(projectId: String) = projectRef(projectId).collection("members")
    private fun tasksRef(projectId: String) = projectRef(projectId).collection("tasks")
    private fun noticesRef(projectId: String) = projectRef(projectId).collection("notices")
    private fun minutesRef(projectId: String) = projectRef(projectId).collection("minutes")
    private fun filesRef(projectId: String) = projectRef(projectId).collection("files")
    fun invitesRef(projectId: String) = projectRef(projectId).collection("invites")
    private fun codeRef(code: String) = db.collection("inviteCodes").document(code)

    private fun now() = System.currentTimeMillis()

    // ───────────────────────── 실시간 리스너 ─────────────────────────

    fun start(userId: String) {
        stop()
        uid = userId
        projectsListener = db.collection("projects")
            .whereArrayContains("memberUids", userId)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) {
                    Log.w(TAG, "projects listener", error)
                    return@addSnapshotListener
                }
                DataHub.projects = snap.documents
                    .mapNotNull { doc -> doc.data?.let { FirestoreMapper.projectFromMap(doc.id, it) } }
                    .sortedBy { it.endDate }
                DataHub.projectsLoaded = true
                syncChildListeners()
                DataHub.publish()
                ChatRepository.reconcileProjectRooms()
            }
    }

    /** 프로젝트 목록이 바뀌면 각 프로젝트의 하위 리스너를 붙이거나 뗀다 */
    private fun syncChildListeners() {
        val ids = DataHub.projects.map { it.id }.toSet()
        for (id in childListeners.keys.toList()) {
            if (id !in ids) {
                childListeners.remove(id)?.forEach { it.remove() }
                DataHub.dropProject(id)
            }
        }
        for (id in ids) {
            if (id !in childListeners) attachChildListeners(id)
        }
    }

    private fun attachChildListeners(id: String) {
        val list = mutableListOf<ListenerRegistration>()
        list += membersRef(id).addSnapshotListener { snap, error ->
            if (error != null || snap == null) return@addSnapshotListener onChildListenerError(id, "members", error)
            if (!snap.metadata.isFromCache) retryCounts.remove(id)
            DataHub.members[id] = snap.documents
                .mapNotNull { doc -> doc.data?.let { FirestoreMapper.memberFromMap(doc.id, id, it) } }
                .sortedBy { it.joinedAt }
            DataHub.publish()
            ChatRepository.reconcileProjectRooms()
        }
        list += tasksRef(id).addSnapshotListener { snap, error ->
            if (error != null || snap == null) return@addSnapshotListener onChildListenerError(id, "tasks", error)
            DataHub.tasks[id] = snap.documents.mapNotNull { doc -> doc.data?.let { FirestoreMapper.taskFromMap(doc.id, id, it) } }
            DataHub.publish()
        }
        list += noticesRef(id).orderBy("createdAt", Query.Direction.DESCENDING).limit(NOTICE_LIMIT)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener onChildListenerError(id, "notices", error)
                DataHub.notices[id] = snap.documents.mapNotNull { doc -> doc.data?.let { FirestoreMapper.noticeFromMap(doc.id, id, it) } }
                DataHub.publish()
            }
        list += minutesRef(id).addSnapshotListener { snap, error ->
            if (error != null || snap == null) return@addSnapshotListener onChildListenerError(id, "minutes", error)
            DataHub.minutes[id] = snap.documents
                .mapNotNull { doc -> doc.data?.let { FirestoreMapper.minutesFromMap(doc.id, id, it) } }
                .sortedWith(compareByDescending<Minutes> { it.meetingDate }.thenByDescending { it.createdAt })
            DataHub.publish()
        }
        list += filesRef(id).addSnapshotListener { snap, error ->
            if (error != null || snap == null) return@addSnapshotListener onChildListenerError(id, "files", error)
            DataHub.files[id] = snap.documents
                .mapNotNull { doc -> doc.data?.let { FirestoreMapper.fileFromMap(doc.id, id, it) } }
                .sortedByDescending { it.createdAt }
            DataHub.publish()
        }
        list += invitesRef(id).addSnapshotListener { snap, error ->
            if (error != null || snap == null) return@addSnapshotListener onChildListenerError(id, "invites", error)
            DataHub.projectInvites[id] = snap.documents
                .mapNotNull { doc -> doc.data?.let { FirestoreMapper.inviteFromMap(doc.id, it) } }
                .sortedByDescending { it.createdAt }
            DataHub.publish()
        }
        childListeners[id] = list
    }

    /**
     * 방금 만들었거나 방금 합류한 프로젝트는 서버 반영 전에 리스너가 먼저 붙어서 보안 규칙에 거부될 수 있다.
     * 거부된 리스너는 다시 동작하지 않으므로, 잠시 뒤(점점 길게) 이 프로젝트의 리스너를 모두 다시 연결한다.
     * 프로젝트가 목록에서 사라졌으면(삭제·나가기) 다시 연결하지 않는다.
     */
    private fun onChildListenerError(id: String, what: String, error: Exception?) {
        Log.w(TAG, "$what listener $id", error)
        val registrations = childListeners.remove(id) ?: return
        registrations.forEach { it.remove() }
        val tries = (retryCounts[id] ?: 0) + 1
        retryCounts[id] = tries
        if (tries > MAX_RETRIES) return
        mainHandler.postDelayed({
            if (DataHub.projects.any { it.id == id } && id !in childListeners) attachChildListeners(id)
        }, RETRY_DELAY_MS * tries)
    }

    fun stop() {
        mainHandler.removeCallbacksAndMessages(null)
        retryCounts.clear()
        projectsListener?.remove()
        projectsListener = null
        childListeners.values.flatten().forEach { it.remove() }
        childListeners.clear()
        uid = ""
    }

    private fun logFailure(what: String, onFailure: (Exception) -> Unit): (Exception) -> Unit = { e ->
        Log.w(TAG, what, e)
        onFailure(e)
    }

    /**
     * 보안 규칙이 쓰기마다 프로젝트 문서를 get()으로 확인하는데, batch 하나당 get() 호출은 최대 20회.
     * 그래서 쓰기를 15개씩 나눠서 순서대로 커밋한다 (같은 기기의 쓰기는 보낸 순서대로 반영됨).
     */
    private fun commitInChunks(writes: List<(WriteBatch) -> Unit>, onFailure: (Exception) -> Unit, onDone: () -> Unit = {}) {
        val chunks = writes.chunked(15)
        if (chunks.isEmpty()) return onDone()
        for ((index, chunk) in chunks.withIndex()) {
            val batch = db.batch()
            for (write in chunk) write(batch)
            val task = batch.commit().addOnFailureListener(onFailure)
            if (index == chunks.lastIndex) task.addOnSuccessListener { onDone() }
        }
    }

    private fun myMember(projectId: String, role: String, time: Long, extra: Map<String, Any?> = emptyMap()): Map<String, Any?> {
        val profile = DataHub.profile
        val member = Member(
            id = uid, projectId = projectId, name = DataHub.current.myName, role = role, uid = uid, joinedAt = time,
            tag = profile?.tag.orEmpty(), avatarColor = profile?.avatarColor ?: -1, availability = profile?.availability,
            availabilityAt = if (profile?.availability != null) time else 0,
        )
        return FirestoreMapper.memberToMap(member) + extra
    }

    // ───────────────────────── 프로젝트 ─────────────────────────

    /** 새 프로젝트를 만들고 만든 사람을 첫 팀원(팀장)으로 등록 + 팀 채팅방 생성. 새 프로젝트 id를 돌려줌 */
    fun createProject(input: Project, leaderRole: String, onFailure: (Exception) -> Unit = {}): String {
        val ref = db.collection("projects").document()
        val code = InviteCode.generate()
        val time = now()
        val project = input.copy(
            id = ref.id, inviteCode = code, ownerUid = uid, memberUids = listOf(uid),
            createdAt = time, updatedAt = time,
        )
        val batch = db.batch()
            .set(ref, FirestoreMapper.projectToMap(project))
            .set(membersRef(ref.id).document(uid), myMember(ref.id, leaderRole, time))
            .set(codeRef(code), mapOf("projectId" to ref.id, "ownerUid" to uid))
        ChatRepository.addProjectRoomCreate(batch, project, mapOf(uid to DataHub.current.myName))
        batch.commit().addOnFailureListener(logFailure("createProject", onFailure))
        return ref.id
    }

    /** 이름·과목·설명·기간·색·회의 정보·모드 수정 (팀원 목록·초대 코드·팀장은 그대로) */
    fun updateProject(project: Project, onFailure: (Exception) -> Unit = {}) {
        val fields = mapOf(
            "name" to project.name,
            "description" to project.description,
            "subject" to project.subject,
            "startDate" to project.startDate.toString(),
            "endDate" to project.endDate.toString(),
            "colorIndex" to project.colorIndex,
            "meetingDay" to project.meetingDay,
            "meetingTime" to project.meetingTime.toString(),
            "meetingDurationMin" to project.meetingDurationMin,
            "meetingDecided" to project.meetingDecided,
            "mode" to project.mode.name,
            "updatedAt" to now(),
        )
        projectRef(project.id).update(fields).addOnFailureListener(logFailure("updateProject", onFailure))
    }

    fun setMode(projectId: String, mode: ProjectMode, onFailure: (Exception) -> Unit = {}) {
        projectRef(projectId).update(mapOf("mode" to mode.name, "updatedAt" to now()))
            .addOnFailureListener(logFailure("setMode", onFailure))
    }

    /** 정기 회의 시간 지정 (회의 시간 찾기에서 "정기 회의로 지정") */
    fun setMeeting(projectId: String, day: Int, time: LocalTime, durationMin: Int, onFailure: (Exception) -> Unit = {}) {
        projectRef(projectId).update(
            mapOf(
                "meetingDay" to day, "meetingTime" to time.toString(), "meetingDurationMin" to durationMin,
                "meetingDecided" to true, "updatedAt" to now(),
            )
        ).addOnFailureListener(logFailure("setMeeting", onFailure))
    }

    /** 팀장 넘기기 (현재 팀장만) */
    fun transferLeadership(project: Project, newOwnerUid: String, onFailure: (Exception) -> Unit = {}) {
        projectRef(project.id).update(mapOf("ownerUid" to newOwnerUid, "updatedAt" to now()))
            .addOnFailureListener(logFailure("transferLeadership", onFailure))
    }

    /** 프로젝트 삭제 (팀장만). 하위 문서·초대 코드·채팅방도 함께 지움 */
    fun deleteProject(project: Project, onFailure: (Exception) -> Unit = {}) {
        val pid = project.id
        val data = DataHub.current
        val writes = mutableListOf<(WriteBatch) -> Unit>()
        for (t in data.tasksOf(pid)) writes.add { it.delete(tasksRef(pid).document(t.id)) }
        for (n in data.noticesOf(pid)) writes.add { it.delete(noticesRef(pid).document(n.id)) }
        for (m in data.minutesOf(pid)) writes.add { it.delete(minutesRef(pid).document(m.id)) }
        for (f in data.filesOf(pid)) writes.add { it.delete(filesRef(pid).document(f.id)) }
        for (i in data.invitesOf(pid)) writes.add { it.delete(invitesRef(pid).document(i.id)) }
        for (m in data.membersOf(pid)) writes.add { it.delete(membersRef(pid).document(m.id)) }
        if (project.inviteCode.isNotEmpty()) writes.add { it.delete(codeRef(project.inviteCode)) }
        // 채팅방 → 프로젝트 순서: 프로젝트 문서가 먼저 지워지면 위 삭제들이 보안 규칙(팀원 확인)을 통과하지 못함
        ChatRepository.deleteProjectRoom(pid) {
            writes.add { it.delete(projectRef(pid)) }
            commitInChunks(writes, logFailure("deleteProject", onFailure))
        }
    }

    /** 프로젝트에서 나가기: 내 담당 업무는 담당자 없음으로 바꾸고 팀원·채팅방에서 빠짐 */
    fun leaveProject(project: Project, onFailure: (Exception) -> Unit = {}) {
        val writes = mutableListOf<(WriteBatch) -> Unit>()
        for (task in DataHub.current.tasksOf(project.id)) {
            if (task.assigneeId == uid) {
                writes.add { it.update(tasksRef(project.id).document(task.id), mapOf("assigneeId" to null, "updatedAt" to now())) }
            }
        }
        writes.add { it.delete(membersRef(project.id).document(uid)) }
        // 접근 권한이 사라지는 쓰기는 마지막 (프로젝트와 채팅방을 같은 batch로)
        val remaining = project.memberUids - uid
        writes.add { batch ->
            batch.update(projectRef(project.id), "memberUids", FieldValue.arrayRemove(uid))
            ChatRepository.addProjectRoomMembers(batch, project.id, remaining)
        }
        commitInChunks(writes, logFailure("leaveProject", onFailure))
    }

    // ───────────────────────── 초대 코드로 참여 ─────────────────────────

    sealed class JoinResult {
        data class Joined(val projectId: String) : JoinResult()
        data class AlreadyMember(val projectId: String) : JoinResult()
        object NotFound : JoinResult()
        data class Failed(val error: Exception) : JoinResult()
    }

    /**
     * 초대 코드로 프로젝트 참여.
     * 보안 규칙이 (1) 내 팀원 문서에 적은 코드가 프로젝트 코드와 같은지, (2) memberUids에 나만 추가됐는지 확인함.
     * 채팅방 참여는 합류 후 프로젝트를 읽을 수 있게 되면 ChatRepository가 맞춰 줌.
     */
    fun joinByCode(rawCode: String, memberRole: String, onResult: (JoinResult) -> Unit) {
        val code = InviteCode.normalize(rawCode)
        if (!InviteCode.isValid(code)) {
            onResult(JoinResult.NotFound)
            return
        }
        codeRef(code).get()
            .addOnSuccessListener { snap ->
                val projectId = snap.getString("projectId")
                when {
                    !snap.exists() || projectId == null -> onResult(JoinResult.NotFound)
                    DataHub.current.project(projectId) != null -> onResult(JoinResult.AlreadyMember(projectId))
                    else -> {
                        val time = now()
                        db.batch()
                            .set(membersRef(projectId).document(uid), myMember(projectId, memberRole, time, mapOf("joinCode" to code)))
                            .update(projectRef(projectId), "memberUids", FieldValue.arrayUnion(uid))
                            .commit()
                            .addOnSuccessListener { onResult(JoinResult.Joined(projectId)) }
                            .addOnFailureListener { e ->
                                Log.w(TAG, "join", e)
                                onResult(JoinResult.Failed(e))
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "join lookup", e)
                onResult(JoinResult.Failed(e))
            }
    }

    /** 받은 초대 수락 batch에 넣을 내 팀원 문서 (InviteRepository가 사용) */
    fun addJoinByInvite(batch: WriteBatch, projectId: String, inviteId: String, role: String) {
        batch.set(membersRef(projectId).document(uid), myMember(projectId, role, now(), mapOf("inviteId" to inviteId)))
        batch.update(projectRef(projectId), "memberUids", FieldValue.arrayUnion(uid))
    }

    // ───────────────────────── 팀원 ─────────────────────────

    /** 앱을 쓰지 않는 팀원을 이름·역할로 추가 (보조 기능) */
    fun addGuestMember(projectId: String, name: String, role: String, onFailure: (Exception) -> Unit = {}) {
        val ref = membersRef(projectId).document()
        val member = Member(id = ref.id, projectId = projectId, name = name, role = role, uid = null, joinedAt = now())
        ref.set(FirestoreMapper.memberToMap(member)).addOnFailureListener(logFailure("addGuestMember", onFailure))
    }

    fun updateMember(member: Member, name: String, role: String, onFailure: (Exception) -> Unit = {}) {
        membersRef(member.projectId).document(member.id)
            .update(mapOf("name" to name, "role" to role))
            .addOnFailureListener(logFailure("updateMember", onFailure))
    }

    fun setMemberAvailability(projectId: String, memberId: String, week: WeekAvailability?, onFailure: (Exception) -> Unit = {}) {
        membersRef(projectId).document(memberId).update(mapOf("availability" to week?.days, "availabilityAt" to now()))
            .addOnFailureListener(logFailure("setMemberAvailability", onFailure))
    }

    /** 팀원 내보내기(팀장) / 앱 안 쓰는 팀원 삭제. 담당 업무는 담당자 없음으로, 채팅방에서도 뺌 */
    fun removeMember(member: Member, onFailure: (Exception) -> Unit = {}) {
        val pid = member.projectId
        val writes = mutableListOf<(WriteBatch) -> Unit>()
        for (task in DataHub.current.tasksOf(pid)) {
            if (task.assigneeId == member.id) {
                writes.add { it.update(tasksRef(pid).document(task.id), mapOf("assigneeId" to null, "updatedAt" to now())) }
            }
        }
        writes.add { it.delete(membersRef(pid).document(member.id)) }
        val memberUid = member.uid
        if (memberUid != null) {
            val remaining = DataHub.current.project(pid)?.memberUids.orEmpty() - memberUid
            writes.add { batch ->
                batch.update(projectRef(pid), "memberUids", FieldValue.arrayRemove(memberUid))
                ChatRepository.addProjectRoomMembers(batch, pid, remaining)
            }
        }
        commitInChunks(writes, logFailure("removeMember", onFailure))
    }

    // ───────────────────────── 업무 ─────────────────────────

    fun newTaskId(projectId: String): String = tasksRef(projectId).document().id

    /** id가 비어 있으면 새 업무, 아니면 수정. 저장된 업무 id를 돌려줌 */
    fun saveTask(task: Task, onFailure: (Exception) -> Unit = {}): String {
        val time = now()
        val isNew = task.createdAt == 0L
        val ref = if (task.id.isEmpty()) tasksRef(task.projectId).document() else tasksRef(task.projectId).document(task.id)
        val saved = task.copy(
            id = ref.id,
            createdBy = if (isNew) uid else task.createdBy,
            createdAt = if (isNew) time else task.createdAt,
            updatedAt = time,
            completedAt = if (task.status == TaskStatus.DONE) (task.completedAt ?: time) else null,
        )
        ref.set(FirestoreMapper.taskToMap(saved)).addOnFailureListener(logFailure("saveTask", onFailure))
        return ref.id
    }

    fun setTaskStatus(task: Task, status: TaskStatus, onFailure: (Exception) -> Unit = {}) {
        val time = now()
        tasksRef(task.projectId).document(task.id)
            .update(
                mapOf(
                    "status" to status.name,
                    "updatedAt" to time,
                    "completedAt" to if (status == TaskStatus.DONE) time else null,
                )
            )
            .addOnFailureListener(logFailure("setTaskStatus", onFailure))
    }

    /** 다음 주로 미루기 → 새 마감일을 돌려줌 */
    fun postponeTask(task: Task, today: LocalDate, onFailure: (Exception) -> Unit = {}): LocalDate {
        val newDue = TaskRules.postponeDate(task.dueDate, today)
        tasksRef(task.projectId).document(task.id)
            .update(mapOf("dueDate" to newDue.toString(), "updatedAt" to now()))
            .addOnFailureListener(logFailure("postponeTask", onFailure))
        return newDue
    }

    fun deleteTask(task: Task, onFailure: (Exception) -> Unit = {}) {
        tasksRef(task.projectId).document(task.id).delete().addOnFailureListener(logFailure("deleteTask", onFailure))
    }

    // ───────────────────────── 공지·투표 ─────────────────────────

    /** 공지(투표 포함) 등록 + 팀 채팅방에 알림 메시지 */
    fun postNotice(project: Project, title: String, body: String, pinned: Boolean, poll: Poll?, onFailure: (Exception) -> Unit = {}): String {
        val ref = noticesRef(project.id).document()
        val time = now()
        val notice = Notice(
            id = ref.id, projectId = project.id, title = title, body = body, authorUid = uid,
            authorName = DataHub.current.myName, pinned = pinned, poll = poll, createdAt = time,
        )
        val batch = db.batch().set(ref, FirestoreMapper.noticeToMap(notice))
        val type = if (poll != null) MessageType.POLL else MessageType.NOTICE
        ChatRepository.addMessage(
            batch, ChatRepository.roomIdForProject(project.id), project.memberUids,
            type = type, text = title.ifBlank { body }, refId = ref.id,
        )
        batch.commit().addOnFailureListener(logFailure("postNotice", onFailure))
        return ref.id
    }

    fun setNoticePinned(notice: Notice, pinned: Boolean, onFailure: (Exception) -> Unit = {}) {
        noticesRef(notice.projectId).document(notice.id).update("pinned", pinned)
            .addOnFailureListener(logFailure("setNoticePinned", onFailure))
    }

    fun deleteNotice(notice: Notice, onFailure: (Exception) -> Unit = {}) {
        noticesRef(notice.projectId).document(notice.id).delete().addOnFailureListener(logFailure("deleteNotice", onFailure))
    }

    fun vote(notice: Notice, optionIds: List<String>, onFailure: (Exception) -> Unit = {}) {
        val ref = noticesRef(notice.projectId).document(notice.id).collection("votes").document(uid)
        if (optionIds.isEmpty()) ref.delete().addOnFailureListener(logFailure("vote", onFailure))
        else ref.set(mapOf("optionIds" to optionIds, "at" to now())).addOnFailureListener(logFailure("vote", onFailure))
    }

    /** 투표 결과 실시간 구독 (공지 화면이 열려 있는 동안) */
    fun listenVotes(notice: Notice, onChange: (List<Vote>) -> Unit): ListenerRegistration =
        noticesRef(notice.projectId).document(notice.id).collection("votes").addSnapshotListener { snap, error ->
            if (error != null || snap == null) {
                Log.w(TAG, "votes listener", error)
                return@addSnapshotListener
            }
            onChange(snap.documents.map { FirestoreMapper.voteFromMap(it.id, it.data.orEmpty()) })
        }

    // ───────────────────────── 회의록 ─────────────────────────

    fun newMinutesId(projectId: String): String = minutesRef(projectId).document().id

    fun saveMinutes(minutes: Minutes, onFailure: (Exception) -> Unit = {}): String {
        val time = now()
        val isNew = minutes.createdAt == 0L
        val ref = if (minutes.id.isEmpty()) minutesRef(minutes.projectId).document() else minutesRef(minutes.projectId).document(minutes.id)
        val saved = minutes.copy(
            id = ref.id,
            createdBy = if (isNew) uid else minutes.createdBy,
            createdByName = if (isNew) DataHub.current.myName else minutes.createdByName,
            createdAt = if (isNew) time else minutes.createdAt,
            updatedAt = time,
        )
        ref.set(FirestoreMapper.minutesToMap(saved)).addOnFailureListener(logFailure("saveMinutes", onFailure))
        return ref.id
    }

    fun deleteMinutes(minutes: Minutes, onFailure: (Exception) -> Unit = {}) {
        minutesRef(minutes.projectId).document(minutes.id).delete().addOnFailureListener(logFailure("deleteMinutes", onFailure))
    }

    // ───────────────────────── 파일 목록 ─────────────────────────

    fun addFile(projectId: String, attachment: Attachment, source: String, sourceId: String, onFailure: (Exception) -> Unit = {}) {
        val ref = filesRef(projectId).document()
        val meta = FileMeta(ref.id, projectId, attachment, DataHub.current.myName, source, sourceId, now())
        ref.set(FirestoreMapper.fileToMap(meta)).addOnFailureListener(logFailure("addFile", onFailure))
    }

    fun deleteFile(file: FileMeta, onFailure: (Exception) -> Unit = {}) {
        filesRef(file.projectId).document(file.id).delete().addOnFailureListener(logFailure("deleteFile", onFailure))
    }

    /** 파일함에서 지운 파일을 그 파일이 첨부된 업무·회의록의 첨부 목록에서도 뺌 */
    fun removeAttachment(file: FileMeta, onFailure: (Exception) -> Unit = {}) {
        val data = DataHub.current
        val (ref, current) = when (file.source) {
            "task" -> tasksRef(file.projectId) to data.tasksOf(file.projectId).firstOrNull { it.id == file.sourceId }?.attachments
            "minutes" -> minutesRef(file.projectId) to data.minutesOf(file.projectId).firstOrNull { it.id == file.sourceId }?.attachments
            else -> return
        }
        if (current == null || current.none { it.path == file.attachment.path }) return
        val left = current.filterNot { it.path == file.attachment.path }.map { FirestoreMapper.attachmentToMap(it) }
        ref.document(file.sourceId).update(mapOf("attachments" to left, "updatedAt" to now()))
            .addOnFailureListener(logFailure("removeAttachment", onFailure))
    }

    // ───────────────────────── 샘플 데이터 ─────────────────────────

    /** 시연용 샘플 프로젝트 생성 (오늘 날짜 기준으로 일정이 잡힘) */
    fun createSampleProjects(labels: SampleData.Labels, onFailure: (Exception) -> Unit = {}) {
        val samples = SampleData.build(
            myUid = uid,
            myName = DataHub.current.myName,
            today = LocalDate.now(),
            labels = labels,
            newProjectId = { db.collection("projects").document().id },
            newCode = { InviteCode.generate() },
            time = now(),
        )
        val writes = mutableListOf<(WriteBatch) -> Unit>()
        for (sample in samples) {
            val p = sample.project
            // 프로젝트 문서가 먼저 있어야 팀원·업무 쓰기가 보안 규칙을 통과함
            writes.add { batch ->
                batch.set(projectRef(p.id), FirestoreMapper.projectToMap(p))
                ChatRepository.addProjectRoomCreate(batch, p, mapOf(uid to DataHub.current.myName))
            }
            writes.add { it.set(codeRef(p.inviteCode), mapOf("projectId" to p.id, "ownerUid" to uid)) }
            for (m in sample.members) writes.add { it.set(membersRef(p.id).document(m.id), FirestoreMapper.memberToMap(m)) }
            for (t in sample.tasks) writes.add { it.set(tasksRef(p.id).document(t.id), FirestoreMapper.taskToMap(t)) }
        }
        commitInChunks(writes, logFailure("createSampleProjects", onFailure))
    }
}
