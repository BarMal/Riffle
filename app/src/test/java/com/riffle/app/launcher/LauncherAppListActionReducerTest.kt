package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppSearchScope
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppCatalog
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
    fun updatesSearchResultsByShortcutLabel() {
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
        assertEquals(listOf("Browser"), updated?.searchResults?.map { app -> app.label })
    }

    @Test
    fun searchScopeCanExcludeShortcutLabels() {
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

        val updated = reducer.reduce(state, LauncherShellAction.SearchScopeSelected(AppSearchScope.APPS))

        assertEquals(AppSearchScope.APPS, updated?.searchScope)
        assertEquals(emptyList<String>(), updated?.searchResults?.map { app -> app.label })
    }

    @Test
    fun searchScopeCanIncludeShortcutLabelsAgain() {
        val browser = app(label = "Browser")
        val state =
            LauncherShellState(
                installedApps = listOf(browser),
                searchQuery = "new tab",
                searchScope = AppSearchScope.APPS,
                appShortcutsByApp =
                    mapOf(
                        browser.identity to listOf(shortcut(app = browser, label = "New tab")),
                    ),
            )

        val updated =
            reducer.reduce(
                state,
                LauncherShellAction.SearchScopeSelected(AppSearchScope.APPS_AND_SHORTCUTS),
            )

        assertEquals(AppSearchScope.APPS_AND_SHORTCUTS, updated?.searchScope)
        assertEquals(listOf("Browser"), updated?.searchResults?.map { app -> app.label })
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
        assertEquals(listOf("Calendar"), updated?.searchResults?.map { app -> app.label })
        assertEquals(listOf(AppProfile.work()), updated?.searchResults?.map { app -> app.identity.profile })
    }

    @Test
    fun filteredAppsCoercesUnavailableProfileFiltersToAll() {
        val camera = app(label = "Camera", profile = AppProfile.personal())
        val state =
            LauncherShellState(
                installedApps = listOf(camera),
                appDrawerProfileFilter = AppDrawerProfileFilter.WORK,
                searchProfileFilter = AppDrawerProfileFilter.PRIVATE,
            )

        val updated = state.withFilteredApps(InstalledAppCatalog())

        assertEquals(AppDrawerProfileFilter.ALL, updated.appDrawerProfileFilter)
        assertEquals(AppDrawerProfileFilter.ALL, updated.searchProfileFilter)
        assertEquals(listOf(camera), updated.appDrawerApps)
        assertEquals(listOf(camera), updated.searchResults)
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
