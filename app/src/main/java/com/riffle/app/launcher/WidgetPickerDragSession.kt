package com.riffle.app.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.riffle.app.launcher.widgets.preferredGridSpan
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.isHorizontalEdge
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import kotlin.math.roundToInt

internal enum class WidgetPickerDragTarget {
    HOME,
    DOCK,
}

internal data class WidgetPickerDragPlacementPreview(
    val provider: InstalledWidgetProvider,
    val target: WidgetPickerDragTarget,
    val targetPageId: LauncherPageId,
    val cell: GridCell,
    val span: GridSpan,
    val isValid: Boolean,
    val conflictingItemIds: Set<LauncherItemId> = emptySet(),
)

internal data class WidgetPickerDragSnapshot(
    val provider: InstalledWidgetProvider,
    val position: Offset,
    val rootSize: IntSize,
)

internal data class WidgetPickerDockPlacementPreview(
    val provider: InstalledWidgetProvider,
    val dockIndex: Int,
    val isValid: Boolean,
)

internal enum class WidgetPickerEdgeHoverSide {
    LEFT,
    RIGHT,
}

data class WidgetPickerPlacementCandidate(
    val pageId: LauncherPageId? = null,
    val cell: GridCell? = null,
    val span: GridSpan? = null,
    val dockIndex: Int? = null,
)

data class WidgetPickerAccessiblePlacement(
    val provider: InstalledWidgetProvider,
    val target: WidgetAddTarget,
    val initialPageId: LauncherPageId,
    val candidates: List<WidgetPickerPlacementCandidate>,
    val selectedCandidateIndex: Int = 0,
) {
    val selectedCandidate: WidgetPickerPlacementCandidate?
        get() = candidates.getOrNull(selectedCandidateIndex)

    val isValid: Boolean
        get() = selectedCandidate != null

    fun selectCandidate(candidate: WidgetPickerPlacementCandidate): WidgetPickerAccessiblePlacement =
        copy(selectedCandidateIndex = candidates.indexOf(candidate).coerceAtLeast(0))
}

internal fun widgetPickerDragPlacementPreviewFor(
    page: LauncherPage,
    provider: InstalledWidgetProvider,
    cell: GridCell,
    availableWidthDp: Int,
    availableHeightDp: Int,
): WidgetPickerDragPlacementPreview {
    val span =
        provider.dimensions.preferredGridSpan(
            grid = page.grid,
            availableWidthDp = availableWidthDp,
            availableHeightDp = availableHeightDp,
        )
    val isInBounds = page.grid.contains(origin = cell, span = span)
    val candidateCells = if (isInBounds) span.cellsAt(cell) else emptyList()
    val conflictingItems =
        page.items
            .filter { item -> candidateCells.any(item::occupies) }
            .map { item -> item.id }
            .toSet()
    return WidgetPickerDragPlacementPreview(
        provider = provider,
        target = WidgetPickerDragTarget.HOME,
        targetPageId = page.id,
        cell = cell,
        span = span,
        isValid = page.type !is LauncherPageType.Generated && isInBounds && conflictingItems.isEmpty(),
        conflictingItemIds = conflictingItems,
    )
}

internal fun widgetPickerDockPlacementPreviewFor(
    snapshot: WidgetPickerDragSnapshot,
    dock: DockModel,
    dockBounds: Rect?,
    isRtl: Boolean = false,
    position: DockPosition = DockPosition.BOTTOM,
): WidgetPickerDockPlacementPreview? {
    val bounds = dockBounds?.takeIf { it.width > 0f && it.height > 0f }
    return bounds?.takeIf { it.contains(snapshot.position) }?.let {
        val insertionCount = dock.items.size + 1
        // Slots run down a side dock, so how far along the run the pointer is comes from y there
        // and from x on a horizontal edge. Only the horizontal run mirrors in RTL.
        val physicalFraction =
            if (position.isHorizontalEdge) {
                ((snapshot.position.x - it.left) / it.width).coerceIn(0f, 1f)
            } else {
                ((snapshot.position.y - it.top) / it.height).coerceIn(0f, 1f)
            }
        val logicalFraction = if (isRtl && position.isHorizontalEdge) 1f - physicalFraction else physicalFraction
        val index = (logicalFraction * insertionCount).toInt().coerceIn(0, dock.items.size)
        WidgetPickerDockPlacementPreview(
            provider = snapshot.provider,
            dockIndex = index,
            isValid = dock.isEnabled && dock.items.size < dock.capacity,
        )
    }
}

internal fun widgetPickerDropIsValid(
    target: WidgetAddTarget?,
    homePreview: WidgetPickerDragPlacementPreview?,
    dockPreview: WidgetPickerDockPlacementPreview?,
): Boolean =
    when (target) {
        WidgetAddTarget.HOME -> homePreview?.isValid == true
        WidgetAddTarget.DOCK -> dockPreview?.isValid == true
        // The picker covers the shelf, so the panel is never under the pointer. It is placed
        // through the guided path instead.
        WidgetAddTarget.DOCK_PANEL -> false
        null -> false
    }

