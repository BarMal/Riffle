package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Test

class HiddenAppsFilterTest {
    @Test
    fun filtersHiddenAppsByLabelPackageActivityAndProfile() {
        val camera = app(label = "Camera", packageName = "com.example.camera", activityName = ".CameraActivity")
        val workDocs =
            app(
                label = "Docs",
                packageName = "com.company.docs",
                activityName = ".DocsActivity",
                profile = AppProfile.work(),
            )

        assertEquals(
            listOf(camera),
            listOf(camera, workDocs).filteredHiddenApps(
                query = "camera",
                profileFilter = AppDrawerProfileFilter.ALL,
            ),
        )
        assertEquals(
            listOf(workDocs),
            listOf(camera, workDocs).filteredHiddenApps(
                query = "company",
                profileFilter = AppDrawerProfileFilter.ALL,
            ),
        )
        assertEquals(
            listOf(workDocs),
            listOf(camera, workDocs).filteredHiddenApps(
                query = "docs",
                profileFilter = AppDrawerProfileFilter.WORK,
            ),
        )
    }

    @Test
    fun describesHiddenAppEmptyStates() {
        assertEquals(
            "No hidden apps",
            hiddenAppsEmptyText(
                totalHiddenAppCount = 0,
                query = "",
                profileFilter = AppDrawerProfileFilter.ALL,
            ),
        )
        assertEquals(
            "No matching hidden apps",
            hiddenAppsEmptyText(
                totalHiddenAppCount = 2,
                query = "camera",
                profileFilter = AppDrawerProfileFilter.ALL,
            ),
        )
        assertEquals(
            "1 app matching, 2 apps hidden",
            hiddenAppsSummaryText(
                totalHiddenAppCount = 2,
                resultCount = 1,
                query = "camera",
                profileFilter = AppDrawerProfileFilter.ALL,
            ),
        )
        assertEquals(
            "1 app matching in work, 2 apps hidden",
            hiddenAppsSummaryText(
                totalHiddenAppCount = 2,
                resultCount = 1,
                query = "",
                profileFilter = AppDrawerProfileFilter.WORK,
            ),
        )
    }

    private fun app(
        label: String,
        packageName: String,
        activityName: String,
        profile: AppProfile = AppProfile.personal(),
    ): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName(packageName),
                    activityName = AppActivityName(activityName),
                    profile = profile,
                ),
            label = label,
            visibility = AppVisibility.HIDDEN,
        )
}
