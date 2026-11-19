package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeDragPushTest {
    private val grid = GridDimensions(columns = 4, rows = 3)
    private val tuning = HomeDragTuning()

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

    private fun folder(
        name: String,
        column: Int,
        row: Int,
    ) = FolderItem(LauncherItemId(name), name, emptyList(), GridPlacement(GridCell(column, row)))

    private fun widget(
        name: String,
        column: Int,
        row: Int,
        columns: Int,
        rows: Int,
    ) = WidgetItem(
        id = LauncherItemId(name),
        appWidgetId = HostedWidgetId(1),
        label = name,
        placement = GridPlacement(GridCell(column, row), GridSpan(columns, rows)),
    )

    private fun page(vararg items: LauncherItem) =
        LauncherPage(
            LauncherPageId("p"),
            grid = grid,
            items = items.toList(),
        )

    private fun plan(
        page: LauncherPage,
        dragged: LauncherItem,
        x: Float,
        y: Float,
        before: Set<LauncherItemId> = emptySet(),
    ) = HomeDragPush.plan(page, dragged, x, y, tuning, before)

    @Test
    fun overEmptySpaceTheItemLandsThereAndNothingMoves() {
        val dragged = app("a", 0, 0)
        val result = plan(page(dragged), dragged, 3.5f, 2.5f)
        assertEquals(HomeDragPlan(landing = GridCell(3, 2)), result)
    }

    @Test
    fun theDeadZoneAtAnAppsCentreMakesADropOnTopAndPushesNothing() {
        val dragged = app("a", 0, 0)
        val other = app("b", 2, 1)
        val result = plan(page(dragged, other), dragged, 2.5f + 0.29f, 1.5f)
        assertEquals(other.id, result.dropOn)
        assertEquals(GridCell(2, 1), result.landing)
        assertTrue(result.pushed.isEmpty())
    }

    @Test
    fun justOutsideTheDeadZoneButInsideTheRingTheAppIsPushedAway() {
        val dragged = app("a", 0, 0)
        val other = app("b", 2, 1)
        // 0.35 cells to the left of b's centre: outside the 0.3 dead zone, inside the 0.75 ring.
        val result = plan(page(dragged, other), dragged, 2.5f - 0.35f, 1.5f)
        assertNull(result.dropOn)
        assertEquals(GridCell(3, 1), result.pushed[other.id])
        assertEquals(GridCell(2, 1), result.landing)
    }

    @Test
    fun anAppOutsideTheRingIsLeftAlone() {
        val dragged = app("a", 0, 0)
        val other = app("b", 2, 1)
        val result = plan(page(dragged, other), dragged, 0.5f, 1.5f)
        assertTrue(result.pushed.isEmpty())
    }

    @Test
    fun theRingBoundaryIsHeldByHysteresis() {
        val dragged = app("a", 0, 0)
        val other = app("b", 2, 1)
        val x = 2.5f - 0.8f // just outside the 0.75 ring, inside ring plus hysteresis
        assertTrue(plan(page(dragged, other), dragged, x, 1.5f).pushed.isEmpty())
        assertEquals(GridCell(3, 1), plan(page(dragged, other), dragged, x, 1.5f, setOf(other.id)).pushed[other.id])
    }

    @Test
    fun pushingAwayFromThePointerPicksTheDominantAxisAndSkipsOverFixedNeighbours() {
        val dragged = app("a", 0, 0)
        val above = app("up", 1, 0)
        val fixedRight = app("fixed", 2, 0)
        // Pointer below "up": it goes up (off the page is refused), then sideways, skipping the fixed app.
        val result = plan(page(dragged, above, fixedRight), dragged, 1.5f, 0.5f + 0.7f)
        val moved = result.pushed[above.id]
        assertNotNull(moved)
        assertTrue(moved != GridCell(1, 0) && moved != GridCell(2, 0))
    }

    @Test
    fun theItemUnderTheLandingCellIsAlwaysPushedEvenOutsideTheRing() {
        val dragged = app("a", 0, 0)
        val widgetItem = widget("w", 0, 1, 2, 1)
        val tiny = HomeDragTuning(ringRadius = 0.5f)
        val result = HomeDragPush.plan(page(dragged, widgetItem), dragged, 0.99f, 1.01f, tiny)
        assertEquals(GridCell(0, 1), result.landing)
        assertEquals(GridCell(0, 2), result.pushed[widgetItem.id]) // the whole span moves away (down), whole
    }

    @Test
    fun aWidgetThatCannotMoveBlocksAndTheLandingIsRefused() {
        val dragged = app("a", 0, 0)
        val full = widget("w", 0, 0, 4, 3)
        val result = plan(page(dragged.copy(placement = null), full), dragged.copy(placement = null), 1.5f, 1.5f)
        assertNull(result.landing)
        assertTrue(result.pushed.isEmpty())
    }

    @Test
    fun aFullPageRefusesThePushAndKeepsItsItems() {
        val dragged = app("drag", 0, 0)
        val items = (0 until 12).filter { it != 0 }.map { app("i$it", it % 4, it / 4) }
        val result = plan(page(dragged, *items.toTypedArray()), dragged, 1.5f, 1.5f)
        // The dragged item's own cell is the only free one; neighbours may shuffle into it but never off the page.
        assertTrue(result.pushed.values.all { it.column in 0..3 && it.row in 0..2 })
        assertEquals(result.pushed.values.toSet().size, result.pushed.size)
    }

    @Test
    fun theDeadZoneOfAFolderIsItsWholeIcon() {
        val dragged = app("a", 0, 0)
        val box = folder("f", 2, 1)
        val result = plan(page(dragged, box), dragged, 2.5f + 0.45f, 1.5f - 0.4f)
        assertEquals(box.id, result.dropOn)
    }

    @Test
    fun aFolderOrAWidgetBeingDraggedNeverDropsOnTop() {
        val box = folder("f", 0, 0)
        val other = app("b", 2, 1)
        assertNull(plan(page(box, other), box, 2.5f, 1.5f).dropOn)
        val wide = widget("w", 0, 2, 1, 1)
        assertNull(plan(page(wide, other), wide, 2.5f, 1.5f).dropOn)
    }

    @Test
    fun aWidgetIsNeverATargetForADropOnTop() {
        val dragged = app("a", 0, 0)
        val widgetItem = widget("w", 2, 0, 2, 2)
        val result = plan(page(dragged, widgetItem), dragged, 3f, 1f)
        assertNull(result.dropOn)
    }

    @Test
    fun theResultNeverMutatesThePageAndIsDeterministic() {
        val dragged = app("a", 0, 0)
        val other = app("b", 2, 1)
        val p = page(dragged, other)
        val first = plan(p, dragged, 2.1f, 1.5f)
        assertEquals(first, plan(p, dragged, 2.1f, 1.5f))
        assertEquals(GridCell(2, 1), p.items.last().placement!!.cell)
    }

    @Test
    fun aReservedCellIsNeverPushedInto() {
        val dragged = app("a", 0, 0)
        val other = app("b", 1, 0)
        // Pointer right of b: b is pushed left, into a's own cell; reserved, so it goes elsewhere.
        val result =
            HomeDragPush.plan(page(dragged, other), dragged, 1.9f, 0.5f, tuning, reserved = setOf(GridCell(0, 0)))
        assertTrue(result.pushed[other.id] != GridCell(0, 0))
        assertNotNull(result.pushed[other.id])
    }

    @Test
    fun theTuningIsHeldToItsRangesAndPrintsItsValues() {
        val wild = HomeDragTuning(5f, 0f, 1, 99999, 5).coerced()
        assertEquals(HomeDragTuning(1.2f, 0.15f, 100, 1000, 300), wild)
        assertTrue(HomeDragTuning().asText().contains("ring=0.75"))
    }
}
