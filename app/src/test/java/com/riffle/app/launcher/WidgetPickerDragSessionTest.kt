package com.riffle.app.launcher

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
            )

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
            )

        assertFalse(preview.isValid)
        assertTrue(preview.conflictingItemIds.isEmpty())
    }

    private fun page(
        type: LauncherPageType = LauncherPageType.Home,
        items: List<WidgetItem> = emptyList(),
    ): LauncherPage =
        LauncherPage(
            id = LauncherPageId("home"),
            type = type,
            grid = GridDimensions(columns = 4, rows = 5),
            items = items,
        )

    private fun provider(
        targetCellWidth: Int,
        targetCellHeight: Int,
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
