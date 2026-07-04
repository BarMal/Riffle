package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppSearchContentFilter
import com.riffle.core.domain.launcher.apps.AppSearchFilters
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherSearchShortcutResultsTest {
    @Test
    fun searchShortcutResultsMatchLabelAcronyms() {
        val maps = app("Maps")
        val routeHome = shortcut(app = maps, id = "route-home", shortLabel = "Route Home")
        val state =
            stateWith(
                apps = listOf(maps),
                shortcuts = listOf(routeHome),
            )

        assertEquals(
            listOf(routeHome),
            state.searchShortcutResults(
                query = "rh",
                filters = shortcutSearchFilters,
            ),
        )
    }

    @Test
    fun searchShortcutResultsMatchLongLabelAcronyms() {
        val camera = app("Camera")
        val selfie =
            shortcut(
                app = camera,
                id = "selfie",
                shortLabel = "Selfie",
                longLabel = "Take Selfie",
            )
        val state =
            stateWith(
                apps = listOf(camera),
                shortcuts = listOf(selfie),
            )

        assertEquals(
            listOf(selfie),
            state.searchShortcutResults(
                query = "ts",
                filters = shortcutSearchFilters,
            ),
        )
    }

    private fun stateWith(
        apps: List<InstalledApp>,
        shortcuts: List<AppShortcut>,
    ): LauncherShellState =
        LauncherShellState(
            installedApps = apps,
            appShortcutsByApp = shortcuts.groupBy { shortcut -> shortcut.appIdentity },
        )

    private fun app(label: String): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.example.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = label,
        )

    private fun shortcut(
        app: InstalledApp,
        id: String,
        shortLabel: String,
        longLabel: String? = null,
    ): AppShortcut =
        AppShortcut(
            id = AppShortcutId(id),
            appIdentity = app.identity,
            shortLabel = shortLabel,
            longLabel = longLabel,
        )

    private companion object {
        val shortcutSearchFilters =
            AppSearchFilters(content = setOf(AppSearchContentFilter.SHORTCUTS))
    }
}
