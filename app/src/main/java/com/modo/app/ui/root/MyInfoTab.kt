package com.modo.app.ui.root

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.modo.app.R
import com.modo.app.data.AppData
import com.modo.app.data.AuthService
import com.modo.app.data.Prefs
import com.modo.app.data.ProfileRepository
import com.modo.app.data.TeamRepository
import com.modo.app.databinding.TabMeBinding
import com.modo.app.logic.AlarmSoundKind
import com.modo.app.notify.AlarmPlayer
import com.modo.app.notify.AlarmScheduler
import com.modo.app.notify.AppNotifier
import com.modo.app.notify.Notifier
import com.modo.app.ui.auth.LoginActivity
import com.modo.app.ui.common.Dialogs
import com.modo.app.ui.common.Labels
import com.modo.app.ui.common.ProfilePhoto
import com.modo.app.ui.common.addPressEffect
import com.modo.app.ui.common.colorOf
import com.modo.app.ui.common.failureToast
import com.modo.app.ui.common.sampleLabels
import com.modo.app.ui.common.toast
import com.modo.app.ui.meeting.AvailabilityActivity

/** 루트 "내 정보" 탭: 프로필·태그, 가능 시간, 알림 설정, 테마·언어, 시연, 로그아웃, 앱 정보 */
class MyInfoTab(private val activity: RootActivity, private val b: TabMeBinding) {

    private var data = AppData()
    private val player = AlarmPlayer(activity)

