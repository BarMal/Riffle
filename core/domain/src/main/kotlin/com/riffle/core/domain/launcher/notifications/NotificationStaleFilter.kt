package com.riffle.core.domain.launcher.notifications

class NotificationStaleFilter {
    fun activeForLauncherState(
        notifications: List<LauncherNotification>,
        nowEpochMillis: Long,
    ): List<LauncherNotification> =
        notifications.filter { notification ->
            !notification.canDismiss ||
                nowEpochMillis - notification.postedAtEpochMillis <= STALE_CLEARABLE_THRESHOLD_MILLIS
        }

    private companion object {
        const val STALE_CLEARABLE_THRESHOLD_MILLIS = 7 * 24 * 60 * 60 * 1_000L
    }
}
