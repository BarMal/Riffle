package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockOverflowMode
import com.riffle.core.domain.launcher.home.MIN_DOCK_ITEM_SPACING_DP
import com.riffle.core.domain.launcher.home.dockOverflowMode
import kotlin.math.min

internal data class DockSlotRenderMetrics(
    val iconSizeDp: Int,
    val itemSpacingDp: Int,
    val overflowMode: DockOverflowMode,
)

/**
 * The pinned run's metrics: the icon is always the configured size, whatever room the run has.
 *
 * When the slots do not fit, the gap between them gives first (down to [MIN_DOCK_ITEM_SPACING_DP], while the icons
 * still fit at that gap); beyond that the run keeps its size and scrolls, fading at its edges. Icons are never
 * shrunk to fit: a pinned icon is the size of a Home icon, and a dynamic entry beside it never makes it smaller.
 */
internal fun dockSlotRenderMetrics(
    slotCount: Int,
    iconSizeDp: Int,
    itemSpacingDp: Int,
    availableContentMainAxisDp: Int,
): DockSlotRenderMetrics {
    val overflowMode =
        dockOverflowMode(
            slotCount = slotCount,
            iconSizeDp = iconSizeDp,
            itemSpacingDp = itemSpacingDp,
            availableMainAxisDp = availableContentMainAxisDp,
        )
    return when (overflowMode) {
        DockOverflowMode.FitByCompaction ->
            DockSlotRenderMetrics(
                iconSizeDp = iconSizeDp,
                itemSpacingDp =
                    compactedDockSlotSpacingDp(
                        slotCount = slotCount,
                        iconSizeDp = iconSizeDp,
                        itemSpacingDp = itemSpacingDp,
                        availableContentMainAxisDp = availableContentMainAxisDp,
                    ),
                overflowMode = overflowMode,
            )

        DockOverflowMode.Fits ->
            DockSlotRenderMetrics(
                iconSizeDp = iconSizeDp,
                itemSpacingDp = itemSpacingDp,
                overflowMode = overflowMode,
            )
    }
}

/**
 * Whether the pinned run is longer than its viewport at these metrics, so it scrolls (and fades at the edge that has
 * more). The rule the strip's overflow affordance follows.
 */
internal fun dockPinnedRunScrolls(
    slotCount: Int,
    metrics: DockSlotRenderMetrics,
    viewportMainAxisDp: Int,
): Boolean = dockSlotContentMainAxisDp(slotCount, metrics) > viewportMainAxisDp

private fun compactedDockSlotSpacingDp(
    slotCount: Int,
    iconSizeDp: Int,
    itemSpacingDp: Int,
    availableContentMainAxisDp: Int,
): Int {
    if (slotCount <= 1) {
        return 0
    }

    val availableSpacingDp = availableContentMainAxisDp - (slotCount * iconSizeDp)
    return min(itemSpacingDp, availableSpacingDp / (slotCount - 1)).coerceAtLeast(MIN_DOCK_ITEM_SPACING_DP)
}

internal fun dockSlotContentMainAxisDp(
    slotCount: Int,
    metrics: DockSlotRenderMetrics,
): Int {
    if (slotCount <= 0) {
        return 0
    }
    return (slotCount * metrics.iconSizeDp) + ((slotCount - 1) * metrics.itemSpacingDp)
}
