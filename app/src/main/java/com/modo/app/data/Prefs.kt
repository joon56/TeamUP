package com.modo.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import com.modo.app.logic.AlarmSoundKind
import com.modo.app.logic.MeetingTimer
import java.time.LocalTime

/** 기기에만 저장하는 앱 설정 (테마, 언어, 알림, 알람). 프로젝트 데이터는 Firestore에 저장. */
object Prefs {

    enum class ThemeMode { DARK, LIGHT, SYSTEM }

    private const val KEY_THEME = "theme"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("modo_prefs", Context.MODE_PRIVATE)
    }

    var themeMode: ThemeMode
        get() = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.DARK
        set(value) {
            prefs.edit { putString(KEY_THEME, value.name) }
            applyTheme()
        }

    fun applyTheme() {
        val mode = when (themeMode) {
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    /** 가입 화면에서 받은 학교·아바타 색. 첫 프로필 문서를 만들 때 쓰고 지움 */
    data class PendingProfile(val school: String, val avatarColor: Int)

    var pendingProfile: PendingProfile?
        get() {
            if (!prefs.contains(KEY_PENDING_COLOR)) return null
            return PendingProfile(prefs.getString(KEY_PENDING_SCHOOL, "").orEmpty(), prefs.getInt(KEY_PENDING_COLOR, -1))
        }
        set(value) = prefs.edit {
            if (value == null) {
                remove(KEY_PENDING_SCHOOL)
                remove(KEY_PENDING_COLOR)
            } else {
                putString(KEY_PENDING_SCHOOL, value.school)
                putInt(KEY_PENDING_COLOR, value.avatarColor)
            }
        }

    /** 회의 찾기 화면에서 마지막으로 본 추천 1순위 (프로젝트별 MeetingSlot.key). 다르면 알림에 뜸 */
    fun meetingSeen(projectId: String): String? = prefs.getString(KEY_MEETING_SEEN + projectId, null)

    fun setMeetingSeen(projectId: String, key: String) = prefs.edit { putString(KEY_MEETING_SEEN + projectId, key) }

    /** AI 요약 전 개인정보 안내에 동의했는지 (처음 한 번만 묻기) */
    var aiNoticeAccepted: Boolean
        get() = prefs.getBoolean(KEY_AI_NOTICE, false)
        set(value) = prefs.edit { putBoolean(KEY_AI_NOTICE, value) }

    private const val KEY_AI_NOTICE = "ai_notice_accepted"
    private const val KEY_MEETING_SEEN = "meeting_seen_"
    private const val KEY_PENDING_SCHOOL = "pending_school"
    private const val KEY_PENDING_COLOR = "pending_color"

    // ───────────────────────── 알림 · 알람 설정 ─────────────────────────

    /** 받을 알림 종류: 마감 알림 · 채팅 메시지 · 업무 배정 */
    enum class Alert { DEADLINE, CHAT, ASSIGN }

    fun alertOn(alert: Alert): Boolean = prefs.getBoolean(KEY_ALERT + alert.name, true)

    fun setAlert(alert: Alert, on: Boolean) = prefs.edit { putBoolean(KEY_ALERT + alert.name, on) }

    /** 매일 마감 알림이 오는 시각 (기본 아침 8시) */
    var digestTime: LocalTime
        get() = LocalTime.of(prefs.getInt(KEY_DIGEST_HOUR, 8), prefs.getInt(KEY_DIGEST_MINUTE, 0))
        set(value) = prefs.edit {
            putInt(KEY_DIGEST_HOUR, value.hour)
            putInt(KEY_DIGEST_MINUTE, value.minute)
        }

    /** 새 알람의 기본 소리 */
    data class AlarmSound(val kind: AlarmSoundKind, val uri: String = "", val name: String = "")

    var alarmSound: AlarmSound
        get() = AlarmSound(
            AlarmSoundKind.entries.firstOrNull { it.name == prefs.getString(KEY_SOUND_KIND, null) } ?: AlarmSoundKind.SYSTEM,
            prefs.getString(KEY_SOUND_URI, "").orEmpty(),
            prefs.getString(KEY_SOUND_NAME, "").orEmpty(),
        )
        set(value) = prefs.edit {
            putString(KEY_SOUND_KIND, value.kind.name)
            putString(KEY_SOUND_URI, value.uri)
            putString(KEY_SOUND_NAME, value.name)
        }

    /** 업무를 완료로 체크할 때 효과음 · 진동 */
    var doneFeedback: Boolean
        get() = prefs.getBoolean(KEY_DONE_FEEDBACK, true)
        set(value) = prefs.edit { putBoolean(KEY_DONE_FEEDBACK, value) }

    /** 알림 권한을 이미 한 번 물어봤는지 */
    var notifPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_ASKED, false)
        set(value) = prefs.edit { putBoolean(KEY_NOTIF_ASKED, value) }

    /** 업무 알람 목록 (JSON). AlarmStore가 읽고 씀 */
    var alarmsJson: String
        get() = prefs.getString(KEY_ALARMS, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_ALARMS, value) }

    /** 내가 맡은 미완료 업무 스냅샷 (JSON). 앱이 꺼져 있어도 아침 마감 알림을 만들 수 있게 저장 */
    var digestJson: String
        get() = prefs.getString(KEY_DIGEST, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_DIGEST, value) }

    /** 프로필 사진 (작게 줄인 JPEG를 Base64로). 이 기기에만 저장 */
    var profilePhoto: String
        get() = prefs.getString(KEY_PHOTO, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_PHOTO, value) }

    /** 돌고 있는 회의 타이머. 없으면 null. 화면을 닫거나 앱이 꺼져도 이어지도록 기기에 저장 */
    var meetingTimer: MeetingTimer?
        get() {
            val endAt = prefs.getLong(KEY_TIMER_END, 0)
            return if (endAt <= 0) null else MeetingTimer(endAt, prefs.getLong(KEY_TIMER_TOTAL, 0))
        }
        set(value) = prefs.edit {
            if (value == null) {
                remove(KEY_TIMER_END)
                remove(KEY_TIMER_TOTAL)
            } else {
                putLong(KEY_TIMER_END, value.endAt)
                putLong(KEY_TIMER_TOTAL, value.totalMs)
            }
        }

    private const val KEY_ALERT = "alert_"
    private const val KEY_DIGEST_HOUR = "digest_hour"
    private const val KEY_DIGEST_MINUTE = "digest_minute"
    private const val KEY_SOUND_KIND = "alarm_sound_kind"
    private const val KEY_SOUND_URI = "alarm_sound_uri"
    private const val KEY_SOUND_NAME = "alarm_sound_name"
    private const val KEY_DONE_FEEDBACK = "done_feedback"
    private const val KEY_NOTIF_ASKED = "notif_asked"
    private const val KEY_ALARMS = "task_alarms"
    private const val KEY_DIGEST = "digest_tasks"
    private const val KEY_PHOTO = "profile_photo"
    private const val KEY_TIMER_END = "meeting_timer_end"
    private const val KEY_TIMER_TOTAL = "meeting_timer_total"

    /** "ko", "en" 또는 시스템 언어를 따르면 "" */
    var language: String
        get() = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')
        set(value) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(value))
        }
}
