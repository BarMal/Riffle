package com.riffle.core.domain.launcher.apps

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class InstalledAppCatalogTest {
    private val catalog = InstalledAppCatalog()

    @Test
    fun appIdentityIncludesProfile() {
        val personalCamera =
            AppIdentity(
                packageName = AppPackageName("com.android.camera"),
                activityName = AppActivityName(".CameraActivity"),
                profile = AppProfile.personal(),
            )
        val workCamera = personalCamera.copy(profile = AppProfile.work())

        assertNotEquals(personalCamera, workCamera)
    }

    @Test
    fun visibleAppsFiltersDisabledHiddenAndExcludedApps() {
        val apps =
            listOf(
                app(label = "Camera"),
                app(label = "Disabled", enabled = false),
                app(label = "Hidden", visibility = AppVisibility.HIDDEN),
                app(label = "Excluded", visibility = AppVisibility.EXCLUDED),
            )

        val visibleApps = catalog.visibleApps(apps)

        assertEquals(listOf("Camera"), visibleApps.map { app -> app.label })
    }

    @Test
    fun visibleAppsSortByLabelThenStableIdentity() {
        val apps =
            listOf(
                app(label = "camera", packageName = "com.android.camera.beta"),
                app(label = "Browser"),
                app(label = "Camera", packageName = "com.android.camera"),
            )

        val visibleApps = catalog.visibleApps(apps)

        assertEquals(
            listOf("Browser", "Camera", "camera"),
            visibleApps.map { app -> app.label },
        )
        assertEquals(
            listOf("com.android.browser", "com.android.camera", "com.android.camera.beta"),
            visibleApps.map { app -> app.identity.packageName.value },
        )
    }

    @Test
    fun appsForProfileReturnsVisibleAppsForOnlyThatProfile() {
        val apps =
            listOf(
                app(label = "Personal Camera", profile = AppProfile.personal()),
                app(label = "Work Camera", profile = AppProfile.work()),
                app(label = "Hidden Work Camera", profile = AppProfile.work(), visibility = AppVisibility.HIDDEN),
            )

        val workApps = catalog.appsForProfile(apps = apps, profile = AppProfile.work())

        assertEquals(listOf("Work Camera"), workApps.map { app -> app.label })
    }

    private fun app(
        label: String,
        packageName: String = "com.android.${label.lowercase().replace(" ", ".")}",
        activityName: String = ".MainActivity",
        profile: AppProfile = AppProfile.personal(),
        enabled: Boolean = true,
        visibility: AppVisibility = AppVisibility.VISIBLE,
    ): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName(packageName),
                    activityName = AppActivityName(activityName),
                    profile = profile,
                ),
            label = label,
            enabled = enabled,
            visibility = visibility,
        )
}
