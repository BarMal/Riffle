package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderAddAppFilterTest {
    @Test
    fun returnsAllAppsForBlankQuery() {
        val apps = listOf(app(label = "Camera"), app(label = "Calendar"))

        assertEquals(apps, apps.filterFolderAddCandidates("  "))
    }

    @Test
    fun filtersAppsByLabelWithoutChangingOrder() {
        val camera = app(label = "Camera")
        val calendar = app(label = "Calendar")
        val clock = app(label = "Clock")
        val apps = listOf(camera, calendar, clock)

        assertEquals(listOf(camera, calendar), apps.filterFolderAddCandidates("ca"))
    }

    @Test
    fun filtersAppsByPackageName() {
        val camera = app(label = "Camera", packageName = "com.android.camera")
        val calendar = app(label = "Calendar", packageName = "com.google.calendar")
        val apps = listOf(camera, calendar)

        assertEquals(listOf(calendar), apps.filterFolderAddCandidates("google"))
    }

    @Test
    fun filtersAppsByActivityName() {
        val settings = app(label = "Settings", activityName = ".HomeSettingsActivity")
        val camera = app(label = "Camera", activityName = ".CaptureActivity")
        val apps = listOf(settings, camera)

        assertEquals(listOf(settings), apps.filterFolderAddCandidates("home"))
    }

    private fun app(
        label: String,
        packageName: String = "com.riffle.${label.lowercase()}",
        activityName: String = ".MainActivity",
    ): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName(packageName),
                    activityName = AppActivityName(activityName),
                ),
            label = label,
        )
}
