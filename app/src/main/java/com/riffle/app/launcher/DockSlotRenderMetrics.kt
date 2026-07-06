package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.MIN_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_ITEM_SPACING_DP

internal data class DockSlotRenderMetrics(
    val iconSizeDp: Int,
    val itemSpacingDp: Int,
)

internal fun dockSlotRenderMetrics(
    slotCount: Int,
    iconSizeDp: Int,
    itemSpacingDp: Int,
    availableContentWidthDp: Int,
): DockSlotRenderMetrics {
    val configuredMetrics = DockSlotRenderMetrics(iconSizeDp = iconSizeDp, itemSpacingDp = itemSpacingDp)
    val gapCount = slotCount - 1
    val widthAfterIcons = availableContentWidthDp - (slotCount * iconSizeDp)
    val configuredContentWidth =
        when {
            slotCount <= 0 -> 0
            else -> (slotCount * iconSizeDp) + (gapCount * itemSpacingDp)
        }

    return when {
        slotCount <= 0 || availableContentWidthDp <= 0 -> configuredMetrics
        configuredContentWidth <= availableContentWidthDp -> configuredMetrics
        gapCount > 0 && widthAfterIcons >= gapCount * MIN_DOCK_ITEM_SPACING_DP ->
            configuredMetrics.copy(
                itemSpacingDp = (widthAfterIcons / gapCount).coerceAtMost(itemSpacingDp),
            )
        else ->
            DockSlotRenderMetrics(
                iconSizeDp =
                    ((availableContentWidthDp - (gapCount * MIN_DOCK_ITEM_SPACING_DP)) / slotCount)
                        .coerceAtLeast(MIN_DOCK_ICON_SIZE_DP)
                        .coerceAtMost(iconSizeDp),
                itemSpacingDp = MIN_DOCK_ITEM_SPACING_DP,
            )
    }
}
