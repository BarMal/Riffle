package com.riffle.app.launcher.notifications

import android.content.Context
import android.content.SharedPreferences
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationRepository

class SharedPreferencesActiveNotificationRepository(context: Context) : LauncherNotificationRepository {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun activeNotifications(): List<LauncherNotification> =
        preferences.getString(KEY_ACTIVE_NOTIFICATIONS, null)
            ?.let { value -> runCatching { decodeActiveNotifications(value) }.getOrDefault(emptyList()) }
            .orEmpty()

    fun saveActiveNotifications(notifications: List<LauncherNotification>) {
        preferences.edit()
            .putString(KEY_ACTIVE_NOTIFICATIONS, encodeActiveNotifications(notifications))
            .apply()
    }

    fun observeActiveNotifications(onChanged: () -> Unit) {
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_ACTIVE_NOTIFICATIONS) {
                onChanged()
            }
        }.also { listener ->
            listeners.add(listener)
            preferences.registerOnSharedPreferenceChangeListener(listener)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "riffle_active_notifications"
        const val KEY_ACTIVE_NOTIFICATIONS = "active_notifications"
    }
}
