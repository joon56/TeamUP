package com.modo.app

import android.app.Activity
import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Bundle
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.initialize
import com.modo.app.data.FileStore
import com.modo.app.data.Prefs
import com.modo.app.notify.AppNotifier

class ModoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Prefs.applyTheme()
        FileStore.init(this)
        // AI 회의록 요약(Firebase AI Logic)은 App Check가 필수. 디버그 빌드는 디버그 공급자를 쓰고,
        // Logcat의 "DebugAppCheckProvider" 비밀값을 콘솔(App Check → 디버그 토큰)에 등록해야 함
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Firebase.initialize(this)
            Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        }
        // 알림 채널을 만들고, 데이터가 바뀔 때 알림을 띄우는 감시를 시작
        AppNotifier.start(this)
        registerActivityLifecycleCallbacks(VisibleCounter)
    }

    /** 화면(액티비티) 생명주기를 세어 앱이 지금 보이는 중인지 판단: onStart에서 +1, onStop에서 -1 */
    private object VisibleCounter : ActivityLifecycleCallbacks {
        var started = 0

        override fun onActivityStarted(activity: Activity) {
            started++
        }

        override fun onActivityStopped(activity: Activity) {
            started--
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    companion object {
        /** 앱 화면이 하나라도 보이는 중인지 */
        val isForeground: Boolean get() = VisibleCounter.started > 0
    }
}
