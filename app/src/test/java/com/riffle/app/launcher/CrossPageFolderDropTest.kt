package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderEngine
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeShortcutEngine
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A drop on top of an item of another page makes the folder there, like a drop on the item's own page. */
class CrossPageFolderDropTest {
    private fun app(
        name: String,
        column: Int,
        row: Int,
    ) = AppShortcutItem(
        id = LauncherItemId(name),
        appIdentity = AppIdentity(AppPackageName("pkg.$name"), AppActivityName("Main")),
        label = name,
        placement = GridPlacement(GridCell(column, row)),
    )

    private val grid = GridDimensions(4, 2)
    private val layout =
        HomeLayoutDefaults.standard().let { base ->
            val a = LauncherPage(LauncherPageId("a"), grid = grid, items = listOf(app("x", 0, 0)))
            val b = LauncherPage(LauncherPageId("b"), grid = grid, items = listOf(app("t", 1, 0)))
            base.copy(pages = listOf(a, b), selectedPageId = a.id)
        }

    private fun drop(label: String?) =
        applyShortcutOrFolderDropEdit(
            action =
                LauncherShellAction.DropHomeItem(
                    itemId = LauncherItemId("x"),
                    sourcePageId = LauncherPageId("a"),
                    targetPageId = LauncherPageId("b"),
                    cell = GridCell(1, 0),
                    dropOnItemId = LauncherItemId("t"),
                    folderLabel = label,
                ),
            layout = layout,
            shortcutEngine = HomeShortcutEngine(),
            folderEngine = FolderEngine(),
        )

    @Test
    fun aDropOnAnItemOfAnotherPageMakesAFolderOfTheTwoThere() {
        val updated = drop("Dossier") as ShortcutOrFolderDropEditResult.Updated
        val pageB = updated.layout.pages.first { it.id == LauncherPageId("b") }
        val folder = pageB.items.filterIsInstance<FolderItem>().single()
        assertEquals("Dossier", folder.label)
        assertEquals(setOf("t", "x"), folder.items.map { it.id.value }.toSet())
        assertTrue(updated.layout.pages.first { it.id == LauncherPageId("a") }.items.isEmpty())
    }
}
