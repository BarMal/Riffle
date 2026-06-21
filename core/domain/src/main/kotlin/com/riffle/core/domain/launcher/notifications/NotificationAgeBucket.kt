package com.riffle.core.domain.launcher.notifications

enum class NotificationAgeBucket {
    NOW,
    RECENT,
    TODAY,
    OLDER,
}

class NotificationAgeBucketClassifier {
    fun bucketFor(
        notification: LauncherNotification,
        nowEpochMillis: Long,
    ): NotificationAgeBucket {
        val ageMillis = (nowEpochMillis - notification.postedAtEpochMillis).coerceAtLeast(0L)

        return when {
            ageMillis < NOW_THRESHOLD_MILLIS -> NotificationAgeBucket.NOW
            ageMillis < RECENT_THRESHOLD_MILLIS -> NotificationAgeBucket.RECENT
            ageMillis < TODAY_THRESHOLD_MILLIS -> NotificationAgeBucket.TODAY
            else -> NotificationAgeBucket.OLDER
        }
    }

    private companion object {
        const val NOW_THRESHOLD_MILLIS = 5 * 60 * 1_000L
        const val RECENT_THRESHOLD_MILLIS = 60 * 60 * 1_000L
        const val TODAY_THRESHOLD_MILLIS = 24 * 60 * 60 * 1_000L
    }
}
