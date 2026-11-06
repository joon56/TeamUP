package com.teamup.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

/** Firebase 이메일/비밀번호 로그인 */
object AuthService {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    val currentUid: String? get() = auth.currentUser?.uid

    val currentEmail: String get() = auth.currentUser?.email.orEmpty()

    /** 이름이 아직 Firestore에서 안 왔을 때 쓸 임시 이름 */
    val fallbackName: String
        get() {
            val user = auth.currentUser ?: return ""
            return user.displayName?.takeIf { it.isNotBlank() } ?: user.email.orEmpty().substringBefore('@')
        }

    fun signIn(email: String, password: String, callback: (Exception?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { callback(null) }
            .addOnFailureListener { callback(it) }
    }

    fun signUp(name: String, email: String, password: String, callback: (Exception?) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                // 이름은 Auth 프로필에 저장 → 메인 화면에서 데이터 구독을 시작할 때 users 문서를 만듦
                // (가입 콜백에서 바로 Firestore에 쓰면 아직 로그인 전 사용자로 처리돼 거부될 수 있음)
                result.user?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build())
                    ?.addOnCompleteListener { callback(null) }
                    ?: callback(null)
            }
            .addOnFailureListener { callback(it) }
    }

    fun sendPasswordReset(email: String, callback: (Exception?) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { callback(null) }
            .addOnFailureListener { callback(it) }
    }

    fun signOut() {
        TeamRepository.stop()
        auth.signOut()
    }
}
