package com.modo.app.data

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch

/**
 * 채팅방(팀방 + 1:1)과 메시지.
 * - 팀방 id = "p_{프로젝트id}", 참여자 = 프로젝트 memberUids (보안 규칙이 같은지 확인)
 * - 1:1방 id = "d_{작은uid}_{큰uid}"
 * - 안 읽은 수: 보내는 사람이 같은 batch에서 다른 참여자의 unread.{uid}를 +1, 읽으면 내 값을 0으로
 * 푸시 알림은 서버(유료)가 필요해서 없고, 앱 안의 배지로만 알린다.
 */
object ChatRepository {

    private const val TAG = "ChatRepository"
    private const val MENTION_LIMIT = 30L

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var roomsListener: ListenerRegistration? = null
    private var mentionsListener: ListenerRegistration? = null
    private var uid = ""

    /** 프로젝트방 참여자를 맞추려고 이미 시도한 (프로젝트 → 참여자 서명) */
    private val syncAttempts = mutableMapOf<String, String>()

    fun roomIdForProject(projectId: String) = "p_$projectId"

    fun directRoomId(a: String, b: String): String = if (a < b) "d_${a}_$b" else "d_${b}_$a"

    fun projectIdOf(roomId: String): String? = roomId.removePrefix("p_").takeIf { roomId.startsWith("p_") }

    private fun roomRef(roomId: String) = db.collection("chatRooms").document(roomId)
    private fun messagesRef(roomId: String) = roomRef(roomId).collection("messages")

    private fun now() = System.currentTimeMillis()

