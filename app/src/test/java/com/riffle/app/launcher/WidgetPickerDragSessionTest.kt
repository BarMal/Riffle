package com.riffle.app.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPickerDragSessionTest {
    @Test
    fun acceptsAnEmptyHomeCellForTheProviderSpan() {
        val preview =
            widgetPickerDragPlacementPreviewFor(
                page = page(),
                provider = provider(targetCellWidth = 2, targetCellHeight = 2),
                cell = GridCell(column = 1, row = 1),
                availableWidthDp = 400,
                availableHeightDp = 500,
            )

        assertTrue(preview.isValid)
        assertEquals(GridSpan(columns = 2, rows = 2), preview.span)
        assertEquals(emptySet<LauncherItemId>(), preview.conflictingItemIds)
    }

    @Test
    fun rejectsAHomeCellWhenTheProviderSpanCollidesWithAnExistingItem() {
        val existing =
            WidgetItem(
                id = LauncherItemId("existing"),
                appWidgetId = HostedWidgetId(1),
                label = "Existing",
                placement = GridPlacement(cell = GridCell(column = 2, row = 2)),
            )
        val preview =
            widgetPickerDragPlacementPreviewFor(
                page = page(items = listOf(existing)),
                provider = provider(targetCellWidth = 2, targetCellHeight = 2),
                cell = GridCell(column = 1, row = 1),
                availableWidthDp = 400,
                availableHeightDp = 500,
            )

        assertFalse(preview.isValid)
        assertEquals(setOf(existing.id), preview.conflictingItemIds)
    }

    @Test
    fun rejectsAProviderSpanThatRunsOutsideTheHomeGrid() {
        val preview =
            widgetPickerDragPlacementPreviewFor(
                page = page(),
                provider = provider(targetCellWidth = 2, targetCellHeight = 2),
                cell = GridCell(column = 3, row = 4),
                availableWidthDp = 400,
                availableHeightDp = 500,
            )

        assertFalse(preview.isValid)
        assertTrue(preview.conflictingItemIds.isEmpty())
    }

    @Test
    fun preservesAnOversizedProviderSpanWhileRejectingTheHomeDrop() {
        val preview =
            widgetPickerDragPlacementPreviewFor(
                page = page(),
                provider = provider(targetCellWidth = 5, targetCellHeight = 2),
                cell = GridCell(column = 0, row = 0),
                availableWidthDp = 400,
                availableHeightDp = 500,
            )

        assertEquals(GridSpan(columns = 5, rows = 2), preview.span)
        assertFalse(preview.isValid)
    }

    @Test
    fun rejectsExtremeProviderDimensionsBeforeEnumeratingCandidateCells() {
        val preview =
            widgetPickerDragPlacementPreviewFor(
                page = page(),
                provider = provider(targetCellWidth = Int.MAX_VALUE, targetCellHeight = Int.MAX_VALUE),
                cell = GridCell(column = 0, row = 0),
                availableWidthDp = 400,
                availableHeightDp = 500,
            )

        assertEquals(GridSpan(columns = Int.MAX_VALUE, rows = Int.MAX_VALUE), preview.span)
        assertFalse(preview.isValid)
        assertTrue(preview.conflictingItemIds.isEmpty())
    }

    @Test
    fun rejectsGeneratedPagesWithoutReportingAFalseCollision() {
        val preview =
            widgetPickerDragPlacementPreviewFor(
                page = page(type = LauncherPageType.Generated(GeneratedLauncherPageKind.APP)),
                provider = provider(targetCellWidth = 1, targetCellHeight = 1),
                cell = GridCell(column = 0, row = 0),
                availableWidthDp = 400,
                availableHeightDp = 500,
            )

        assertFalse(preview.isValid)
        assertTrue(preview.conflictingItemIds.isEmpty())
    }

    @Test
    fun derivesPreviewSpanFromMinimumDimensionsWhenTargetCellsAreMissing() {
        val existing =
            WidgetItem(
                id = LauncherItemId("existing"),
                appWidgetId = HostedWidgetId(2),
                label = "Existing",
                placement = GridPlacement(cell = GridCell(column = 1, row = 0)),
            )
        val preview =
            widgetPickerDragPlacementPreviewFor(
                page = page(items = listOf(existing)),
                provider = provider(targetCellWidth = null, targetCellHeight = null),
                cell = GridCell(column = 0, row = 0),
                availableWidthDp = 400,
                availableHeightDp = 500,
            )

        assertEquals(GridSpan(columns = 2, rows = 1), preview.span)
        assertFalse(preview.isValid)
        assertEquals(setOf(existing.id), preview.conflictingItemIds)
    }

    @Test
    fun edgeHoverSelectsTheAdjacentEditablePage() {
        val pages =
            listOf(
                page(id = "first"),
                page(id = "second"),
                page(id = "third"),
            )

        assertEquals(
            LauncherPageId("first"),
            widgetPickerEdgeHoverPageId(
                position = Offset(5f, 100f),
                workspaceBounds = Rect(0f, 0f, 400f, 500f),
                edgeZonePx = 40f,
                pages = pages,
                selectedPageId = LauncherPageId("second"),
            ),
        )
        assertEquals(
            LauncherPageId("third"),
            widgetPickerEdgeHoverPageId(
                position = Offset(395f, 100f),
                workspaceBounds = Rect(0f, 0f, 400f, 500f),
                edgeZonePx = 40f,
                pages = pages,
                selectedPageId = LauncherPageId("second"),
            ),
        )
    }

    @Test
    fun edgeHoverUsesVisualPageDirectionInRtl() {
        val pages = listOf(page(id = "first"), page(id = "second"), page(id = "third"))

        assertEquals(
            LauncherPageId("third"),
            widgetPickerEdgeHoverPageId(
                position = Offset(5f, 100f),
                workspaceBounds = Rect(0f, 0f, 400f, 500f),
                edgeZonePx = 40f,
                pages = pages,
                selectedPageId = LauncherPageId("second"),
                isRtl = true,
            ),
        )
    }

    @Test
    fun edgeHoverDoesNotCrossGeneratedPageBoundary() {
        val pages =
            listOf(
                page(id = "home"),
                page(id = "generated", type = LauncherPageType.Generated(GeneratedLauncherPageKind.APP)),
                page(id = "other-home"),
            )

        assertEquals(
            null,
            widgetPickerEdgeHoverPageId(
                position = Offset(395f, 100f),
                workspaceBounds = Rect(0f, 0f, 400f, 500f),
                edgeZonePx = 40f,
                pages = pages,
                selectedPageId = LauncherPageId("home"),
            ),
        )
    }

    @Test
    fun edgeHoverIgnoresInteriorOutsideAndTerminalTargets() {
        val pages = listOf(page(id = "first"), page(id = "second"))
        val bounds = Rect(0f, 0f, 400f, 500f)

        assertEquals(
            null,
            widgetPickerEdgeHoverPageId(
                position = Offset(200f, 100f),
                workspaceBounds = bounds,
                edgeZonePx = 40f,
                pages = pages,
                selectedPageId = LauncherPageId("first"),
            ),
        )
        assertEquals(
            null,
            widgetPickerEdgeHoverPageId(
                position = Offset(405f, 100f),
                workspaceBounds = bounds,
                edgeZonePx = 40f,
                pages = pages,
                selectedPageId = LauncherPageId("first"),
            ),
        )
        assertEquals(
            null,
            widgetPickerEdgeHoverPageId(
                position = Offset(5f, 100f),
                workspaceBounds = bounds,
                edgeZonePx = 40f,
                pages = pages,
                selectedPageId = LauncherPageId("first"),
            ),
        )
    }

    @Test
    fun retainedDragSnapshotRecomputesCollisionPreviewForTheSelectedPage() {
        val provider = provider(targetCellWidth = 1, targetCellHeight = 1)
        val occupiedCell = GridCell(column = 3, row = 2)
        val occupied =
            WidgetItem(
                id = LauncherItemId("occupied"),
                appWidgetId = HostedWidgetId(3),
                label = "Occupied",
                placement = GridPlacement(cell = occupiedCell),
            )
        val snapshot =
            WidgetPickerDragSnapshot(
                provider = provider,
                position = Offset(395f, 250f),
                rootSize = IntSize(400, 500),
            )
        val bounds = Rect(0f, 0f, 400f, 500f)

        val firstPagePreview =
            widgetPickerDragPlacementPreviewFor(
                snapshot = snapshot,
                page = page(id = "first"),
                workspaceBounds = bounds,
                dockBounds = null,
                density = 1f,
            )
        val secondPagePreview =
            widgetPickerDragPlacementPreviewFor(
                snapshot = snapshot,
                page = page(id = "second", items = listOf(occupied)),
                workspaceBounds = bounds,
                dockBounds = null,
                density = 1f,
            )

        assertEquals(LauncherPageId("first"), firstPagePreview?.targetPageId)
        assertTrue(firstPagePreview?.isValid == true)
        assertEquals(LauncherPageId("second"), secondPagePreview?.targetPageId)
        assertEquals(setOf(occupied.id), secondPagePreview?.conflictingItemIds)
        assertFalse(secondPagePreview?.isValid == true)
    }

    @Test
    fun fractionalDensityUsesTheSameRoundedGeometryForPreviewAndDrop() {
        val provider =
            provider(targetCellWidth = null, targetCellHeight = 1).copy(
                dimensions =
                    WidgetProviderDimensions(
                        minWidthDp = 67,
                        minHeightDp = 1,
                        targetCellHeight = 1,
                    ),
            )
        val page =
            page(id = "fractional").copy(
                grid = GridDimensions(columns = 3, rows = 1),
            )
        val snapshot =
            WidgetPickerDragSnapshot(
                provider = provider,
                position = Offset(200f, 50f),
                rootSize = IntSize(401, 200),
            )

        val sharedPreviewAndDropGeometry =
            widgetPickerDragPlacementPreviewFor(
                snapshot = snapshot,
                page = page,
                workspaceBounds = Rect(0f, 0f, 401f, 200f),
                dockBounds = null,
                density = 2f,
            )
        val dropPreview =
            widgetPickerDragPlacementPreviewFor(
                page = page,
                provider = provider,
                cell = GridCell(column = 1, row = 0),
                availableWidthDp = 201,
                availableHeightDp = 100,
            )

        assertEquals(GridSpan(columns = 1, rows = 1), sharedPreviewAndDropGeometry?.span)
        assertEquals(dropPreview, sharedPreviewAndDropGeometry)
        assertTrue(sharedPreviewAndDropGeometry?.isValid == true)
    }

    private fun page(
        id: String = "home",
        type: LauncherPageType = LauncherPageType.Home,
        items: List<WidgetItem> = emptyList(),
    ): LauncherPage =
        LauncherPage(
            id = LauncherPageId(id),
            type = type,
            grid = GridDimensions(columns = 4, rows = 5),
            items = items,
        )

    private fun provider(
        targetCellWidth: Int?,
        targetCellHeight: Int?,
    ): InstalledWidgetProvider =
        InstalledWidgetProvider(
            identity =
                WidgetProviderIdentity(
                    packageName = AppPackageName("com.example.widget"),
                    className = WidgetProviderClassName(".Widget"),
                ),
            label = "Widget",
            dimensions =
                WidgetProviderDimensions(
                    minWidthDp = 120,
                    minHeightDp = 80,
                    targetCellWidth = targetCellWidth,
                    targetCellHeight = targetCellHeight,
                ),
        )
}
