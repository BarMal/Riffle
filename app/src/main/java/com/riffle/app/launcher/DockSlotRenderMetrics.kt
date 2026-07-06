package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockOverflowMode
import com.riffle.core.domain.launcher.home.dockOverflowMode

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
    val configuredMetrics =
        DockSlotRenderMetrics(
            iconSizeDp = iconSizeDp,
            itemSpacingDp = itemSpacingDp,
            overflowMode = overflowMode,
        )

    return configuredMetrics
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
