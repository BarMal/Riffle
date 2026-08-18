package com.riffle.core.domain.launcher.home

/**
 * Whether a dock's slots fit the extent it has to lay them out along.
 *
 * Expressed in main-axis terms rather than width: the question is "N slots of this size, with this
 * spacing, along this much room" regardless of whether the dock runs across the bottom or down an
 * edge. Nothing here needs to know which. See [com.riffle.core.domain.launcher.home.DockPosition].
 */
sealed interface DockOverflowMode {
    data object Fits : DockOverflowMode

    data object FitByCompaction : DockOverflowMode

    data object RequiresOverflowNavigation : DockOverflowMode
}

/**
 * [availableMainAxisDp] is the room along the dock's own run: its width when the dock is
 * horizontal, its height when it is vertical.
 */
fun dockOverflowMode(
    slotCount: Int,
    iconSizeDp: Int,
    itemSpacingDp: Int,
    minIconSizeDp: Int = MIN_DOCK_ICON_SIZE_DP,
    availableMainAxisDp: Int,
): DockOverflowMode {
    val normalizedSlotCount = slotCount.coerceAtLeast(0)
    if (normalizedSlotCount == 0) {
        return DockOverflowMode.Fits
    }

    val configuredMainAxisDp =
        dockContentMainAxisDp(
            slotCount = normalizedSlotCount,
            iconSizeDp = iconSizeDp.coerceAtLeast(0),
            itemSpacingDp = itemSpacingDp.coerceAtLeast(0),
        )
    val hardMinimumMainAxisDp =
        dockContentMainAxisDp(
            slotCount = normalizedSlotCount,
            iconSizeDp = minIconSizeDp.coerceAtLeast(0),
            itemSpacingDp = MIN_DOCK_ITEM_SPACING_DP,
        )

    return when {
        configuredMainAxisDp <= availableMainAxisDp.toLong() -> DockOverflowMode.Fits
        hardMinimumMainAxisDp <= availableMainAxisDp.toLong() -> DockOverflowMode.FitByCompaction
        else -> DockOverflowMode.RequiresOverflowNavigation
    }
}

private fun dockContentMainAxisDp(
    slotCount: Int,
    iconSizeDp: Int,
    itemSpacingDp: Int,
): Long =
    (slotCount.toLong() * iconSizeDp.toLong()) +
        ((slotCount - 1).coerceAtLeast(0).toLong() * itemSpacingDp.toLong())
