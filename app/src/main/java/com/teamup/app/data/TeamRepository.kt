package com.teamup.app.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import com.teamup.app.logic.InviteCode
import com.teamup.app.logic.TaskRules
import java.time.LocalDate

/**
 * 앱 데이터의 단일 창구.
 * - 읽기: Firestore 실시간 리스너가 바뀐 내용을 [data](LiveData)로 내보냄 → 화면은 observe 해서 다시 그림
 * - 쓰기: Firestore에 바로 기록. 오프라인이어도 로컬 캐시에 먼저 반영되고 리스너가 즉시 화면을 갱신함
 *
 * Firestore 구조
 *   users/{uid}
 *   inviteCodes/{code}            → projectId
 *   projects/{projectId}
 *   projects/{projectId}/members/{memberId}
 *   projects/{projectId}/tasks/{taskId}
 */
object TeamRepository {

    private const val TAG = "TeamRepository"

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val _data = MutableLiveData(AppData())
    val data: LiveData<AppData> = _data

    /** 현재 스냅샷 (리스너 밖에서 바로 읽을 때) */
    val current: AppData get() = _data.value ?: AppData()

    private var uid = ""
    private var myName = ""
    private var projects = listOf<Project>()
    private val members = mutableMapOf<String, List<Member>>()
    private val tasks = mutableMapOf<String, List<Task>>()
    private var projectsLoaded = false

    private var userListener: ListenerRegistration? = null
    private var projectsListener: ListenerRegistration? = null
    private val childListeners = mutableMapOf<String, List<ListenerRegistration>>()

    private const val MAX_RETRIES = 6
    private const val RETRY_DELAY_MS = 1500L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val retryCounts = mutableMapOf<String, Int>()

    private fun projectRef(projectId: String) = db.collection("projects").document(projectId)
    private fun membersRef(projectId: String) = projectRef(projectId).collection("members")
    private fun tasksRef(projectId: String) = projectRef(projectId).collection("tasks")
    private fun codeRef(code: String) = db.collection("inviteCodes").document(code)
    private fun userRef(userId: String) = db.collection("users").document(userId)

    private fun now() = System.currentTimeMillis()

    // ───────────────────────── 실시간 리스너 ─────────────────────────

