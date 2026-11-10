package com.modo.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat

/** 기기에만 저장하는 앱 설정 (테마, 언어). 프로젝트 데이터는 Firestore에 저장. */
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

    /** "ko", "en" 또는 시스템 언어를 따르면 "" */
    var language: String
        get() = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')
        set(value) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(value))
        }
}
