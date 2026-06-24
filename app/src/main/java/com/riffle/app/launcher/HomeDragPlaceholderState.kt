package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.WidgetItem

internal data class HomeDragPlaceholderState(
    val span: GridSpan,
    val fillSpan: Boolean,
)

internal fun HomeGridCellState.dragPlaceholderAtProjectedCell(session: HomeDragSession): HomeDragPlaceholderState? {
    val occupyingItem = previewItems.occupyingItemAt(cell = cell)

    return when {
        session.item is WidgetItem ->
            HomeDragPlaceholderState(
                span = session.item.placement?.span ?: GridSpan(),
                fillSpan = true,
            )

        occupyingItem is WidgetItem -> null

        else ->
            HomeDragPlaceholderState(
                span = session.item.placement?.span ?: GridSpan(),
                fillSpan = false,
            )
    }
}
