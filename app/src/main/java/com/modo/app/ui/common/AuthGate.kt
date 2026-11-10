package com.modo.app.ui.common

import android.app.Activity
import android.content.Intent
import com.modo.app.data.AuthService
import com.modo.app.data.Session
import com.modo.app.ui.auth.LoginActivity

/**
 * 모든 화면 onCreate에서 호출.
 * 로그인 안 돼 있으면 로그인 화면으로 보내고 false,
 * 로그인 돼 있으면 데이터 구독을 시작(이미 했으면 그대로)하고 true.
 * (앱이 백그라운드에서 종료됐다가 복원돼도 데이터가 다시 연결됨)
 */
fun Activity.ensureSignedIn(): Boolean {
    val uid = try {
        AuthService.currentUid
    } catch (e: IllegalStateException) {
        null // google-services.json 없이 빌드한 경우
    }
    if (uid == null) {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
        return false
    }
    Session.start(uid, AuthService.fallbackName, AuthService.currentEmail)
    return true
}
