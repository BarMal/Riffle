// Existing blocking DataStore access, predating the runBlocking ban (#1198); convert to suspend/write-behind.
@file:Suppress("ForbiddenImport")

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
        decodeStoredLauncherSettings(readString(LauncherSettingsDataStoreKeys.launcherSettings))

    override fun saveLauncherSettings(settings: LauncherSettings) {
        preserveNewerBuildBlob()
        writeString(
            key = LauncherSettingsDataStoreKeys.launcherSettings,
            value = encodeLauncherSettings(settings),
        )
    }

    /**
     * A blob written by a newer build is decoded as far as this build understands it, and then this build's save
     * would replace it with a version it can read. Keep the newer copy under its own key first (once), so a downgrade
     * and a later upgrade never lose what the newer build stored.
     */
    private fun preserveNewerBuildBlob() {
        val stored = readString(LauncherSettingsDataStoreKeys.launcherSettings)
        if (isNewerThanThisBuild(stored) && readString(LauncherSettingsDataStoreKeys.newerBuildCopy) == null) {
            writeString(LauncherSettingsDataStoreKeys.newerBuildCopy, requireNotNull(stored))
        }
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
    val newerBuildCopy = stringPreferencesKey("launcher_settings_newer_build_copy")
}

internal fun decodeStoredLauncherSettings(value: String?): LauncherSettings? =
    value?.let { encoded -> runCatching { decodeLauncherSettings(encoded) }.getOrNull() }
