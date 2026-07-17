package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.AppSearchContentFilter
import com.riffle.core.domain.launcher.apps.AppSearchFilters
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppCatalog
import com.riffle.core.domain.launcher.search.LauncherSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherAppListActionReducerTest {
    private val reducer = LauncherAppListActionReducer(InstalledAppCatalog())

    @Test
    fun updatesDrawerQueryAndFilteredApps() {
        val state =
            LauncherShellState(
                installedApps =
                    listOf(
                        app(label = "Camera"),
                        app(label = "Calendar"),
                        app(label = "Maps"),
                    ),
            )

        val updated = reducer.reduce(state, LauncherShellAction.AppDrawerQueryChanged("cam"))

        assertEquals("cam", updated?.appDrawerQuery)
        assertEquals(listOf("Camera"), updated?.appDrawerApps?.map { app -> app.label })
        assertEquals("", updated?.searchQuery)
    }

    @Test
    fun updatesDrawerProfileFilterForCurrentQuery() {
        val state =
            LauncherShellState(
                installedApps =
                    listOf(
                        app(label = "Calendar", profile = AppProfile.personal()),
                        app(label = "Calendar", profile = AppProfile.work()),
                        app(label = "Camera", profile = AppProfile.work()),
                    ),
                appDrawerQuery = "cal",
            )

        val updated =
            reducer.reduce(
                state,
                LauncherShellAction.AppDrawerProfileFilterSelected(AppDrawerProfileFilter.WORK),
            )

        assertEquals(AppDrawerProfileFilter.WORK, updated?.appDrawerProfileFilter)
        assertEquals(listOf("Calendar"), updated?.appDrawerApps?.map { app -> app.label })
        assertEquals(listOf(AppProfile.work()), updated?.appDrawerApps?.map { app -> app.identity.profile })
    }

    @Test
    fun defaultSearchUsesAllAvailableProfiles() {
        val personalCamera = app(label = "Camera", profile = AppProfile.personal())
        val workCamera = app(label = "Camera", profile = AppProfile.work())
        val state =
            LauncherShellState(
                installedApps = listOf(personalCamera, workCamera),
            )

        val updated = reducer.reduce(state, LauncherShellAction.SearchQueryChanged("cam"))

        assertEquals("cam", updated?.searchQuery)
        assertEquals(
            listOf(personalCamera.identity, workCamera.identity),
            updated?.searchResults?.map { app -> app.identity },
        )
    }

    @Test
    fun searchQueryIncludesMatchingSettingsResults() {
        val appDrawer = app(label = "App drawer")
        val state =
            LauncherShellState(
                installedApps = listOf(appDrawer),
            )

        val updated = reducer.reduce(state, LauncherShellAction.SearchQueryChanged("drawer"))

        assertEquals("drawer", updated?.searchQuery)
        assertEquals(listOf("App drawer"), updated?.searchResults?.map { app -> app.label })
        assertEquals(listOf("App drawer"), updated?.searchSettingsResults?.map { result -> result.title })
    }

    @Test
    fun blankSearchQueryClearsSettingsResults() {
        val state =
            LauncherShellState(
                searchQuery = "app",
                searchSettingsResults =
                    settingsLauncherSearchEntries()
                        .map { entry -> LauncherSearchResult.Setting(entry) },
            )

        val updated = reducer.reduce(state, LauncherShellAction.SearchQueryChanged(""))

        assertEquals("", updated?.searchQuery)
        assertEquals(emptyList<String>(), updated?.searchSettingsResults?.map { result -> result.title })
    }

    @Test
    fun defaultSearchIncludesShortcutLabels() {
        val camera = app(label = "Camera")
        val browser = app(label = "Browser")
        val state =
            LauncherShellState(
                installedApps = listOf(camera, browser),
                appShortcutsByApp =
                    mapOf(
                        camera.identity to listOf(shortcut(app = camera, label = "Selfie")),
                        browser.identity to listOf(shortcut(app = browser, label = "New tab")),
                    ),
            )

        val updated = reducer.reduce(state, LauncherShellAction.SearchQueryChanged("new tab"))

        assertEquals("new tab", updated?.searchQuery)
        assertEquals(emptyList<String>(), updated?.searchResults?.map { app -> app.label })
        assertEquals(listOf("New tab"), updated?.searchShortcutResults?.map { shortcut -> shortcut.shortLabel })
    }

    @Test
    fun togglingShortcutFilterExcludesShortcutLabels() {
        val browser = app(label = "Browser")
        val state =
            LauncherShellState(
                installedApps = listOf(browser),
                searchQuery = "new tab",
                appShortcutsByApp =
                    mapOf(
                        browser.identity to listOf(shortcut(app = browser, label = "New tab")),
                    ),
                searchResults = listOf(browser),
            )

        val updated =
            reducer.reduce(
                state,
                LauncherShellAction.ToggleSearchContentFilter(AppSearchContentFilter.SHORTCUTS),
            )

        assertEquals(
            setOf(AppSearchContentFilter.APPS),
            updated?.searchFilters?.content,
        )
        assertEquals(emptyList<String>(), updated?.searchResults?.map { app -> app.label })
        assertEquals(emptyList<String>(), updated?.searchShortcutResults?.map { shortcut -> shortcut.shortLabel })
    }

    @Test
    fun togglingAppsFilterCanSearchShortcutsOnly() {
        val browser = app(label = "Browser")
        val camera = app(label = "Camera")
        val state =
            LauncherShellState(
                installedApps = listOf(browser, camera),
                searchQuery = "new tab",
                searchFilters =
                    AppSearchFilters(
                        content = setOf(AppSearchContentFilter.APPS, AppSearchContentFilter.SHORTCUTS),
                    ),
                appShortcutsByApp =
                    mapOf(
                        browser.identity to listOf(shortcut(app = browser, label = "New tab")),
                    ),
            )

        val updated =
            reducer.reduce(
                state,
                LauncherShellAction.ToggleSearchContentFilter(AppSearchContentFilter.APPS),
            )

        assertEquals(setOf(AppSearchContentFilter.SHORTCUTS), updated?.searchFilters?.content)
        assertEquals(emptyList<String>(), updated?.searchResults?.map { app -> app.label })
        assertEquals(listOf("New tab"), updated?.searchShortcutResults?.map { shortcut -> shortcut.shortLabel })
    }

    @Test
    fun searchShortcutResultsRespectProfileFilters() {
        val personalDocs = app(label = "Docs", profile = AppProfile.personal())
        val workDocs = app(label = "Docs", profile = AppProfile.work())
        val state =
            LauncherShellState(
                installedApps = listOf(personalDocs, workDocs),
                searchQuery = "scan",
                searchFilters =
                    AppSearchFilters(
                        content = setOf(AppSearchContentFilter.APPS, AppSearchContentFilter.SHORTCUTS),
                        profiles = setOf(AppProfileType.WORK),
                    ),
                appShortcutsByApp =
                    mapOf(
                        personalDocs.identity to listOf(shortcut(app = personalDocs, label = "Scan")),
                        workDocs.identity to listOf(shortcut(app = workDocs, label = "Scan")),
                    ),
            )

        val updated = reducer.reduce(state, LauncherShellAction.SearchQueryChanged("scan"))

        assertEquals(
            listOf(workDocs.identity),
            updated?.searchShortcutResults?.map { shortcut -> shortcut.appIdentity },
        )
    }

    @Test
    fun updatesSearchProfileFilterForCurrentQuery() {
        val state =
            LauncherShellState(
                installedApps =
                    listOf(
                        app(label = "Calendar", profile = AppProfile.personal()),
                        app(label = "Calendar", profile = AppProfile.work()),
                        app(label = "Camera", profile = AppProfile.work()),
                    ),
                searchQuery = "cal",
            )

        val updated =
            reducer.reduce(
                state,
                LauncherShellAction.SearchProfileFilterSelected(AppDrawerProfileFilter.WORK),
            )

        assertEquals(AppDrawerProfileFilter.WORK, updated?.searchProfileFilter)
        assertEquals(setOf(AppProfileType.WORK), updated?.searchFilters?.profiles)
        assertEquals(listOf("Calendar"), updated?.searchResults?.map { app -> app.label })
        assertEquals(listOf(AppProfile.work()), updated?.searchResults?.map { app -> app.identity.profile })
    }

    @Test
    fun togglesSearchProfileFilters() {
        val personalCalendar = app(label = "Calendar", profile = AppProfile.personal())
        val workCalendar = app(label = "Calendar", profile = AppProfile.work())
        val state =
            LauncherShellState(
                installedApps = listOf(personalCalendar, workCalendar),
                searchQuery = "cal",
                searchResults = listOf(personalCalendar),
            )

        val updated = reducer.reduce(state, LauncherShellAction.ToggleSearchProfileFilter(AppProfileType.WORK))

        assertEquals(
            setOf(AppProfileType.PERSONAL, AppProfileType.PRIVATE),
            updated?.searchFilters?.profiles,
        )
        assertEquals(
            listOf(personalCalendar.identity),
            updated?.searchResults?.map { app -> app.identity },
        )
    }

    @Test
    fun resetsSearchFiltersForCurrentQuery() {
        val personalCalendar = app(label = "Calendar", profile = AppProfile.personal())
        val workCalendar = app(label = "Calendar", profile = AppProfile.work())
        val state =
            LauncherShellState(
                installedApps = listOf(personalCalendar, workCalendar),
                searchQuery = "cal",
                searchFilters =
                    AppSearchFilters(
                        content = setOf(AppSearchContentFilter.APPS, AppSearchContentFilter.SHORTCUTS),
                        profiles = setOf(AppProfileType.WORK),
                    ),
            )

        val updated = reducer.reduce(state, LauncherShellAction.ResetSearchFilters)

        assertEquals(AppSearchFilters(), updated?.searchFilters)
        assertEquals("cal", updated?.searchQuery)
        assertEquals(
            listOf(personalCalendar.identity, workCalendar.identity),
            updated?.searchResults?.map { app -> app.identity },
        )
        assertEquals(emptyList<AppShortcut>(), updated?.searchShortcutResults)
    }

    @Test
    fun filteredAppsKeepsUnavailableDrawerProfileFilterSelected() {
        val camera = app(label = "Camera", profile = AppProfile.personal())
        val state =
            LauncherShellState(
                installedApps = listOf(camera),
                appDrawerProfileFilter = AppDrawerProfileFilter.WORK,
                searchProfileFilter = AppDrawerProfileFilter.PRIVATE,
            )

        val updated = state.withFilteredApps(InstalledAppCatalog())

        assertEquals(AppDrawerProfileFilter.WORK, updated.appDrawerProfileFilter)
        assertEquals(AppDrawerProfileFilter.ALL, updated.searchProfileFilter)
        assertEquals(emptyList<InstalledApp>(), updated.appDrawerApps)
        assertEquals(listOf(camera), updated.searchResults)
    }

    @Test
    fun filteredAppsCoercesUnavailableSearchProfileFilters() {
        val camera = app(label = "Camera", profile = AppProfile.personal())
        val state =
            LauncherShellState(
                installedApps = listOf(camera),
                searchQuery = "cam",
                searchFilters = AppSearchFilters(profiles = setOf(AppProfileType.WORK)),
            )

        val updated = state.withFilteredApps(InstalledAppCatalog())

        assertEquals(setOf(AppProfileType.PERSONAL), updated.searchFilters.profiles)
        assertEquals(listOf(camera.identity), updated.searchResults.map { app -> app.identity })
    }

    @Test
    fun filteredAppsFallsBackToAvailableSearchProfilesWhenPersonalIsUnavailable() {
        val vault = app(label = "Vault", profile = AppProfile.private())
        val state =
            LauncherShellState(
                installedApps = listOf(vault),
                searchQuery = "vault",
                searchFilters = AppSearchFilters(profiles = setOf(AppProfileType.WORK)),
            )

        val updated = state.withFilteredApps(InstalledAppCatalog())

        assertEquals(setOf(AppProfileType.PRIVATE), updated.searchFilters.profiles)
        assertEquals(listOf(vault.identity), updated.searchResults.map { app -> app.identity })
    }

    @Test
    fun ignoresNonAppListActions() {
        assertNull(reducer.reduce(LauncherShellState(), LauncherShellAction.OpenSettings))
    }

    private fun app(
        label: String,
        profile: AppProfile = AppProfile.personal(),
    ): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                    profile = profile,
                ),
            label = label,
        )

    private fun shortcut(
        app: InstalledApp,
        label: String,
    ): AppShortcut =
        AppShortcut(
            id = AppShortcutId("${app.identity.packageName.value}:$label"),
            appIdentity = app.identity,
            shortLabel = label,
        )
}
