package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderEngine
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeDragPlan
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeShortcutEngine
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A drop on a full page that cannot push spills: the displaced app goes to the next page (HomeSpill). */
class SpillDropTest {
    private fun app(
        name: String,
        column: Int,
        row: Int,
    ) = AppShortcutItem(
        id = LauncherItemId(name),
        appIdentity = AppIdentity(AppPackageName("com.example.$name"), AppActivityName("Main")),
        label = name,
        placement = GridPlacement(GridCell(column, row)),
    )

    private val grid = GridDimensions(2, 2)
    private val layout =
        HomeLayoutDefaults.standard().let { base ->
            val a = LauncherPage(LauncherPageId("a"), grid = grid, items = listOf(app("x", 0, 0)))
            val b =
                LauncherPage(
                    LauncherPageId("b"),
                    grid = grid,
                    items = listOf(app("p", 0, 0), app("q", 1, 0), app("r", 0, 1), app("s", 1, 1)),
                )
            base.copy(pages = listOf(a, b), selectedPageId = a.id)
        }

    private val dragState =
        HomeItemDragState(
            pageId = LauncherPageId("a"),
            cell = GridCell(0, 0),
            cellSizePx = 100f,
            grid = grid,
            pageItems = layout.pages[0].items,
        )

    @Test
    fun aBlockedPlanReleasesAsASpillDropAtTheNamedCell() {
        val session =
            HomeDragSession(
                item = layout.pages[0].items.single(),
                originPageId = LauncherPageId("a"),
                originCell = GridCell(0, 0),
                targetPageId = LauncherPageId("b"),
                projectedCell = GridCell(1, 0),
                plan = HomeDragPlan(landing = null, spillLanding = GridCell(1, 0)),
            )
        val action = homeDropAction(session.item, dragState, session, 0f, 0f)
        val drop = action as LauncherShellAction.DropHomeItem
        assertTrue(drop.spill)
        assertEquals(GridCell(1, 0), drop.cell)
        assertEquals(LauncherPageId("b"), drop.targetPageId)
    }

    @Test
    fun aSpillDropTakesTheCellAndSendsTheDisplacedAppToANewPage() {
        val result =
            applyShortcutOrFolderDropEdit(
                action =
                    LauncherShellAction.DropHomeItem(
                        itemId = LauncherItemId("x"),
                        sourcePageId = LauncherPageId("a"),
                        targetPageId = LauncherPageId("b"),
                        cell = GridCell(1, 0),
                        spill = true,
                    ),
                layout = layout,
                shortcutEngine = HomeShortcutEngine(),
                folderEngine = FolderEngine(),
            ) as ShortcutOrFolderDropEditResult.Updated
        val pages = result.layout.pages
        assertEquals(3, pages.size)
        assertEquals(setOf("p", "r", "s", "x"), pages[1].items.map { it.id.value }.toSet())
        assertEquals(listOf("q"), pages[2].items.map { it.id.value })
        assertTrue(pages[0].items.isEmpty())
    }
}
