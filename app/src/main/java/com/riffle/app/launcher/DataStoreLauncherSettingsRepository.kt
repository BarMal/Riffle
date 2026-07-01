package com.riffle.app.launcher

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.launcherSettingsDataStore by preferencesDataStore(
    name = "riffle_launcher_settings",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, LauncherSettingsDataStoreKeys.PREFERENCES_NAME))
    },
)

class DataStoreLauncherSettingsRepository(context: Context) : LauncherSettingsRepository {
    private val dataStore = context.launcherSettingsDataStore

    override fun loadLauncherSettings(): LauncherSettings? =
        readString(LauncherSettingsDataStoreKeys.launcherSettings)
            ?.let { value -> runCatching { decodeLauncherSettings(value) }.getOrNull() }

    override fun saveLauncherSettings(settings: LauncherSettings) {
        writeString(
            key = LauncherSettingsDataStoreKeys.launcherSettings,
            value = encodeLauncherSettings(settings),
        )
    }

    private fun readString(key: Preferences.Key<String>): String? = runBlocking { dataStore.data.first()[key] }

    private fun writeString(
        key: Preferences.Key<String>,
        value: String,
    ) {
        runBlocking {
            dataStore.edit { preferences ->
                preferences[key] = value
            }
        }
    }
}

private object LauncherSettingsDataStoreKeys {
    const val PREFERENCES_NAME = "riffle_launcher_settings"
    val launcherSettings = stringPreferencesKey("launcher_settings")
}