    /** 로그인한 사용자의 데이터 구독 시작. 같은 사용자로 다시 불러도 안전 */
    fun start(userId: String, fallbackName: String, email: String) {
        if (uid == userId && projectsListener != null) return
        stop()
        uid = userId
        myName = fallbackName
        publish()

        userListener = userRef(userId).addSnapshotListener { snap, error ->
            if (error != null || snap == null) {
                Log.w(TAG, "user listener", error)
                return@addSnapshotListener
            }
            // 가입 직후에는 Firestore가 아직 로그인 정보를 못 받아 문서 생성이 거부될 수 있으므로,
            // 서버에서 "문서 없음"이 확인되면 여기서 만든다 (캐시만 보고 판단하면 다른 기기의 이름을 덮어쓸 수 있음)
            if (!snap.exists() && !snap.metadata.isFromCache) {
                createUserProfile(userId, fallbackName, email)
            }
            snap.getString("name")?.takeIf { it.isNotBlank() }?.let { myName = it }
            publish()
        }

        projectsListener = db.collection("projects")
            .whereArrayContains("memberUids", userId)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) {
                    Log.w(TAG, "projects listener", error)
                    return@addSnapshotListener
                }
                projects = snap.documents
                    .mapNotNull { doc -> doc.data?.let { FirestoreMapper.projectFromMap(doc.id, it) } }
                    .sortedBy { it.endDate }
                projectsLoaded = true
                syncChildListeners()
                publish()
            }
    }

    /** 프로젝트 목록이 바뀌면 각 프로젝트의 팀원·업무 리스너를 붙이거나 뗀다 */
    private fun syncChildListeners() {
        val ids = projects.map { it.id }.toSet()
        for (id in childListeners.keys.toList()) {
            if (id !in ids) {
                childListeners.remove(id)?.forEach { it.remove() }
                members.remove(id)
                tasks.remove(id)
            }
        }
        for (id in ids) {
            if (id !in childListeners) attachChildListeners(id)
        }
    }

    private fun attachChildListeners(id: String) {
        val memberListener = membersRef(id).addSnapshotListener { snap, error ->
            if (error != null || snap == null) {
                onChildListenerError(id, "members", error)
                return@addSnapshotListener
            }
            if (!snap.metadata.isFromCache) retryCounts.remove(id)
            members[id] = snap.documents
                .mapNotNull { doc -> doc.data?.let { FirestoreMapper.memberFromMap(doc.id, id, it) } }
                .sortedBy { it.joinedAt }
            publish()
        }
        val taskListener = tasksRef(id).addSnapshotListener { snap, error ->
            if (error != null || snap == null) {
                onChildListenerError(id, "tasks", error)
                return@addSnapshotListener
            }
            if (!snap.metadata.isFromCache) retryCounts.remove(id)
            tasks[id] = snap.documents
                .mapNotNull { doc -> doc.data?.let { FirestoreMapper.taskFromMap(doc.id, id, it) } }
            publish()
        }
        childListeners[id] = listOf(memberListener, taskListener)
    }

    /**
     * 방금 만든 프로젝트는 서버 반영 전에 리스너가 먼저 붙어서 보안 규칙에 거부될 수 있다.
     * 거부된 리스너는 다시 동작하지 않으므로, 잠시 뒤(점점 길게) 다시 연결한다.
     * 프로젝트가 목록에서 사라졌으면(삭제·나가기) 다시 연결하지 않는다.
     */
    private fun onChildListenerError(id: String, what: String, error: Exception?) {
        Log.w(TAG, "$what listener $id", error)
        childListeners.remove(id)?.forEach { it.remove() }
        val tries = (retryCounts[id] ?: 0) + 1
        retryCounts[id] = tries
        if (tries > MAX_RETRIES) return
        mainHandler.postDelayed({
            if (projects.any { it.id == id } && id !in childListeners) attachChildListeners(id)
        }, RETRY_DELAY_MS * tries)
    }

    fun stop() {
        mainHandler.removeCallbacksAndMessages(null)
        retryCounts.clear()
        userListener?.remove()
        projectsListener?.remove()
        childListeners.values.flatten().forEach { it.remove() }
        userListener = null
        projectsListener = null
        childListeners.clear()
        uid = ""
        myName = ""
        projects = emptyList()
        members.clear()
        tasks.clear()
        projectsLoaded = false
        _data.value = AppData()
    }

    private fun publish() {
        _data.value = AppData(
            myUid = uid,
            myName = myName,
            projects = projects,
            members = members.toMap(),
            tasks = tasks.toMap(),
            loading = !projectsLoaded,
        )
    }

    private fun logFailure(what: String, onFailure: (Exception) -> Unit): (Exception) -> Unit = { e ->
        Log.w(TAG, what, e)
        onFailure(e)
    }

    /**
     * 보안 규칙이 쓰기마다 프로젝트 문서를 get()으로 확인하는데, batch 하나당 get() 호출은 최대 20회.
     * 그래서 쓰기를 15개씩 나눠서 순서대로 커밋한다 (같은 기기의 쓰기는 보낸 순서대로 반영됨).
     */
    private fun commitInChunks(writes: List<(WriteBatch) -> Unit>, onFailure: (Exception) -> Unit) {
        for (chunk in writes.chunked(15)) {
            val batch = db.batch()
            for (write in chunk) write(batch)
            batch.commit().addOnFailureListener(onFailure)
        }
    }

    // ───────────────────────── 프로젝트 ─────────────────────────

    /** 새 프로젝트를 만들고 만든 사람을 첫 팀원(팀장)으로 등록. 새 프로젝트 id를 돌려줌 */
    fun createProject(input: Project, leaderRole: String, onFailure: (Exception) -> Unit = {}): String {
        val ref = db.collection("projects").document()
        val code = InviteCode.generate()
        val time = now()
        val project = input.copy(
            id = ref.id, inviteCode = code, ownerUid = uid, memberUids = listOf(uid),
            createdAt = time, updatedAt = time,
        )
        val me = Member(id = uid, projectId = ref.id, name = myName, role = leaderRole, uid = uid, joinedAt = time)

        db.batch()
            .set(ref, FirestoreMapper.projectToMap(project))
            .set(membersRef(ref.id).document(uid), FirestoreMapper.memberToMap(me))
            .set(codeRef(code), mapOf("projectId" to ref.id, "ownerUid" to uid))
            .commit()
            .addOnFailureListener(logFailure("createProject", onFailure))
        return ref.id
    }

    /** 이름·설명·기간·색·회의 정보만 수정 (팀원 목록·초대 코드는 그대로) */
    fun updateProject(project: Project, onFailure: (Exception) -> Unit = {}) {
        val fields = mapOf(
            "name" to project.name,
            "description" to project.description,
            "startDate" to project.startDate.toString(),
            "endDate" to project.endDate.toString(),
            "colorIndex" to project.colorIndex,
            "meetingDay" to project.meetingDay,
            "meetingTime" to project.meetingTime.toString(),
            "updatedAt" to now(),
        )
        projectRef(project.id).update(fields).addOnFailureListener(logFailure("updateProject", onFailure))
    }

    /** 프로젝트 삭제 (만든 사람만). 하위 팀원·업무 문서와 초대 코드도 함께 지움 */
    fun deleteProject(project: Project, onFailure: (Exception) -> Unit = {}) {
        val writes = mutableListOf<(WriteBatch) -> Unit>()
        for (task in current.tasksOf(project.id)) writes.add { it.delete(tasksRef(project.id).document(task.id)) }
        for (member in current.membersOf(project.id)) writes.add { it.delete(membersRef(project.id).document(member.id)) }
        // 프로젝트 문서는 마지막에 지워야 위의 삭제가 보안 규칙(팀원 확인)을 통과함
        if (project.inviteCode.isNotEmpty()) writes.add { it.delete(codeRef(project.inviteCode)) }
        writes.add { it.delete(projectRef(project.id)) }
        commitInChunks(writes, logFailure("deleteProject", onFailure))
    }

    /** 프로젝트에서 나가기: 내 담당 업무는 담당자 없음으로 바꾸고 팀원에서 빠짐 */
    fun leaveProject(project: Project, onFailure: (Exception) -> Unit = {}) {
        val writes = mutableListOf<(WriteBatch) -> Unit>()
        for (task in current.tasksOf(project.id)) {
            if (task.assigneeId == uid) {
                writes.add { it.update(tasksRef(project.id).document(task.id), mapOf("assigneeId" to null, "updatedAt" to now())) }
            }
        }
        writes.add { it.delete(membersRef(project.id).document(uid)) }
        // 접근 권한이 사라지는 쓰기는 마지막
        writes.add { it.update(projectRef(project.id), "memberUids", FieldValue.arrayRemove(uid)) }
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
     * 보안 규칙이 (1) 내 팀원 문서에 적은 코드가 프로젝트 코드와 같은지, (2) memberUids에 나만 추가됐는지 확인함
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
                    current.project(projectId) != null -> onResult(JoinResult.AlreadyMember(projectId))
                    else -> {
                        val member = mapOf(
                            "name" to myName, "role" to memberRole, "uid" to uid,
                            "joinedAt" to now(), "joinCode" to code,
                        )
                        db.batch()
                            .set(membersRef(projectId).document(uid), member)
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

    // ───────────────────────── 팀원 ─────────────────────────

    /** 앱을 쓰지 않는 팀원을 이름·역할로 추가 */
    fun addMember(projectId: String, name: String, role: String, onFailure: (Exception) -> Unit = {}) {
        val ref = membersRef(projectId).document()
        val member = Member(id = ref.id, projectId = projectId, name = name, role = role, uid = null, joinedAt = now())
        ref.set(FirestoreMapper.memberToMap(member)).addOnFailureListener(logFailure("addMember", onFailure))
    }

    fun updateMember(member: Member, name: String, role: String, onFailure: (Exception) -> Unit = {}) {
        membersRef(member.projectId).document(member.id)
            .update(mapOf("name" to name, "role" to role))
            .addOnFailureListener(logFailure("updateMember", onFailure))
    }

    /** 팀원 삭제. 담당 업무는 담당자 없음으로, 앱 사용자면 프로젝트 접근 권한도 제거 */
    fun removeMember(member: Member, onFailure: (Exception) -> Unit = {}) {
        val writes = mutableListOf<(WriteBatch) -> Unit>()
        for (task in current.tasksOf(member.projectId)) {
            if (task.assigneeId == member.id) {
                writes.add { it.update(tasksRef(member.projectId).document(task.id), mapOf("assigneeId" to null, "updatedAt" to now())) }
            }
        }
        writes.add { it.delete(membersRef(member.projectId).document(member.id)) }
        val memberUid = member.uid
        if (memberUid != null) {
            writes.add { it.update(projectRef(member.projectId), "memberUids", FieldValue.arrayRemove(memberUid)) }
        }
        commitInChunks(writes, logFailure("removeMember", onFailure))
    }

    // ───────────────────────── 업무 ─────────────────────────

    /** id가 비어 있으면 새 업무, 아니면 수정. 저장된 업무 id를 돌려줌 */
    fun saveTask(task: Task, onFailure: (Exception) -> Unit = {}): String {
        val time = now()
        val isNew = task.id.isEmpty()
        val ref = if (isNew) tasksRef(task.projectId).document() else tasksRef(task.projectId).document(task.id)
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

    // ───────────────────────── 내 정보 ─────────────────────────

    /** 회원가입 직후 사용자 문서 생성 */
    fun createUserProfile(userId: String, name: String, email: String) {
        userRef(userId).set(mapOf("name" to name, "email" to email, "createdAt" to now()), SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "createUserProfile", it) }
    }

    /** 내 이름 변경: 사용자 문서 + 내가 속한 모든 프로젝트의 내 팀원 문서 */
    fun updateMyName(name: String, onFailure: (Exception) -> Unit = {}) {
        myName = name
        val batch = db.batch()
        batch.set(userRef(uid), mapOf("name" to name), SetOptions.merge())
        for (project in projects) {
            if (current.member(project.id, uid) != null) {
                batch.update(membersRef(project.id).document(uid), "name", name)
            }
        }
        batch.commit().addOnFailureListener(logFailure("updateMyName", onFailure))
        publish()
    }

    // ───────────────────────── 샘플 데이터 ─────────────────────────

    /** 시연용 샘플 프로젝트 3개 생성 (오늘 날짜 기준으로 일정이 잡힘) */
    fun createSampleProjects(labels: SampleData.Labels, onFailure: (Exception) -> Unit = {}) {
        val samples = SampleData.build(
            myUid = uid,
            myName = myName,
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
            writes.add { it.set(projectRef(p.id), FirestoreMapper.projectToMap(p)) }
            writes.add { it.set(codeRef(p.inviteCode), mapOf("projectId" to p.id, "ownerUid" to uid)) }
            for (m in sample.members) writes.add { it.set(membersRef(p.id).document(m.id), FirestoreMapper.memberToMap(m)) }
            for (t in sample.tasks) writes.add { it.set(tasksRef(p.id).document(t.id), FirestoreMapper.taskToMap(t)) }
        }
        commitInChunks(writes, logFailure("createSampleProjects", onFailure))
    }
}
