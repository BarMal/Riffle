package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HomeShortcutEngineTest {
    private val engine = HomeShortcutEngine()

    @Test
    fun addsAppShortcutToSelectedPage() {
        val app = app(label = "Camera")

        val result = engine.addAppToSelectedPage(layout = HomeLayoutDefaults.standard(), app = app)

        val updated = assertIs<HomeShortcutResult.Updated>(result)
        val shortcut = updated.layout.selectedPage.items.single() as AppShortcutItem
        assertEquals(app.identity, shortcut.appIdentity)
        assertEquals("Camera", shortcut.label)
        assertEquals(GridPlacement(cell = GridCell(column = 0, row = 0)), shortcut.placement)
    }

    @Test
    fun rejectsWhenSelectedPageHasNoAvailableCells() {
        val fullPage =
            LauncherPage(
                id = LauncherPageId("full"),
                grid = GridDimensions(columns = 1, rows = 1),
                items =
                    listOf(
                        appShortcut(
                            id = "calendar",
                            placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
                        ),
                    ),
            )
        val layout =
            HomeLayoutDefaults.standard().copy(
                pages = listOf(fullPage),
                selectedPageId = fullPage.id,
            )

        val result = engine.addAppToSelectedPage(layout = layout, app = app(label = "Camera"))

        val rejected = assertIs<HomeShortcutResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.NO_AVAILABLE_CELL, rejected.reason)
    }

    @Test
    fun removesShortcutFromSelectedPage() {
        val shortcut =
            appShortcut(
                id = "camera",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val layout =
            HomeLayoutDefaults.standard().copy(
                pages = listOf(HomeLayoutDefaults.standard().selectedPage.copy(items = listOf(shortcut))),
            )

        val result = engine.removeShortcutFromSelectedPage(layout = layout, itemId = shortcut.id)

        val updated = assertIs<HomeShortcutResult.Updated>(result)
        assertEquals(emptyList(), updated.layout.selectedPage.items)
    }

    @Test
    fun rejectsRemovingMissingShortcut() {
        val result =
            engine.removeShortcutFromSelectedPage(
                layout = HomeLayoutDefaults.standard(),
                itemId = LauncherItemId("missing"),
            )

        val rejected = assertIs<HomeShortcutResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.ITEM_NOT_FOUND, rejected.reason)
    }

    @Test
    fun movesShortcutOnSelectedPage() {
        val shortcut =
            appShortcut(
                id = "camera",
                placement = GridPlacement(cell = GridCell(column = 1, row = 1)),
            )
        val layout = layoutWith(shortcut)

        val result =
            engine.moveShortcutOnSelectedPage(
                layout = layout,
                itemId = shortcut.id,
                direction = HomeShortcutMoveDirection.RIGHT,
            )

        val updated = assertIs<HomeShortcutResult.Updated>(result)
        assertEquals(
            GridPlacement(cell = GridCell(column = 2, row = 1)),
            updated.layout.selectedPage.items.single().placement,
        )
        assertEquals(GridPlacement(cell = GridCell(column = 1, row = 1)), shortcut.placement)
    }

    @Test
    fun rejectsMoveThatWouldLeaveSelectedPageGrid() {
        val shortcut =
            appShortcut(
                id = "camera",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val layout = layoutWith(shortcut)

        val result =
            engine.moveShortcutOnSelectedPage(
                layout = layout,
                itemId = shortcut.id,
                direction = HomeShortcutMoveDirection.LEFT,
            )

        val rejected = assertIs<HomeShortcutResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.OUT_OF_BOUNDS, rejected.reason)
    }

    @Test
    fun rejectsMoveThatWouldCollideWithAnotherShortcut() {
        val camera =
            appShortcut(
                id = "camera",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val calendar =
            appShortcut(
                id = "calendar",
                placement = GridPlacement(cell = GridCell(column = 1, row = 0)),
            )
        val layout = layoutWith(camera, calendar)

        val result =
            engine.moveShortcutOnSelectedPage(
                layout = layout,
                itemId = camera.id,
                direction = HomeShortcutMoveDirection.RIGHT,
            )

        val rejected = assertIs<HomeShortcutResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.COLLISION, rejected.reason)
    }

    @Test
    fun rejectsMovingMissingShortcut() {
        val result =
            engine.moveShortcutOnSelectedPage(
                layout = HomeLayoutDefaults.standard(),
                itemId = LauncherItemId("missing"),
                direction = HomeShortcutMoveDirection.RIGHT,
            )

        val rejected = assertIs<HomeShortcutResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.ITEM_NOT_FOUND, rejected.reason)
    }

    private fun layoutWith(vararg shortcuts: AppShortcutItem): HomeLayout =
        HomeLayoutDefaults.standard().copy(
            pages = listOf(HomeLayoutDefaults.standard().selectedPage.copy(items = shortcuts.toList())),
        )

    private fun app(label: String): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = label,
        )

    private fun appShortcut(
        id: String,
        placement: GridPlacement,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(id),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.$id"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = id,
            placement = placement,
        )
}
