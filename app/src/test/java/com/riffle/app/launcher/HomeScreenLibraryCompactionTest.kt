package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockEditResult
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSettings
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScreenLibraryCompactionTest {
    @Test
    fun repacksAFullyPackedCompactLibraryInsteadOfRejectingTheMove() {
        val grid = GridDimensions(columns = 2, rows = 1)
        val apps = listOf(app("Alpha"), app("Bravo"), app("Charlie"), app("Delta"), app("Echo"))
        val layout =
            HomeLayoutDefaults.standard().copy(
                viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                dock = HomeLayoutDefaults.standard().dock.copy(position = DockPosition.BOTTOM),
                pages = listOf(LauncherPage(id = LauncherPageId("home"), grid = grid)),
                settings =
                    HomeLayoutDefaults.standard().settings.copy(
                        grid = GridSettings(dimensions = grid, compactLibraryPages = true),
                    ),
            ).withHomeScreenLibraryApps(apps)
        // Every page but the last is packed edge-to-edge -- exactly what makes the generic
        // column-preserving reflow reject a side-edge move (DockConfigurationEngineTest covers that).
        assertTrue(layout.pages.dropLast(1).all { page -> page.items.size == grid.columns })

        val result = layout.repackedForDockPosition(DockPosition.LEFT, apps)

        val updated = result as DockEditResult.Updated
        assertEquals(DockPosition.LEFT, updated.layout.dock.position)
        // A side dock leaves one column, so the library repacks to one app per page.
        assertEquals(apps.size, updated.layout.pages.size)
        assertTrue(updated.layout.pages.all { page -> page.items.size == 1 })
        assertEquals(
            apps.map { it.identity }.sortedBy { it.packageName.value },
            updated.layout.pages.flatMap { page -> page.items }
                .filterIsInstance<AppShortcutItem>()
                .map { item -> item.appIdentity }
                .sortedBy { it.packageName.value },
        )
    }

    @Test
    fun leavesNonCompactLibraryLayoutsToTheOrdinaryReflow() {
        val layout =
            HomeLayoutDefaults.standard().copy(
                viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                settings =
                    HomeLayoutDefaults.standard().settings.copy(
                        grid = HomeLayoutDefaults.standard().settings.grid.copy(compactLibraryPages = false),
                    ),
            )

        assertEquals(false, layout.usesCompactLibraryPacking)
    }

    private fun app(label: String): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = label,
        )
}
