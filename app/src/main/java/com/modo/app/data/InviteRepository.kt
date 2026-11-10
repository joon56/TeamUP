package com.modo.app.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * 태그·이메일로 찾은 사람에게 보내는 프로젝트 초대.
 * 초대는 projects/{pid}/invites/{id}에 저장하고, 받은 사람은 컬렉션 그룹 쿼리(toUid == 나)로 본다.
 * 수락하면 같은 batch에서 초대 상태를 ACCEPTED로 바꾸고 팀원이 된다 (보안 규칙이 이 짝을 확인).
 */
object InviteRepository {

    private const val TAG = "InviteRepository"

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var listener: ListenerRegistration? = null
    private var uid = ""

    private fun now() = System.currentTimeMillis()

    fun start(userId: String) {
        stop()
        uid = userId
        listener = db.collectionGroup("invites").whereEqualTo("toUid", userId)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) {
                    Log.w(TAG, "received invites listener", error)
                    return@addSnapshotListener
                }
                DataHub.receivedInvites = snap.documents
                    .mapNotNull { doc -> doc.data?.let { FirestoreMapper.inviteFromMap(doc.id, it) } }
                    .sortedByDescending { it.createdAt }
                DataHub.publish()
            }
    }

    fun stop() {
        listener?.remove()
        listener = null
        uid = ""
    }

    sealed class SendResult {
        object Sent : SendResult()
        object Self : SendResult()
        object AlreadyMember : SendResult()
        object AlreadyInvited : SendResult()
        data class Failed(val error: Exception) : SendResult()
    }

    fun send(project: Project, target: PublicProfile, onResult: (SendResult) -> Unit) {
        val data = DataHub.current
        when {
            target.uid == uid -> return onResult(SendResult.Self)
            target.uid in project.memberUids -> return onResult(SendResult.AlreadyMember)
            data.invitesOf(project.id).any { it.toUid == target.uid && it.status == InviteStatus.PENDING } ->
                return onResult(SendResult.AlreadyInvited)
        }
        val ref = TeamRepository.invitesRef(project.id).document()
        val invite = Invite(
            id = ref.id, projectId = project.id, projectName = project.name,
            fromUid = uid, fromName = data.myName, toUid = target.uid, toName = target.name,
            status = InviteStatus.PENDING, createdAt = now(),
        )
        ref.set(FirestoreMapper.inviteToMap(invite))
            .addOnSuccessListener { onResult(SendResult.Sent) }
            .addOnFailureListener { e ->
                Log.w(TAG, "send", e)
                onResult(SendResult.Failed(e))
            }
    }

    fun cancel(invite: Invite, onFailure: (Exception) -> Unit = {}) {
        TeamRepository.invitesRef(invite.projectId).document(invite.id)
            .update("status", InviteStatus.CANCELED.name)
            .addOnFailureListener(onFailure)
    }

    fun decline(invite: Invite, onFailure: (Exception) -> Unit = {}) {
        TeamRepository.invitesRef(invite.projectId).document(invite.id)
            .update(mapOf("status" to InviteStatus.DECLINED.name, "respondedAt" to now()))
            .addOnFailureListener(onFailure)
    }

    /** 수락: 초대 상태 + 내 팀원 문서 + 프로젝트 memberUids를 한 batch로 */
    fun accept(invite: Invite, role: String, onResult: (Exception?) -> Unit) {
        val batch = db.batch()
        batch.update(
            TeamRepository.invitesRef(invite.projectId).document(invite.id),
            mapOf("status" to InviteStatus.ACCEPTED.name, "respondedAt" to now()),
        )
        TeamRepository.addJoinByInvite(batch, invite.projectId, invite.id, role)
        batch.commit()
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener { e ->
                Log.w(TAG, "accept", e)
                onResult(e)
            }
    }
}
