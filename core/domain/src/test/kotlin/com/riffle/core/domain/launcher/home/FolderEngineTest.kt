package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FolderEngineTest {
    private val engine = FolderEngine()

    @Test
    fun createsFolderFromSelectedPageShortcuts() {
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
            engine.createFolderOnSelectedPage(
                layout = layout,
                folderId = LauncherItemId("folder:tools"),
                label = "Tools",
                itemIds = listOf(camera.id, calendar.id),
            )

        val updated = assertIs<FolderEditResult.Updated>(result)
        val folder = assertIs<FolderItem>(updated.layout.selectedPage.items.single())
        assertEquals(LauncherItemId("folder:tools"), folder.id)
        assertEquals("Tools", folder.label)
        assertEquals(GridPlacement(cell = GridCell(column = 0, row = 0)), folder.placement)
        assertEquals(listOf(camera.appIdentity, calendar.appIdentity), folder.items.map { item -> item.appIdentity })
        assertEquals(listOf(null, null), folder.items.map { item -> item.placement })
    }

    @Test
    fun rejectsFolderWithFewerThanTwoDistinctItems() {
        val camera = appShortcut(id = "camera", placement = GridPlacement(cell = GridCell(column = 0, row = 0)))
        val result =
            engine.createFolderOnSelectedPage(
                layout = layoutWith(camera),
                folderId = LauncherItemId("folder:tools"),
                label = "Tools",
                itemIds = listOf(camera.id, camera.id),
            )

        val rejected = assertIs<FolderEditResult.Rejected>(result)
        assertEquals(FolderEditRejectionReason.NOT_ENOUGH_ITEMS, rejected.reason)
    }

    @Test
    fun rejectsMissingShortcut() {
        val camera = appShortcut(id = "camera", placement = GridPlacement(cell = GridCell(column = 0, row = 0)))
        val result =
            engine.createFolderOnSelectedPage(
                layout = layoutWith(camera),
                folderId = LauncherItemId("folder:tools"),
                label = "Tools",
                itemIds = listOf(camera.id, LauncherItemId("missing")),
            )

        val rejected = assertIs<FolderEditResult.Rejected>(result)
        assertEquals(FolderEditRejectionReason.ITEM_NOT_FOUND, rejected.reason)
    }

    @Test
    fun rejectsUnsupportedFolderMemberItem() {
        val camera = appShortcut(id = "camera", placement = GridPlacement(cell = GridCell(column = 0, row = 0)))
        val folder =
            FolderItem(
                id = LauncherItemId("folder:existing"),
                label = "Existing",
                items = listOf(camera.copy(placement = null)),
                placement = GridPlacement(cell = GridCell(column = 1, row = 0)),
            )

        val result =
            engine.createFolderOnSelectedPage(
                layout = layoutWith(camera, folder),
                folderId = LauncherItemId("folder:tools"),
                label = "Tools",
                itemIds = listOf(camera.id, folder.id),
            )

        val rejected = assertIs<FolderEditResult.Rejected>(result)
        assertEquals(FolderEditRejectionReason.UNSUPPORTED_ITEM, rejected.reason)
    }

    @Test
    fun renamesFolderOnSelectedPage() {
        val folder =
            FolderItem(
                id = LauncherItemId("folder:tools"),
                label = "Folder",
                items =
                    listOf(
                        appShortcut(id = "camera", placement = GridPlacement(cell = GridCell(column = 0, row = 0)))
                            .copy(placement = null),
                    ),
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val layout = layoutWith(folder)

        val result =
            engine.renameFolderOnSelectedPage(
                layout = layout,
                itemId = folder.id,
                label = " Tools ",
            )

        val updated = assertIs<FolderEditResult.Updated>(result)
        val renamedFolder = assertIs<FolderItem>(updated.layout.selectedPage.items.single())
        assertEquals("Tools", renamedFolder.label)
    }

    @Test
    fun rejectsBlankFolderName() {
        val folder =
            FolderItem(
                id = LauncherItemId("folder:tools"),
                label = "Folder",
                items =
                    listOf(
                        appShortcut(id = "camera", placement = GridPlacement(cell = GridCell(column = 0, row = 0)))
                            .copy(placement = null),
                    ),
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val result =
            engine.renameFolderOnSelectedPage(
                layout = layoutWith(folder),
                itemId = folder.id,
                label = " ",
            )

        val rejected = assertIs<FolderEditResult.Rejected>(result)
        assertEquals(FolderEditRejectionReason.INVALID_LABEL, rejected.reason)
    }

    private fun layoutWith(vararg items: LauncherItem): HomeLayout =
        HomeLayoutDefaults.standard().copy(
            pages = listOf(HomeLayoutDefaults.standard().selectedPage.copy(items = items.toList())),
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
