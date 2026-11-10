package com.modo.app.ui.root

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import androidx.core.graphics.ColorUtils
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.AuthService
import com.modo.app.data.Prefs
import com.modo.app.data.ProfileRepository
import com.modo.app.data.TeamRepository
import com.modo.app.databinding.TabMeBinding
import com.modo.app.ui.auth.LoginActivity
import com.modo.app.ui.common.Dialogs
import com.modo.app.ui.common.Labels
import com.modo.app.ui.common.addPressEffect
import com.modo.app.ui.common.failureToast
import com.modo.app.ui.common.sampleLabels
import com.modo.app.ui.common.toast
import com.modo.app.ui.meeting.AvailabilityActivity

/** 루트 "내 정보" 탭: 프로필·태그, 가능 시간, 테마·언어, 시연, 로그아웃, 앱 정보 */
class MyInfoTab(private val activity: RootActivity, private val b: TabMeBinding) {

    private var data = AppData()

    init {
        // 테마·언어는 Prefs(기기 설정)가 기준. 현재 값을 먼저 체크한 뒤 리스너를 붙여야 처음에 리스너가 불리지 않음
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
            if (mode != Prefs.themeMode) Prefs.themeMode = mode
        }
        b.languageGroup.setOnCheckedChangeListener { _, checkedId ->
            val language = when (checkedId) {
                R.id.langKo -> "ko"
                R.id.langEn -> "en"
                else -> ""
            }
            if (language != Prefs.language) Prefs.language = language
        }

        b.editProfile.setOnClickListener {
            Dialogs.showProfile(activity, data.profile, data.myName) { name, school, color ->
                ProfileRepository.updateProfile(name, school, color, activity.failureToast())
            }
        }
        b.handleRow.setOnClickListener {
            val profile = data.profile ?: return@setOnClickListener
            if (profile.tag.isEmpty()) return@setOnClickListener
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("MODO", profile.handle))
            activity.toast(R.string.me_tag_copied)
        }
        b.availabilityCard.setOnClickListener { AvailabilityActivity.startForProfile(activity) }
        b.availabilityCard.addPressEffect()

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
        val profile = data.profile
        val name = data.myName
        val color = Labels.avatarColor(activity, profile?.avatarColor ?: -1, name)
        b.avatar.text = name.take(1).ifEmpty { "?" }
        b.avatar.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x40))
        b.avatar.setTextColor(color)
        b.name.text = name
        b.handle.text = if (profile != null && profile.tag.isNotEmpty()) profile.handle else activity.getString(R.string.me_tag_pending)
        b.email.text = profile?.email?.ifBlank { null } ?: AuthService.currentEmail
        b.school.text = profile?.school?.ifBlank { null } ?: activity.getString(R.string.me_school_none)
        val week = profile?.availability
        b.availabilitySummary.text = if (week == null || week.isEmpty) activity.getString(R.string.me_availability_none)
        else {
            val hours = week.freeSlotCount() / 2.0
            val text = if (hours % 1.0 == 0.0) hours.toInt().toString() else hours.toString()
            activity.getString(R.string.me_availability_summary, text)
        }
    }

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
