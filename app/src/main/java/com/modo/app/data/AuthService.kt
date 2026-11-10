package com.modo.app.data

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.exceptions.ClearCredentialException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest

/** Firebase 로그인: 이메일/비밀번호 · Google 계정 */
object AuthService {

    private const val TAG = "AuthService"

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    val currentUid: String? get() = auth.currentUser?.uid

    val currentEmail: String get() = auth.currentUser?.email.orEmpty()

    /** 이름이 아직 Firestore에서 안 왔을 때 쓸 임시 이름 (Google 이름의 '/'·'#'은 태그 주소에 못 쓰므로 공백으로) */
    val fallbackName: String
        get() {
            val user = auth.currentUser ?: return ""
            val name = user.displayName?.takeIf { it.isNotBlank() } ?: user.email.orEmpty().substringBefore('@')
            return name.replace('/', ' ').replace('#', ' ').trim().replace(Regex("\\s+"), " ")
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

    /**
     * Google ID 토큰으로 로그인. 처음 들어온 계정이면 isNewUser = true (이름은 Google 프로필 이름으로 시작).
     * 같은 Gmail로 이미 비밀번호 가입한 계정이면 Firebase가 그 계정에 Google 로그인을 이어 붙임 (uid 그대로).
     */
    fun signInWithGoogle(idToken: String, callback: (isNewUser: Boolean, error: Exception?) -> Unit) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
            .addOnSuccessListener { callback(it.additionalUserInfo?.isNewUser == true, null) }
            .addOnFailureListener { callback(false, it) }
    }

    fun sendPasswordReset(email: String, callback: (Exception?) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { callback(null) }
            .addOnFailureListener { callback(it) }
    }

    fun signOut(context: Context) {
        Session.stop()
        auth.signOut()
        // Google 계정 자동 선택 상태도 지워서 다음 로그인 때 계정을 다시 고를 수 있게
        val app = context.applicationContext
        CredentialManager.create(app).clearCredentialStateAsync(
            ClearCredentialStateRequest(), null, ContextCompat.getMainExecutor(app),
            object : CredentialManagerCallback<Void?, ClearCredentialException> {
                override fun onResult(result: Void?) = Unit
                override fun onError(e: ClearCredentialException) {
                    Log.w(TAG, "clearCredentialState", e)
                }
            },
        )
    }
}
