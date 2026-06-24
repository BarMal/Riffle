package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
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

    private fun app(label: String): AppIdentity =
        AppIdentity(
            packageName = AppPackageName("com.riffle.${label.lowercase()}"),
            activityName = AppActivityName(".MainActivity"),
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
}
