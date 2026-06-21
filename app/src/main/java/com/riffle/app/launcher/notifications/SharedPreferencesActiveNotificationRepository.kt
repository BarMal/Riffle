package com.riffle.app.launcher.notifications

import android.content.Context
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationRepository

class SharedPreferencesActiveNotificationRepository(context: Context) : LauncherNotificationRepository {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun activeNotifications(): List<LauncherNotification> =
        preferences.getString(KEY_ACTIVE_NOTIFICATIONS, null)
            ?.let { value -> runCatching { decodeActiveNotifications(value) }.getOrDefault(emptyList()) }
            .orEmpty()

    fun saveActiveNotifications(notifications: List<LauncherNotification>) {
        preferences.edit()
            .putString(KEY_ACTIVE_NOTIFICATIONS, encodeActiveNotifications(notifications))
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "riffle_active_notifications"
        const val KEY_ACTIVE_NOTIFICATIONS = "active_notifications"
    }
}
