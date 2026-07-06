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

    @Test
    fun blankSearchReturnsVisibleApps() {
        val apps =
            listOf(
                app(label = "Camera"),
                app(label = "Hidden", visibility = AppVisibility.HIDDEN),
                app(label = "Browser"),
            )

        val results = catalog.searchApps(apps = apps, query = " ")

        assertEquals(listOf("Browser", "Camera"), results.map { app -> app.label })
    }

    @Test
    fun searchMatchesLabelPackageAndActivity() {
        val apps =
            listOf(
                app(label = "Camera", packageName = "com.android.camera"),
                app(label = "Maps", packageName = "com.google.android.apps.maps"),
                app(label = "Dialler", activityName = ".PhoneActivity"),
            )

        assertEquals(
            listOf("Camera"),
            catalog.searchApps(apps = apps, query = "cam").map { app -> app.label },
        )
        assertEquals(
            listOf("Maps"),
            catalog.searchApps(apps = apps, query = "google").map { app -> app.label },
        )
        assertEquals(
            listOf("Dialler"),
            catalog.searchApps(apps = apps, query = "phone").map { app -> app.label },
        )
    }

    @Test
    fun searchNormalizesWhitespaceAndCase() {
        val apps =
            listOf(
                app(label = "Google Maps"),
                app(label = "Google Messages"),
            )

        assertEquals(
            listOf("Google Maps"),
            catalog.searchApps(apps = apps, query = "  GOOGLE   maps ").map { app -> app.label },
        )
    }

    @Test
    fun searchMatchesProfileTypeAndId() {
        val apps =
            listOf(
                app(label = "Camera", profile = AppProfile.personal()),
                app(label = "Docs", profile = AppProfile.work()),
                app(label = "Sheets", profile = AppProfile(AppProfileId("company"), AppProfileType.WORK)),
            )

        assertEquals(
            listOf("Docs", "Sheets"),
            catalog.searchApps(apps = apps, query = "work").map { app -> app.label },
        )
        assertEquals(
            listOf("Sheets"),
            catalog.searchApps(apps = apps, query = "company").map { app -> app.label },
        )
        assertEquals(
            listOf("Camera"),
            catalog.searchApps(apps = apps, query = "personal").map { app -> app.label },
        )
    }

    @Test
    fun searchMatchesMultipleQueryTokensAcrossAppIdentityFields() {
        val apps =
            listOf(
                app(label = "Maps", packageName = "com.google.android.apps.maps", profile = AppProfile.personal()),
                app(label = "Docs", packageName = "com.google.android.apps.docs", profile = AppProfile.work()),
                app(label = "Calendar", packageName = "com.android.calendar", profile = AppProfile.work()),
            )

        assertEquals(
            listOf("Maps"),
            catalog.searchApps(apps = apps, query = "google maps").map { app -> app.label },
        )
        assertEquals(
            listOf("Docs"),
            catalog.searchApps(apps = apps, query = "google work").map { app -> app.label },
        )
        assertEquals(
            listOf("Calendar"),
            catalog.searchApps(apps = apps, query = "calendar work").map { app -> app.label },
        )
    }

    @Test
    fun searchMatchesShortcutLabelsAndIds() {
        val camera = app(label = "Camera")
        val browser = app(label = "Browser")
        val shortcutsByApp =
            mapOf(
                camera.identity to
                    listOf(
                        AppShortcut(
                            id = AppShortcutId("take-selfie"),
                            appIdentity = camera.identity,
                            shortLabel = "Selfie",
                            longLabel = "Take a selfie",
                        ),
                    ),
                browser.identity to
                    listOf(
                        AppShortcut(
                            id = AppShortcutId("new-tab"),
                            appIdentity = browser.identity,
                            shortLabel = "Tab",
                        ),
                    ),
            )

        assertEquals(
            listOf("Camera"),
            catalog.searchApps(apps = listOf(camera, browser), query = "selfie", shortcutsByApp = shortcutsByApp)
                .map { app -> app.label },
        )
        assertEquals(
            listOf("Browser"),
            catalog.searchApps(apps = listOf(camera, browser), query = "new-tab", shortcutsByApp = shortcutsByApp)
                .map { app -> app.label },
        )
    }

    @Test
    fun searchMatchesShortcutAcronyms() {
        val maps = app(label = "Maps")
        val camera = app(label = "Camera")
        val shortcutsByApp =
            mapOf(
                maps.identity to
                    listOf(
                        AppShortcut(
                            id = AppShortcutId("route-home"),
                            appIdentity = maps.identity,
                            shortLabel = "Route Home",
                        ),
                    ),
                camera.identity to
                    listOf(
                        AppShortcut(
                            id = AppShortcutId("take-selfie"),
                            appIdentity = camera.identity,
                            shortLabel = "Selfie",
                        ),
                    ),
            )

        assertEquals(
            listOf("Maps"),
            catalog.searchApps(apps = listOf(maps, camera), query = "rh", shortcutsByApp = shortcutsByApp)
                .map { app -> app.label },
        )
        assertEquals(
            listOf("Camera"),
            catalog.searchApps(apps = listOf(maps, camera), query = "ts", shortcutsByApp = shortcutsByApp)
                .map { app -> app.label },
        )
    }

    @Test
    fun searchRanksAppLabelMatchesBeforeShortcutAndIdentityMatches() {
        val shortcutMatch = app(label = "Alpha")
        val labelMatch = app(label = "Camera")
        val identityMatch = app(label = "Beta", packageName = "com.android.camera.tools")
        val shortcutsByApp =
            mapOf(
                shortcutMatch.identity to
                    listOf(
                        AppShortcut(
                            id = AppShortcutId("camera-scan"),
                            appIdentity = shortcutMatch.identity,
                            shortLabel = "Scan",
                        ),
                    ),
            )

        val results =
            catalog.searchApps(
                apps = listOf(shortcutMatch, identityMatch, labelMatch),
                query = "cam",
                shortcutsByApp = shortcutsByApp,
            )

        assertEquals(
            listOf("Camera", "Alpha", "Beta"),
            results.map { app -> app.label },
        )
    }

    @Test
    fun searchRanksLabelPrefixMatchesBeforeContainedLabelMatches() {
        val containedMatch = app(label = "Acme Camera")
        val prefixMatch = app(label = "Camera")

        val results = catalog.searchApps(apps = listOf(containedMatch, prefixMatch), query = "cam")

        assertEquals(
            listOf("Camera", "Acme Camera"),
            results.map { app -> app.label },
        )
    }

    @Test
    fun searchMatchesAppLabelAcronyms() {
        val apps =
            listOf(
                app(label = "Google Maps"),
                app(label = "Google Messages"),
                app(label = "Maps"),
            )

        assertEquals(
            listOf("Google Maps", "Google Messages"),
            catalog.searchApps(apps = apps, query = "gm").map { app -> app.label },
        )
    }

    @Test
    fun searchRanksLabelPrefixMatchesBeforeAcronymMatches() {
        val apps =
            listOf(
                app(label = "Google Maps"),
                app(label = "Gmail"),
            )

        assertEquals(
            listOf("Gmail", "Google Maps"),
            catalog.searchApps(apps = apps, query = "gm").map { app -> app.label },
        )
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