    fun start(userId: String) {
        stop()
        uid = userId
        // 서버 확인 이벤트까지 받아야 "방이 아직 없음"을 확정하고 팀방 참여자를 맞출 수 있음
        roomsListener = db.collection("chatRooms").whereArrayContains("memberUids", userId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
                if (error != null || snap == null) {
                    Log.w(TAG, "rooms listener", error)
                    return@addSnapshotListener
                }
                DataHub.rooms = snap.documents
                    .mapNotNull { doc -> doc.data?.let { FirestoreMapper.roomFromMap(doc.id, it) } }
                    .sortedByDescending { it.updatedAt }
                DataHub.roomsLoaded = !snap.metadata.isFromCache || DataHub.roomsLoaded
                DataHub.publish()
                reconcileProjectRooms()
            }
        mentionsListener = db.collectionGroup("messages")
            .whereArrayContains("mentions", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(MENTION_LIMIT)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) {
                    Log.w(TAG, "mentions listener", error)
                    return@addSnapshotListener
                }
                DataHub.mentions = snap.documents.mapNotNull { doc ->
                    val roomId = doc.reference.parent.parent?.id ?: return@mapNotNull null
                    doc.data?.let { FirestoreMapper.messageFromMap(doc.id, roomId, it) }
                }
                DataHub.publish()
            }
    }

    fun stop() {
        roomsListener?.remove()
        mentionsListener?.remove()
        roomsListener = null
        mentionsListener = null
        syncAttempts.clear()
        uid = ""
    }

    // ───────────────────────── 팀방 참여자 맞추기 ─────────────────────────

    private fun appUserNames(projectId: String): Map<String, String> =
        DataHub.members[projectId].orEmpty().mapNotNull { m -> m.uid?.let { it to m.name } }.toMap()

    /** 새 프로젝트 batch에 팀방 생성 추가 */
    fun addProjectRoomCreate(batch: WriteBatch, project: Project, names: Map<String, String>) {
        batch.set(
            roomRef(roomIdForProject(project.id)),
            mapOf(
                "type" to RoomType.PROJECT.name, "projectId" to project.id, "memberUids" to project.memberUids,
                "names" to names, "updatedAt" to project.createdAt, "unread" to emptyMap<String, Long>(),
            ),
        )
    }

    /** 나가기·내보내기 batch에 팀방 참여자 변경 추가 (내가 들어가 있는 방이 있을 때만) */
    fun addProjectRoomMembers(batch: WriteBatch, projectId: String, memberUids: List<String>) {
        val roomId = roomIdForProject(projectId)
        if (DataHub.rooms.none { it.id == roomId }) return
        batch.update(roomRef(roomId), "memberUids", memberUids)
    }

    /**
     * 프로젝트 팀원과 팀방 참여자가 다르면(방이 없던 옛 프로젝트, 방금 합류, 누가 나감) 맞춘다.
     * 같은 상태로는 한 번만 시도한다.
     */
    fun reconcileProjectRooms() {
        if (uid.isEmpty() || !DataHub.roomsLoaded) return
        for (project in DataHub.projects) {
            if (uid !in project.memberUids) continue
            val expected = project.memberUids.toSet()
            val room = DataHub.rooms.firstOrNull { it.id == roomIdForProject(project.id) }
            if (room != null && room.memberUids.toSet() == expected) continue
            val signature = expected.sorted().joinToString(",")
            if (syncAttempts[project.id] == signature) continue
            syncAttempts[project.id] = signature
            val data = mutableMapOf<String, Any?>(
                "type" to RoomType.PROJECT.name, "projectId" to project.id, "memberUids" to project.memberUids,
                "names" to appUserNames(project.id),
            )
            if (room == null) data["updatedAt"] = project.createdAt
            roomRef(roomIdForProject(project.id)).set(data, SetOptions.merge())
                .addOnFailureListener { Log.w(TAG, "sync project room ${project.id}", it) }
        }
    }

    /** 프로젝트 삭제 전에 팀방 메시지와 방을 지운다. 지울 게 없거나 실패해도 onQueued는 불림 */
    fun deleteProjectRoom(projectId: String, onQueued: () -> Unit) {
        val roomId = roomIdForProject(projectId)
        if (DataHub.rooms.none { it.id == roomId }) return onQueued()
        messagesRef(roomId).limit(450).get()
            .addOnSuccessListener { snap ->
                for (chunk in snap.documents.chunked(15)) {
                    val batch = db.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit().addOnFailureListener { Log.w(TAG, "delete messages", it) }
                }
                roomRef(roomId).delete().addOnFailureListener { Log.w(TAG, "delete room", it) }
                onQueued()
            }
            .addOnFailureListener {
                Log.w(TAG, "list messages for delete", it)
                onQueued()
            }
    }

    // ───────────────────────── 1:1 방 ─────────────────────────

    /** 1:1 방이 없으면 만들고 방 id를 돌려줌 */
    fun ensureDirectRoom(otherUid: String, otherName: String, onFailure: (Exception) -> Unit = {}): String {
        val roomId = directRoomId(uid, otherUid)
        if (DataHub.rooms.any { it.id == roomId }) return roomId
        val members = listOf(uid, otherUid).sorted()
        roomRef(roomId).set(
            mapOf(
                "type" to RoomType.DIRECT.name, "memberUids" to members,
                "names" to mapOf(uid to DataHub.current.myName, otherUid to otherName), "updatedAt" to now(),
            ),
            SetOptions.merge(),
        ).addOnFailureListener {
            Log.w(TAG, "ensureDirectRoom", it)
            onFailure(it)
        }
        return roomId
    }

    // ───────────────────────── 메시지 ─────────────────────────

    /** 방 참여자 (방 문서가 아직 안 왔으면 id로 추정) */
    fun memberUidsOf(roomId: String): List<String> {
        DataHub.rooms.firstOrNull { it.id == roomId }?.let { return it.memberUids }
        projectIdOf(roomId)?.let { pid -> return DataHub.projects.firstOrNull { it.id == pid }?.memberUids.orEmpty() }
        if (roomId.startsWith("d_")) return roomId.removePrefix("d_").split("_").filter { it.isNotEmpty() }
        return listOf(uid)
    }

    /** 최근 [limit]개 메시지 실시간 구독 (오래된 것 → 최신 순으로 전달) */
    fun listenMessages(roomId: String, limit: Long, onChange: (List<ChatMessage>) -> Unit): ListenerRegistration =
        messagesRef(roomId).orderBy("createdAt", Query.Direction.DESCENDING).limit(limit)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) {
                    Log.w(TAG, "messages listener $roomId", error)
                    return@addSnapshotListener
                }
                onChange(snap.documents.mapNotNull { doc -> doc.data?.let { FirestoreMapper.messageFromMap(doc.id, roomId, it) } }.reversed())
            }

    /**
     * batch에 메시지 1개 + 방의 마지막 메시지·안 읽은 수 갱신을 추가.
     * 방 문서가 아직 없으면(옛 프로젝트) 방도 함께 만든다.
     */
    fun addMessage(
        batch: WriteBatch,
        roomId: String,
        memberUids: List<String>,
        type: MessageType,
        text: String,
        file: Attachment? = null,
        refId: String? = null,
        mentions: List<String> = emptyList(),
    ): String {
        val time = now()
        val myName = DataHub.current.myName
        val msgRef = messagesRef(roomId).document()
        val message = ChatMessage(msgRef.id, roomId, uid, myName, type, text, file, refId, mentions, time)
        batch.set(msgRef, FirestoreMapper.messageToMap(message))
        val last = FirestoreMapper.lastMessageToMap(LastMessage(text, type, uid, myName, time))
        val others = memberUids.filter { it != uid }
        if (DataHub.rooms.any { it.id == roomId }) {
            val fields = mutableMapOf<String, Any?>("lastMessage" to last, "updatedAt" to time)
            others.forEach { fields["unread.$it"] = FieldValue.increment(1) }
            batch.update(roomRef(roomId), fields)
        } else {
            val projectId = projectIdOf(roomId)
            val data = mutableMapOf<String, Any?>(
                "type" to (if (projectId != null) RoomType.PROJECT else RoomType.DIRECT).name,
                "memberUids" to if (projectId != null) memberUids else memberUids.sorted(),
                "lastMessage" to last, "updatedAt" to time,
                "unread" to others.associateWith { FieldValue.increment(1) },
            )
            if (projectId != null) data["projectId"] = projectId
            batch.set(roomRef(roomId), data, SetOptions.merge())
        }
        return msgRef.id
    }

    fun send(
        roomId: String,
        type: MessageType,
        text: String,
        file: Attachment? = null,
        refId: String? = null,
        mentions: List<String> = emptyList(),
        onFailure: (Exception) -> Unit = {},
    ) {
        val batch = db.batch()
        addMessage(batch, roomId, memberUidsOf(roomId), type, text, file, refId, mentions)
        batch.commit().addOnFailureListener {
            Log.w(TAG, "send", it)
            onFailure(it)
        }
    }

    fun deleteMessage(message: ChatMessage, onFailure: (Exception) -> Unit = {}) {
        messagesRef(message.roomId).document(message.id).delete().addOnFailureListener(onFailure)
    }

    /** 방을 열었을 때 내 안 읽은 수를 0으로 */
    fun markRead(roomId: String) {
        val room = DataHub.rooms.firstOrNull { it.id == roomId } ?: return
        if (room.unreadFor(uid) == 0) return
        roomRef(roomId).update("unread.$uid", 0).addOnFailureListener { Log.w(TAG, "markRead", it) }
    }
}
