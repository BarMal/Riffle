package com.riffle.core.domain.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppCatalog
import com.riffle.core.domain.launcher.notifications.AppNotificationGrouper
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.notifications.NotificationPriority
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerformanceBudgetSmokeTest {
    @Test
    fun appDrawerSearchStaysWithinBudgetForMidSizedCatalog() {
        val catalog = InstalledAppCatalog()
        val apps = syntheticApps(count = 250)

        repeat(WARMUP_RUNS) {
            catalog.searchApps(apps, query = "camera")
        }

        val elapsedMillis =
            measureMillis {
                repeat(MEASURED_RUNS) {
                    val results = catalog.searchApps(apps, query = "camera")

                    assertEquals("Camera 000", results.first().label)
                }
            } / MEASURED_RUNS

        assertTrue(
            actual = elapsedMillis < APP_DRAWER_SEARCH_BUDGET_MILLIS,
            message = "Expected app drawer search under ${APP_DRAWER_SEARCH_BUDGET_MILLIS}ms, was ${elapsedMillis}ms",
        )
    }

    @Test
    fun notificationGroupingStaysWithinBudgetForLargeActiveSet() {
        val grouper = AppNotificationGrouper()
        val notifications = syntheticNotifications(count = 500)

        repeat(WARMUP_RUNS) {
            grouper.groupByApp(notifications, nowEpochMillis = NOW_EPOCH_MILLIS)
        }

        val elapsedMillis =
            measureMillis {
                repeat(MEASURED_RUNS) {
                    val groups = grouper.groupByApp(notifications, nowEpochMillis = NOW_EPOCH_MILLIS)

                    assertEquals(100, groups.size)
                }
            } / MEASURED_RUNS

        assertTrue(
            actual = elapsedMillis < NOTIFICATION_GROUPING_BUDGET_MILLIS,
            message =
                "Expected notification grouping under ${NOTIFICATION_GROUPING_BUDGET_MILLIS}ms, " +
                    "was ${elapsedMillis}ms",
        )
    }

    private fun syntheticApps(count: Int): List<InstalledApp> =
        List(count) { index ->
            val paddedIndex = index.toString().padStart(3, '0')
            val label =
                when (index % 5) {
                    0 -> "Camera $paddedIndex"
                    1 -> "Calendar $paddedIndex"
                    2 -> "Messages $paddedIndex"
                    3 -> "Maps $paddedIndex"
                    else -> "Notes $paddedIndex"
                }

            InstalledApp(
                identity =
                    AppIdentity(
                        packageName = AppPackageName("com.example.app$paddedIndex"),
                        activityName = AppActivityName(".MainActivity$paddedIndex"),
                    ),
                label = label,
            )
        }

    private fun syntheticNotifications(count: Int): List<LauncherNotification> =
        List(count) { index ->
            val packageIndex = index % 100

            LauncherNotification(
                key = LauncherNotificationKey("notification-$index"),
                packageName = AppPackageName("com.example.app$packageIndex"),
                category = NotificationCategory.entries[index % NotificationCategory.entries.size],
                priority = NotificationPriority.entries[index % NotificationPriority.entries.size],
                canDismiss = index % 3 != 0,
                postedAtEpochMillis = NOW_EPOCH_MILLIS - index,
            )
        }

    private fun measureMillis(block: () -> Unit): Long = measureNanoTime(block) / NANOS_PER_MILLI

    private companion object {
        const val APP_DRAWER_SEARCH_BUDGET_MILLIS = 100L
        const val NOTIFICATION_GROUPING_BUDGET_MILLIS = 150L
        const val MEASURED_RUNS = 10
        const val WARMUP_RUNS = 5
        const val NANOS_PER_MILLI = 1_000_000L
        const val NOW_EPOCH_MILLIS = 1_900_000_000_000L
    }
}
