package com.riffle.app.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
