package com.riffle.app.launcher.apps

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRefreshResult
import org.junit.Assert.assertEquals
import org.junit.Test

class PackageManagerInstalledAppRepositoryTest {
    @Test
    fun fallbackResultIsPartialWhenOnlyTheCallingProfileIsAvailable() {
        val personalApp =
            InstalledApp(
                identity =
                    AppIdentity(
                        packageName = AppPackageName("com.riffle.camera"),
                        activityName = AppActivityName(".MainActivity"),
                    ),
                label = "Camera",
            )

        val result = packageManagerFallbackRefreshResult(listOf(personalApp))

        assertEquals(InstalledAppRefreshResult.Partial(listOf(personalApp)), result)
    }
}
