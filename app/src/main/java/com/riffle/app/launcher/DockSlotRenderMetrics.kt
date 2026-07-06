package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockOverflowMode
import com.riffle.core.domain.launcher.home.MIN_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_ITEM_SPACING_DP
import com.riffle.core.domain.launcher.home.dockOverflowMode
import kotlin.math.min

internal data class DockSlotRenderMetrics(
    val iconSizeDp: Int,
    val itemSpacingDp: Int,
    val overflowMode: DockOverflowMode,
)

internal fun dockSlotRenderMetrics(
    slotCount: Int,
    iconSizeDp: Int,
    itemSpacingDp: Int,
    availableContentWidthDp: Int,
): DockSlotRenderMetrics {
    val overflowMode =
        dockOverflowMode(
            slotCount = slotCount,
            iconSizeDp = iconSizeDp,
            itemSpacingDp = itemSpacingDp,
            availableWidthDp = availableContentWidthDp,
        )
    return when (overflowMode) {
        DockOverflowMode.FitByCompaction -> {
            val compactedSpacingDp =
                compactedDockSlotSpacingDp(
                    slotCount = slotCount,
                    iconSizeDp = iconSizeDp,
                    itemSpacingDp = itemSpacingDp,
                    availableContentWidthDp = availableContentWidthDp,
                )
            DockSlotRenderMetrics(
                iconSizeDp =
                    compactedDockSlotIconSizeDp(
                        slotCount = slotCount,
                        iconSizeDp = iconSizeDp,
                        itemSpacingDp = compactedSpacingDp,
                        availableContentWidthDp = availableContentWidthDp,
                    ),
                itemSpacingDp = compactedSpacingDp,
                overflowMode = overflowMode,
            )
        }

        DockOverflowMode.Fits,
        DockOverflowMode.RequiresOverflowNavigation,
        ->
            DockSlotRenderMetrics(
                iconSizeDp = iconSizeDp,
                itemSpacingDp = itemSpacingDp,
                overflowMode = overflowMode,
            )
    }
}

private fun compactedDockSlotSpacingDp(
    slotCount: Int,
    iconSizeDp: Int,
    itemSpacingDp: Int,
    availableContentWidthDp: Int,
): Int {
    if (slotCount <= 1) {
        return 0
    }

    val availableSpacingDp = availableContentWidthDp - (slotCount * iconSizeDp)
    return min(itemSpacingDp, availableSpacingDp / (slotCount - 1)).coerceAtLeast(MIN_DOCK_ITEM_SPACING_DP)
}

private fun compactedDockSlotIconSizeDp(
    slotCount: Int,
    iconSizeDp: Int,
    itemSpacingDp: Int,
    availableContentWidthDp: Int,
): Int {
    if (slotCount <= 0) {
        return iconSizeDp
    }

    val availableIconWidthDp = availableContentWidthDp - ((slotCount - 1).coerceAtLeast(0) * itemSpacingDp)
    return min(iconSizeDp, availableIconWidthDp / slotCount).coerceAtLeast(MIN_DOCK_ICON_SIZE_DP)
}

internal fun dockSlotContentWidthDp(
    slotCount: Int,
    metrics: DockSlotRenderMetrics,
): Int {
    if (slotCount <= 0) {
        return 0
    }
    return (slotCount * metrics.iconSizeDp) + ((slotCount - 1) * metrics.itemSpacingDp)
}