    /** 여러 권한을 한 번에 요청. 결과는 "권한 이름 → 허용 여부" Map으로 옴 */
    private val permissionRequest = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val denied = result.filterValues { granted -> !granted }.keys
        when {
            denied.isEmpty() -> activity.toast(R.string.perm_all_granted)
            // 여러 번 거절해 허용 창이 더는 뜨지 않으면 기기 설정에서만 바꿀 수 있음
            denied.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) } -> askOpenSettings()
            else -> activity.toast(R.string.perm_some_denied)
        }
        renderSettings()
    }

    /** 카메라 앱을 띄워 작은 사진(Bitmap)을 바로 받아 옴 → 프로필 사진 */
    private val takePhoto = activity.registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            Prefs.profilePhoto = ProfilePhoto.encode(bitmap)
            renderPhoto()
        }
    }

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

        b.avatarPhoto.clipToOutline = true // 배경(원) 모양대로 사진을 잘라 둥글게 보이게
        b.avatarBox.setOnClickListener { showPhotoMenu() }
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

        b.alertKindsRow.setOnClickListener { pickAlertKinds() }
        b.digestTimeRow.setOnClickListener {
            Dialogs.pickTime(activity, Prefs.digestTime) { picked ->
                Prefs.digestTime = picked
                AlarmScheduler.scheduleDigest(activity)
                renderSettings()
            }
        }
        b.alarmSoundRow.setOnClickListener { pickAlarmSound() }
        b.permissionRow.setOnClickListener { requestPermissions() }
        b.doneFeedback.isChecked = Prefs.doneFeedback
        b.doneFeedback.setOnCheckedChangeListener { _, checked -> Prefs.doneFeedback = checked }

        b.sampleCard.setOnClickListener {
            TeamRepository.createSampleProjects(activity.sampleLabels(), activity.failureToast())
            activity.toast(R.string.msg_sample_created)
        }
        b.sampleCard.addPressEffect()

        b.logout.setOnClickListener {
            Dialogs.confirm(activity, R.string.confirm_logout, null, R.string.settings_logout) {
                AppNotifier.onSignedOut(activity) // 이 기기의 알림 · 알람을 지움
                Prefs.profilePhoto = ""
                AuthService.signOut(activity)
                activity.startActivity(
                    Intent(activity, LoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                activity.finish()
            }
        }
        b.version.text = activity.getString(R.string.about_version, versionName())
        renderSettings()
        renderPhoto()
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

    // ───────────────────────── 프로필 사진 (이 기기에만 저장) ─────────────────────────

    private fun renderPhoto() {
        val photo = ProfilePhoto.decode(Prefs.profilePhoto)
        b.avatarPhoto.setImageBitmap(photo)
        b.avatarPhoto.isVisible = photo != null
    }

    /** 목록에서 하나를 누르면 바로 실행되는 선택창 (setItems) */
    private fun showPhotoMenu() {
        val hasPhoto = Prefs.profilePhoto.isNotEmpty()
        val items = if (hasPhoto) {
            arrayOf(activity.getString(R.string.photo_take), activity.getString(R.string.photo_remove))
        } else {
            arrayOf(activity.getString(R.string.photo_take))
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.photo_title)
            .setItems(items) { _, which ->
                if (which == 0) openCamera() else {
                    Prefs.profilePhoto = ""
                    renderPhoto()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun openCamera() {
        try {
            takePhoto.launch(null)
        } catch (e: ActivityNotFoundException) {
            activity.toast(R.string.photo_no_camera)
        }
    }

    // ───────────────────────── 알림 설정 ─────────────────────────

    /** 기기 설정에서 권한을 바꾸고 돌아왔을 때도 불러서 표시를 맞춤 */
    fun renderSettings() {
        val on = Prefs.Alert.entries.filter { Prefs.alertOn(it) }
        b.alertKindsValue.text = when (on.size) {
            0 -> activity.getString(R.string.settings_alert_kinds_none)
            Prefs.Alert.entries.size -> activity.getString(R.string.settings_alert_kinds_all)
            else -> on.joinToString(" · ") { alertLabel(it) }
        }
        b.digestTimeValue.text = activity.getString(R.string.settings_digest_time_value, Notifier.formatTime(Prefs.digestTime))
        // 마감 알림을 꺼 두면 시각을 고를 필요가 없음
        val deadlineOn = Prefs.alertOn(Prefs.Alert.DEADLINE)
        b.digestTimeRow.isEnabled = deadlineOn
        b.digestTimeRow.alpha = if (deadlineOn) 1f else 0.4f
        b.alarmSoundValue.text = soundLabel(Prefs.alarmSound.kind)

        val notifOn = Notifier.canPost(activity)
        val micOn = isGranted(Manifest.permission.RECORD_AUDIO)
        b.permissionValue.text = activity.getString(
            R.string.perm_summary,
            activity.getString(if (notifOn) R.string.perm_on else R.string.perm_off),
            activity.getString(if (micOn) R.string.perm_on else R.string.perm_off),
        )
        b.permissionValue.setTextColor(activity.colorOf(if (notifOn) R.color.text_secondary else R.color.warning))
    }

    private fun alertLabel(alert: Prefs.Alert): String = activity.getString(
        when (alert) {
            Prefs.Alert.DEADLINE -> R.string.settings_alert_deadline
            Prefs.Alert.CHAT -> R.string.settings_alert_chat
            Prefs.Alert.ASSIGN -> R.string.settings_alert_assign
        }
    )

    private fun soundLabel(kind: AlarmSoundKind): String = activity.getString(
        when (kind) {
            AlarmSoundKind.SYSTEM -> R.string.alarm_sound_system
            AlarmSoundKind.MODO -> R.string.alarm_sound_modo
            AlarmSoundKind.SONG -> R.string.alarm_sound_song
        }
    )

    /** 받을 알림: 여러 개를 체크하는 선택창 */
    private fun pickAlertKinds() {
        val kinds = Prefs.Alert.entries
        val checked = BooleanArray(kinds.size) { Prefs.alertOn(kinds[it]) }
        Dialogs.chooseMany(activity, R.string.settings_alert_kinds, kinds.map { alertLabel(it) }, checked) { picked ->
            for ((index, kind) in kinds.withIndex()) Prefs.setAlert(kind, picked[index])
            AlarmScheduler.scheduleDigest(activity) // 마감 알림을 껐으면 예약도 취소됨
            renderSettings()
        }
    }

    /** 기본 알람음: 하나를 고르는 선택창. 누를 때마다 미리 들려주고 [확인]을 눌러야 저장 */
    private fun pickAlarmSound() {
        val kinds = listOf(AlarmSoundKind.SYSTEM, AlarmSoundKind.MODO)
        var picked = kinds.indexOf(Prefs.alarmSound.kind).coerceAtLeast(0)
        AlertDialog.Builder(activity)
            .setTitle(R.string.settings_alarm_sound)
            .setSingleChoiceItems(kinds.map { soundLabel(it) }.toTypedArray(), picked) { _, which ->
                picked = which
                player.play(kinds[which], "", loop = false)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                Prefs.alarmSound = Prefs.AlarmSound(kinds[picked])
                renderSettings()
            }
            .setOnDismissListener { player.stop() }
            .show()
    }

    // ───────────────────────── 앱 권한 ─────────────────────────

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    /** 아직 허용하지 않은 권한(알림 · 마이크)을 한 번에 요청 */
    private fun requestPermissions() {
        val wanted = mutableListOf(Manifest.permission.RECORD_AUDIO)
        // 알림 권한은 Android 13(API 33)에서 생김. 그 전에는 요청할 필요가 없음
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = wanted.filter { !isGranted(it) }
        when {
            missing.isNotEmpty() -> permissionRequest.launch(missing.toTypedArray())
            Notifier.canPost(activity) -> activity.toast(R.string.perm_all_granted)
            else -> askOpenSettings() // 권한은 있는데 기기 설정에서 앱 알림을 꺼 둔 경우
        }
    }

    private fun askOpenSettings() {
        AlertDialog.Builder(activity)
            .setTitle(R.string.settings_notif_off_title)
            .setMessage(R.string.settings_notif_off_body)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.settings_notif_open) { _, _ ->
                // 암시적 인텐트: 이 앱의 "앱 정보" 설정 화면을 열어 달라고 시스템에 요청
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null))
                activity.startActivity(intent)
            }
            .show()
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
