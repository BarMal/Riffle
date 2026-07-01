package com.riffle.app.launcher.notifications

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.activeNotificationDataStore by preferencesDataStore(
    name = "riffle_active_notifications",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, ActiveNotificationDataStoreKeys.PREFERENCES_NAME))
    },
)

class DataStoreActiveNotificationRepository(context: Context) :
    LauncherNotificationRepository,
    ActiveNotificationChangeSource {
    private val dataStore = context.activeNotificationDataStore
    private val observeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun activeNotifications(): List<LauncherNotification> =
        readString(ActiveNotificationDataStoreKeys.activeNotifications)
            ?.let { value -> runCatching { decodeActiveNotifications(value) }.getOrDefault(emptyList()) }
            .orEmpty()

    fun saveActiveNotifications(notifications: List<LauncherNotification>) {
        writeString(
            key = ActiveNotificationDataStoreKeys.activeNotifications,
            value = encodeActiveNotifications(notifications),
        )
    }

    override fun observeActiveNotifications(onChanged: () -> Unit) {
        observeScope.launch {
            dataStore.data
                .map { preferences -> preferences[ActiveNotificationDataStoreKeys.activeNotifications] }
                .distinctUntilChanged()
                .drop(1)
                .collect { onChanged() }
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

private object ActiveNotificationDataStoreKeys {
    const val PREFERENCES_NAME = "riffle_active_notifications"
    val activeNotifications = stringPreferencesKey("active_notifications")
}
