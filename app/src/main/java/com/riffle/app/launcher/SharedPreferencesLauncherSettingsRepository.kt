package com.riffle.app.launcher

import android.content.Context
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository

class SharedPreferencesLauncherSettingsRepository(context: Context) : LauncherSettingsRepository {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun loadLauncherSettings(): LauncherSettings? =
        preferences.getString(KEY_LAUNCHER_SETTINGS, null)
            ?.let { value -> runCatching { decodeLauncherSettings(value) }.getOrNull() }

    override fun saveLauncherSettings(settings: LauncherSettings) {
        preferences.edit()
            .putString(KEY_LAUNCHER_SETTINGS, encodeLauncherSettings(settings))
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "riffle_launcher_settings"
        const val KEY_LAUNCHER_SETTINGS = "launcher_settings"
    }
}
