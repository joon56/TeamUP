package com.teamup.app.ui.main

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import androidx.core.graphics.ColorUtils
import com.teamup.app.R
import com.teamup.app.data.AppData
import com.teamup.app.data.AuthService
import com.teamup.app.data.Prefs
import com.teamup.app.data.TeamRepository
import com.teamup.app.databinding.TabSettingsBinding
import com.teamup.app.ui.auth.LoginActivity
import com.teamup.app.ui.common.Dialogs
import com.teamup.app.ui.common.Items
import com.teamup.app.ui.common.addPressEffect
import com.teamup.app.ui.common.failureToast
import com.teamup.app.ui.common.sampleLabels
import com.teamup.app.ui.common.toast

/** 설정 탭: 내 정보, 테마, 언어, 시연용 샘플, 로그아웃, 앱 정보 */
class SettingsTab(private val activity: MainActivity, private val b: TabSettingsBinding) {

    private var data = AppData()

    init {
        // 테마·언어는 AppData가 아니라 Prefs(기기 설정)가 기준.
        // 현재 값을 먼저 체크한 뒤 리스너를 붙여야 처음 표시할 때 리스너가 불리지 않음
        b.themeGroup.check(
            when (Prefs.themeMode) {
                Prefs.ThemeMode.DARK -> R.id.themeDark
                Prefs.ThemeMode.LIGHT -> R.id.themeLight
                Prefs.ThemeMode.SYSTEM -> R.id.themeSystem
            }
        )
        b.languageGroup.check(
            when (Prefs.language) {
                "ko" -> R.id.langKo
                "en" -> R.id.langEn
                else -> R.id.langSystem
            }
        )

        b.themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.themeLight -> Prefs.ThemeMode.LIGHT
                R.id.themeSystem -> Prefs.ThemeMode.SYSTEM
                else -> Prefs.ThemeMode.DARK
            }
            // 저장과 동시에 적용 → 화면이 새 테마로 다시 만들어짐
            if (mode != Prefs.themeMode) Prefs.themeMode = mode
        }
        b.languageGroup.setOnCheckedChangeListener { _, checkedId ->
            val language = when (checkedId) {
                R.id.langKo -> "ko"
                R.id.langEn -> "en"
                else -> ""
            }
            // values / values-en 리소스가 바뀌도록 앱 언어 변경 (화면이 다시 만들어짐)
            if (language != Prefs.language) Prefs.language = language
        }

        b.changeName.setOnClickListener {
            Dialogs.showName(activity, data.myName) { name ->
                TeamRepository.updateMyName(name, activity.failureToast())
            }
        }

        b.sampleCard.setOnClickListener {
            TeamRepository.createSampleProjects(activity.sampleLabels(), activity.failureToast())
            activity.toast(R.string.msg_sample_created)
        }
        b.sampleCard.addPressEffect()

        b.logout.setOnClickListener {
            Dialogs.confirm(activity, R.string.confirm_logout, null, R.string.settings_logout) {
                AuthService.signOut()
                activity.startActivity(
                    Intent(activity, LoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                activity.finish()
            }
        }

        b.version.text = activity.getString(R.string.about_version, versionName())
    }

    fun render(data: AppData) {
        this.data = data
        val name = data.myName
        b.name.text = name
        b.email.text = AuthService.currentEmail

        // 이니셜 아바타: 이름마다 고정된 색
        val color = Items.avatarColor(activity, name)
        b.avatarBg.imageTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x40))
        b.avatarInitial.text = name.take(1).ifEmpty { "?" }
        b.avatarInitial.setTextColor(color)
    }

    // 테마·언어는 Prefs에 저장되므로 화면 회전 때 따로 저장할 상태가 없음
    fun saveState(out: Bundle) {}

    fun restoreState(state: Bundle) {}

    /** build.gradle.kts의 versionName (BuildConfig를 만들지 않으므로 PackageManager에서 읽음) */
    private fun versionName(): String {
        val pm = activity.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(activity.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(activity.packageName, 0)
        }
        return info.versionName ?: ""
    }
}
