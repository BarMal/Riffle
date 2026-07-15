package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutUnavailableAppPrunerTest {
    @Test
    fun removesTrailingEmptyAllAppsPagesAfterPruningUnavailableApps() {
        val camera = app("Camera")
        val docs = app("Docs")
        val layout =
            HomeLayoutDefaults.standard().let { defaults ->
                defaults.copy(
                    viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                    pages =
                        listOf(
                            defaults.selectedPage.copy(items = listOf(shortcut(id = "camera", app = camera))),
                            LauncherPage(
                                id = LauncherPageId("library:1"),
                                type = LauncherPageType.AllApps,
                                grid = defaults.settings.grid.dimensions,
                                items = listOf(shortcut(id = "library-docs", app = docs)),
                            ),
                        ),
                    selectedPageId = LauncherPageId("library:1"),
                )
            }

        val prunedLayout = layout.keepingApps(setOf(camera))

        assertEquals(listOf(LauncherPageId("home")), prunedLayout.pages.map { page -> page.id })
        assertEquals(LauncherPageId("home"), prunedLayout.selectedPageId)
    }

    @Test
    fun keepsTrailingEmptyHomePagesInLibraryModeAfterPruningUnavailableApps() {
        val camera = app("Camera")
        val layout =
            HomeLayoutDefaults.standard().let { defaults ->
                defaults.copy(
                    viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                    pages =
                        listOf(
                            defaults.selectedPage.copy(items = listOf(shortcut(id = "camera", app = camera))),
                            LauncherPage(
                                id = LauncherPageId("spare"),
                                grid = defaults.settings.grid.dimensions,
                            ),
                        ),
                    selectedPageId = LauncherPageId("spare"),
                )
            }

        val prunedLayout = layout.keepingApps(setOf(camera))

        assertEquals(
            listOf(LauncherPageId("home"), LauncherPageId("spare")),
            prunedLayout.pages.map { page -> page.id },
        )
        assertEquals(LauncherPageId("spare"), prunedLayout.selectedPageId)
    }

    @Test
    fun confirmedRemovalPrunesOnlyTheMatchingPackageAndProfile() {
        val personalCamera = app("Camera")
        val workCamera = app("Camera", profile = AppProfile.work())
        val layout =
            HomeLayoutDefaults.standard().let { defaults ->
                defaults.copy(
                    pages =
                        listOf(
                            defaults.selectedPage.copy(
                                items =
                                    listOf(
                                        shortcut(id = "personal-camera", app = personalCamera),
                                        shortcut(id = "work-camera", app = workCamera),
                                    ),
                            ),
                        ),
                )
            }

        val pruned =
            LauncherShellState(homeLayout = layout).withoutConfirmedPackage(
                packageName = personalCamera.packageName,
                profile = personalCamera.profile,
                homeLayoutRepository = InMemoryHomeLayoutRepository(),
            )

        val shortcuts = pruned.homeLayout.selectedPage.items.filterIsInstance<AppShortcutItem>()
        assertEquals(listOf(workCamera), shortcuts.map { shortcut -> shortcut.appIdentity })
    }

    private fun app(
        label: String,
        profile: AppProfile = AppProfile.personal(),
    ): AppIdentity =
        AppIdentity(
            packageName = AppPackageName("com.riffle.${label.lowercase()}"),
            activityName = AppActivityName(".MainActivity"),
            profile = profile,
        )

    private fun shortcut(
        id: String,
        app: AppIdentity,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(id),
            appIdentity = app,
            label = app.packageName.value,
            placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
        )

    private class InMemoryHomeLayoutRepository : HomeLayoutRepository {
        override fun loadHomeLayout() = null

        override fun saveHomeLayout(layout: com.riffle.core.domain.launcher.home.HomeLayout) = Unit
    }
}
