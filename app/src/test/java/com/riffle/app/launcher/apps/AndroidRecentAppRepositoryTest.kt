package com.riffle.app.launcher.apps

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.RecentAppUsage
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidRecentAppRepositoryTest {
    @Test
    fun mapsEligibleUsageRecordsInMostRecentlyUsedOrder() {
        val usages =
            listOf(
                PlatformRecentAppUsage(packageName = "com.riffle.old", lastUsedAtMillis = 100),
                PlatformRecentAppUsage(packageName = "com.riffle.new", lastUsedAtMillis = 300),
                PlatformRecentAppUsage(packageName = "com.riffle.middle", lastUsedAtMillis = 200),
            ).toRecentAppUsages()

        assertEquals(
            listOf(
                AppPackageName("com.riffle.new"),
                AppPackageName("com.riffle.middle"),
                AppPackageName("com.riffle.old"),
            ),
            usages.map { usage -> usage.packageName },
        )
    }

    @Test
    fun ordersEqualUsageTimestampsByPackageName() {
        val usages =
            listOf(
                PlatformRecentAppUsage(packageName = "com.riffle.zebra", lastUsedAtMillis = 100),
                PlatformRecentAppUsage(packageName = "com.riffle.alpha", lastUsedAtMillis = 100),
            ).toRecentAppUsages()

        assertEquals(
            listOf(
                AppPackageName("com.riffle.alpha"),
                AppPackageName("com.riffle.zebra"),
            ),
            usages.map { usage -> usage.packageName },
        )
    }

    @Test
    fun excludesBlankPackagesAndRecordsWithoutForegroundUse() {
        val usages =
            listOf(
                PlatformRecentAppUsage(packageName = "", lastUsedAtMillis = 100),
                PlatformRecentAppUsage(packageName = "com.riffle.never", lastUsedAtMillis = 0),
                PlatformRecentAppUsage(packageName = "com.riffle.valid", lastUsedAtMillis = 100),
            ).toRecentAppUsages()

        assertEquals(listOf(AppPackageName("com.riffle.valid")), usages.map { usage -> usage.packageName })
    }

    @Test
    fun fallsBackToNoRecentAppsWhenUsageAccessIsUnavailableOrDenied() {
        assertEquals(emptyList<RecentAppUsage>(), recentAppUsagesOrEmpty { emptyList() })
        assertEquals(
            emptyList<RecentAppUsage>(),
            recentAppUsagesOrEmpty { throw SecurityException("Usage Access denied") },
        )
    }
}
