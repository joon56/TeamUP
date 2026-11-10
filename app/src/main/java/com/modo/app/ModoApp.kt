package com.modo.app

import android.app.Application
import android.content.pm.ApplicationInfo
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.initialize
import com.modo.app.data.Prefs

class ModoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Prefs.applyTheme()
        // AI 회의록 요약(Firebase AI Logic)은 App Check가 필수. 디버그 빌드는 디버그 공급자를 쓰고,
        // Logcat의 "DebugAppCheckProvider" 비밀값을 콘솔(App Check → 디버그 토큰)에 등록해야 함
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Firebase.initialize(this)
            Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        }
    }
}
