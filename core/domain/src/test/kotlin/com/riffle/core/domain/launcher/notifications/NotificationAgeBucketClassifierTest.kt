package com.riffle.core.domain.launcher.notifications

import com.riffle.core.domain.launcher.apps.AppPackageName
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationAgeBucketClassifierTest {
    private val classifier = NotificationAgeBucketClassifier()

    @Test
    fun classifiesNotificationsPostedInTheLastFiveMinutesAsNow() {
        assertEquals(
            NotificationAgeBucket.NOW,
            classifier.bucketFor(notification(postedAtEpochMillis = 1_000L), nowEpochMillis = 60_000L),
        )
    }

    @Test
    fun classifiesNotificationsPostedInTheLastHourAsRecent() {
        assertEquals(
            NotificationAgeBucket.RECENT,
            classifier.bucketFor(notification(postedAtEpochMillis = 1_000L), nowEpochMillis = 10 * 60 * 1_000L),
        )
    }

    @Test
    fun classifiesNotificationsPostedInTheLastDayAsToday() {
        assertEquals(
            NotificationAgeBucket.TODAY,
            classifier.bucketFor(notification(postedAtEpochMillis = 1_000L), nowEpochMillis = 2 * 60 * 60 * 1_000L),
        )
    }

    @Test
    fun classifiesOlderNotificationsAsOlder() {
        assertEquals(
            NotificationAgeBucket.OLDER,
            classifier.bucketFor(
                notification(postedAtEpochMillis = 1_000L),
                nowEpochMillis = 2 * 24 * 60 * 60 * 1_000L,
            ),
        )
    }

    @Test
    fun treatsFutureTimestampsAsNow() {
        assertEquals(
            NotificationAgeBucket.NOW,
            classifier.bucketFor(notification(postedAtEpochMillis = 2_000L), nowEpochMillis = 1_000L),
        )
    }

    private fun notification(postedAtEpochMillis: Long): LauncherNotification =
        LauncherNotification(
            key = LauncherNotificationKey("notification"),
            packageName = AppPackageName("com.riffle.mail"),
            postedAtEpochMillis = postedAtEpochMillis,
        )
}
