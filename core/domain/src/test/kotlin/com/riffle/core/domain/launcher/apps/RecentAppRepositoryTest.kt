package com.riffle.core.domain.launcher.apps

import kotlin.test.Test
import kotlin.test.assertEquals

class RecentAppRepositoryTest {
    @Test
    fun usageRetainsPackageLevelRecencyWithoutAssumingAnActivityOrProfile() {
        val usage =
            RecentAppUsage(
                packageName = AppPackageName("com.riffle.calendar"),
                lastUsedAtMillis = 1_726_560_000_000,
            )

        assertEquals(AppPackageName("com.riffle.calendar"), usage.packageName)
        assertEquals(1_726_560_000_000, usage.lastUsedAtMillis)
    }
}