internal fun firstValidWidgetPickerPlacementPreviewFor(
    page: LauncherPage,
    provider: InstalledWidgetProvider,
    availableWidthDp: Int,
    availableHeightDp: Int,
): WidgetPickerDragPlacementPreview? {
    for (row in 0 until page.grid.rows.coerceAtLeast(0)) {
        for (column in 0 until page.grid.columns.coerceAtLeast(0)) {
            widgetPickerDragPlacementPreviewFor(
                page = page,
                provider = provider,
                cell = GridCell(column = column, row = row),
                availableWidthDp = availableWidthDp,
                availableHeightDp = availableHeightDp,
            ).takeIf { preview -> preview.isValid }?.let { preview -> return preview }
        }
    }
    return null
}

internal fun widgetPickerDragPlacementPreviewFor(
    snapshot: WidgetPickerDragSnapshot,
    page: LauncherPage,
    workspaceBounds: Rect?,
    dockBounds: Rect?,
    density: Float,
): WidgetPickerDragPlacementPreview? {
    if (
        workspaceBounds == null ||
        density <= 0f ||
        widgetPickerDropTarget(
            position = snapshot.position,
            workspaceBounds = workspaceBounds,
            dockBounds = dockBounds,
        ) != WidgetAddTarget.HOME
    ) {
        return null
    }
    return widgetPickerDragPlacementPreviewFor(
        page = page,
        provider = snapshot.provider,
        cell = widgetPickerDropCell(snapshot.position, workspaceBounds, page.grid),
        availableWidthDp = (snapshot.rootSize.width / density).roundToInt(),
        availableHeightDp = (snapshot.rootSize.height / density).roundToInt(),
    )
}

internal fun widgetPickerEdgeHoverPageId(
    position: Offset,
    workspaceBounds: Rect,
    edgeZonePx: Float,
    pages: List<LauncherPage>,
    selectedPageId: LauncherPageId,
    isRtl: Boolean = false,
): LauncherPageId? =
    widgetPickerEdgeHoverSide(
        position = position,
        workspaceBounds = workspaceBounds,
        edgeZonePx = edgeZonePx,
    )?.let { side ->
        widgetPickerEdgeHoverPageId(
            side = side,
            pages = pages,
            selectedPageId = selectedPageId,
            isRtl = isRtl,
        )
    }

internal fun widgetPickerEdgeHoverSide(
    position: Offset,
    workspaceBounds: Rect,
    edgeZonePx: Float,
): WidgetPickerEdgeHoverSide? {
    if (
        edgeZonePx <= 0f ||
        workspaceBounds.width <= 0f ||
        !workspaceBounds.contains(position)
    ) {
        return null
    }

    val boundedEdgeZone = edgeZonePx.coerceAtMost(workspaceBounds.width / 2f)
    return when {
        position.x <= workspaceBounds.left + boundedEdgeZone -> WidgetPickerEdgeHoverSide.LEFT
        position.x >= workspaceBounds.right - boundedEdgeZone -> WidgetPickerEdgeHoverSide.RIGHT
        else -> null
    }
}

internal fun widgetPickerEdgeHoverPageId(
    side: WidgetPickerEdgeHoverSide,
    pages: List<LauncherPage>,
    selectedPageId: LauncherPageId,
    isRtl: Boolean = false,
): LauncherPageId? {
    val selectedPageIndex = pages.indexOfFirst { page -> page.id == selectedPageId }
    val physicalDirection = if (side == WidgetPickerEdgeHoverSide.LEFT) -1 else 1
    val pageDirection = if (isRtl) -physicalDirection else physicalDirection
    val selectedPage = pages.getOrNull(selectedPageIndex)
    val adjacentPage = pages.getOrNull(selectedPageIndex + pageDirection)
    return adjacentPage
        ?.takeIf {
            selectedPage != null &&
                selectedPage.type !is LauncherPageType.Generated &&
                adjacentPage.type !is LauncherPageType.Generated
        }?.id
}

private fun GridSpan.cellsAt(origin: GridCell): List<GridCell> =
    (origin.column until origin.column + columns.coerceAtLeast(1)).flatMap { column ->
        (origin.row until origin.row + rows.coerceAtLeast(1)).map { row ->
            GridCell(column = column, row = row)
        }
    }

private fun GridDimensions.contains(
    origin: GridCell,
    span: GridSpan,
): Boolean {
    return columns > 0 &&
        rows > 0 &&
        origin.column >= 0 &&
        origin.row >= 0 &&
        span.columns > 0 &&
        span.rows > 0 &&
        span.columns <= columns &&
        span.rows <= rows &&
        origin.column <= columns - span.columns &&
        origin.row <= rows - span.rows
}
