package com.riffle.core.domain.launcher.notifications

import com.riffle.core.domain.launcher.apps.AppPackageName
import kotlin.test.Test
import kotlin.test.assertEquals

class AppNotificationCounterTest {
    @Test
    fun countsActiveNotificationsByPackage() {
        val counter = AppNotificationCounter()

        val counts =
            counter.countByPackage(
                listOf(
                    notification(key = "camera-1", packageName = "com.android.camera"),
                    notification(key = "camera-2", packageName = "com.android.camera"),
                    notification(key = "maps-1", packageName = "com.google.maps"),
                ),
            )

        assertEquals(2, counts[AppPackageName("com.android.camera")])
        assertEquals(1, counts[AppPackageName("com.google.maps")])
    }

    @Test
    fun returnsEmptyCountsForEmptyNotifications() {
        val counter = AppNotificationCounter()

        assertEquals(emptyMap<AppPackageName, Int>(), counter.countByPackage(emptyList()))
    }

    private fun notification(
        key: String,
        packageName: String,
    ): LauncherNotification =
        LauncherNotification(
            key = LauncherNotificationKey(key),
            packageName = AppPackageName(packageName),
            postedAtEpochMillis = 1_000L,
        )
}
