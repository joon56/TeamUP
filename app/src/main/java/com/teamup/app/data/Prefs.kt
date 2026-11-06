package com.teamup.app.data

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
        prefs = context.getSharedPreferences("teamup_prefs", Context.MODE_PRIVATE)
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

    /** "ko", "en" 또는 시스템 언어를 따르면 "" */
    var language: String
        get() = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')
        set(value) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(value))
        }
}
