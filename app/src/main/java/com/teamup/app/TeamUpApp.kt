package com.teamup.app

import android.app.Application
import com.teamup.app.data.Prefs

class TeamUpApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Prefs.applyTheme()
    }
}
