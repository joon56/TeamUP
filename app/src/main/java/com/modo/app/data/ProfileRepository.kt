package com.modo.app.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import com.modo.app.logic.UserTag
import com.modo.app.logic.WeekAvailability
import kotlin.random.Random

/**
 * 내 프로필(users/{uid})과 검색용 공개 프로필(userHandles, userEmails).
 * - 태그(이름#1234)는 처음 로그인할 때 자동 발급. 이미 쓰는 태그면 보안 규칙이 거부 → 다시 뽑음
 * - 이름·아바타·학교를 바꾸면 내가 속한 프로젝트의 팀원 문서와 채팅방 이름도 함께 바꿈
 */
object ProfileRepository {

    private const val TAG = "ProfileRepository"
    private const val MAX_TAG_TRIES = 6

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private var listener: ListenerRegistration? = null
    private var uid = ""
    private var email = ""
    private var tagIssuing = false
    private var lookupsChecked = false

    private fun userRef(id: String) = db.collection("users").document(id)
    private fun handleRef(key: String) = db.collection("userHandles").document(key)
    private fun emailRef(email: String) = db.collection("userEmails").document(UserTag.normalizeEmail(email))

    private fun now() = System.currentTimeMillis()

    fun start(userId: String, fallbackName: String, userEmail: String) {
        stop()
        uid = userId
        email = userEmail
        // 캐시에서 먼저 읽은 경우 서버 확인(메타데이터만 바뀜)도 받아야 "문서 없음·태그 없음"을 확정할 수 있음
        listener = userRef(userId).addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
            if (error != null || snap == null) {
                Log.w(TAG, "profile listener", error)
                return@addSnapshotListener
            }
            // 가입 직후에는 Firestore가 아직 로그인 정보를 못 받아 문서 생성이 거부될 수 있으므로,
            // 서버에서 "문서 없음"이 확인되면 여기서 만든다 (캐시만 보고 판단하면 다른 기기의 값을 덮어쓸 수 있음)
            if (!snap.exists()) {
                if (!snap.metadata.isFromCache) {
                    val pending = Prefs.pendingProfile
                    createProfile(userId, fallbackName, userEmail, pending?.school.orEmpty(), pending?.avatarColor ?: -1)
                    Prefs.pendingProfile = null
                }
                return@addSnapshotListener
            }
            val profile = FirestoreMapper.profileFromMap(userId, snap.data.orEmpty())
            DataHub.profile = profile
            DataHub.publish()
            if (!snap.metadata.isFromCache) {
                if (profile.tag.isEmpty()) issueTag(profile)
                else if (!lookupsChecked) {
                    lookupsChecked = true
                    writeLookups(profile)
                }
            }
        }
    }

    fun stop() {
        listener?.remove()
        listener = null
        uid = ""
        email = ""
        tagIssuing = false
        lookupsChecked = false
    }

    /** 첫 로그인: 사용자 문서 생성 (가입 화면에서 받은 학교·아바타가 있으면 함께) */
    fun createProfile(userId: String, name: String, userEmail: String, school: String = "", avatarColor: Int = -1) {
        val color = if (avatarColor >= 0) avatarColor else Random.nextInt(8)
        val data = mapOf(
            "name" to name, "email" to userEmail, "school" to school,
            "avatarColor" to color, "createdAt" to now(),
        )
        userRef(userId).set(data, SetOptions.merge()).addOnFailureListener { Log.w(TAG, "createProfile", it) }
    }

    /** 태그 발급 (v0.1 사용자 포함). 같은 이름#태그가 이미 있으면 규칙이 거부하므로 다른 번호로 재시도 */
    private fun issueTag(profile: UserProfile, attempt: Int = 1) {
        if (attempt == 1) {
            if (tagIssuing) return
            tagIssuing = true
        }
        if (attempt > MAX_TAG_TRIES || profile.name.isBlank()) {
            tagIssuing = false
            return
        }
        val tag = UserTag.generate()
        val public = profile.copy(tag = tag).toPublic()
        handleRef(UserTag.handleKey(profile.name, tag)).set(FirestoreMapper.publicProfileToMap(public))
            .addOnSuccessListener {
                userRef(profile.uid).update("tag", tag)
                    .addOnCompleteListener { tagIssuing = false }
                writeEmailLookup(public)
                syncMemberDocs(profile.copy(tag = tag))
            }
            .addOnFailureListener {
                Log.w(TAG, "tag $tag taken, retry", it)
                issueTag(profile, attempt + 1)
            }
    }

    /** 검색 문서가 지금 프로필과 같도록 (앱 시작 시 한 번) */
    private fun writeLookups(profile: UserProfile) {
        val public = profile.toPublic()
        handleRef(UserTag.handleKey(profile.name, profile.tag)).set(FirestoreMapper.publicProfileToMap(public))
            .addOnFailureListener { Log.w(TAG, "handle lookup", it) }
        writeEmailLookup(public)
    }

    private fun writeEmailLookup(public: PublicProfile) {
        val address = (DataHub.profile?.email?.takeIf { it.isNotBlank() } ?: email)
        if (address.isBlank()) return
        emailRef(address).set(FirestoreMapper.publicProfileToMap(public))
            .addOnFailureListener { Log.w(TAG, "email lookup", it) }
    }

    /** 내가 속한 프로젝트의 내 팀원 문서에 이름·태그·아바타 반영 + 채팅방 이름표 */
    private fun syncMemberDocs(profile: UserProfile) {
        val batch = db.batch()
        var count = 0
        for (project in DataHub.projects) {
            if (DataHub.members[project.id].orEmpty().any { it.id == profile.uid }) {
                batch.update(
                    db.collection("projects").document(project.id).collection("members").document(profile.uid),
                    mapOf("name" to profile.name, "tag" to profile.tag, "avatarColor" to profile.avatarColor),
                )
                count++
            }
        }
        for (room in DataHub.rooms) {
            if (room.names[profile.uid] != profile.name) {
                batch.update(db.collection("chatRooms").document(room.id), "names.${profile.uid}", profile.name)
                count++
            }
        }
        if (count > 0) batch.commit().addOnFailureListener { Log.w(TAG, "syncMemberDocs", it) }
    }

    /**
     * 이름·학교·아바타 변경.
     * 이름이 바뀌면 같은 태그로 새 검색 문서를 만들고(이미 있으면 새 태그) 옛 문서를 지움.
     */
    fun updateProfile(name: String, school: String, avatarColor: Int, onFailure: (Exception) -> Unit = {}) {
        val old = DataHub.profile ?: return
        val updated = old.copy(name = name, school = school, avatarColor = avatarColor)
        DataHub.profile = updated
        DataHub.publish()
        if (name == old.name || old.tag.isEmpty()) {
            userRef(old.uid).update(mapOf("name" to name, "school" to school, "avatarColor" to avatarColor))
                .addOnFailureListener(onFailure)
            if (old.tag.isNotEmpty()) writeLookups(updated)
            syncMemberDocs(updated)
            return
        }
        renameWithTag(old, updated, old.tag, 1, onFailure)
    }

    private fun renameWithTag(old: UserProfile, updated: UserProfile, tag: String, attempt: Int, onFailure: (Exception) -> Unit) {
        val next = updated.copy(tag = tag)
        handleRef(UserTag.handleKey(next.name, tag)).set(FirestoreMapper.publicProfileToMap(next.toPublic()))
            .addOnSuccessListener {
                if (UserTag.handleKey(old.name, old.tag) != UserTag.handleKey(next.name, tag)) {
                    handleRef(UserTag.handleKey(old.name, old.tag)).delete()
                }
                userRef(old.uid).update(
                    mapOf("name" to next.name, "school" to next.school, "avatarColor" to next.avatarColor, "tag" to tag)
                ).addOnFailureListener(onFailure)
                writeEmailLookup(next.toPublic())
                syncMemberDocs(next)
            }
            .addOnFailureListener { e ->
                if (attempt >= MAX_TAG_TRIES) onFailure(e) else renameWithTag(old, updated, UserTag.generate(), attempt + 1, onFailure)
            }
    }

    /** 평소 가능 시간 저장. applyToProjects면 내가 속한 모든 프로젝트의 내 팀원 문서에도 반영 */
    fun saveAvailability(week: WeekAvailability, applyToProjects: Boolean, onFailure: (Exception) -> Unit = {}) {
        val me = DataHub.uid
        // 프로필 문서가 생기기 전에 쓰면 이름 없는 문서가 먼저 생겨 태그가 안 나옴
        if (me.isEmpty() || DataHub.profile == null) return onFailure(IllegalStateException("profile not ready"))
        DataHub.profile = DataHub.profile?.copy(availability = week)
        DataHub.publish()
        val batch = db.batch()
        batch.set(userRef(me), mapOf("availability" to week.days), SetOptions.merge())
        if (applyToProjects) {
            for (project in DataHub.projects) {
                if (DataHub.members[project.id].orEmpty().any { it.id == me }) {
                    batch.update(
                        db.collection("projects").document(project.id).collection("members").document(me),
                        mapOf("availability" to week.days, "availabilityAt" to now()),
                    )
                }
            }
        }
        batch.commit().addOnFailureListener(onFailure)
    }

    fun markNotificationsRead() {
        val me = DataHub.uid
        if (me.isEmpty()) return
        val time = now()
        DataHub.profile = DataHub.profile?.copy(notifReadAt = time)
        DataHub.publish()
        userRef(me).update("notifReadAt", time).addOnFailureListener { Log.w(TAG, "notifReadAt", it) }
    }

    // ───────────────────────── 사람 찾기 ─────────────────────────

    sealed class SearchResult {
        data class Found(val profile: PublicProfile) : SearchResult()
        object NotFound : SearchResult()
        object Invalid : SearchResult()
        data class Failed(val error: Exception) : SearchResult()
    }

    /** "이름#1234" 또는 가입 이메일로 정확히 한 사람을 찾음 */
    fun search(raw: String, onResult: (SearchResult) -> Unit) {
        val ref = when (val query = UserTag.parse(raw)) {
            is UserTag.Query.Handle -> handleRef(UserTag.handleKey(query.name, query.tag))
            is UserTag.Query.Email -> emailRef(query.email)
            UserTag.Query.Invalid -> {
                onResult(SearchResult.Invalid)
                return
            }
        }
        ref.get()
            .addOnSuccessListener { snap ->
                val profile = snap.data?.let { FirestoreMapper.publicProfileFromMap(it) }
                onResult(if (profile == null) SearchResult.NotFound else SearchResult.Found(profile))
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "search", e)
                onResult(SearchResult.Failed(e))
            }
    }
}
