package dev.ryuya.galaxydiag

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class GalaxyDiagApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val mode =
            RootRunner.prefs(this)
                .getInt("appearance_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(
            if (mode in listOf(-1, 1, 2)) mode else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )
    }
}
