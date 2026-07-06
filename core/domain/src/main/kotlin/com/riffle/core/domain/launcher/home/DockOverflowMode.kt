package com.riffle.core.domain.launcher.home

sealed interface DockOverflowMode {
    data object Fits : DockOverflowMode

    data object FitByCompaction : DockOverflowMode

    data object RequiresOverflowNavigation : DockOverflowMode
}

fun dockOverflowMode(
    slotCount: Int,
    iconSizeDp: Int,
    itemSpacingDp: Int,
    minIconSizeDp: Int = MIN_DOCK_ICON_SIZE_DP,
    availableWidthDp: Int,
): DockOverflowMode {
    val normalizedSlotCount = slotCount.coerceAtLeast(0)
    if (normalizedSlotCount == 0) {
        return DockOverflowMode.Fits
    }

    val configuredWidthDp =
        dockContentWidthDp(
            slotCount = normalizedSlotCount,
            iconSizeDp = iconSizeDp.coerceAtLeast(0),
            itemSpacingDp = itemSpacingDp.coerceAtLeast(0),
        )
    val hardMinimumWidthDp =
        dockContentWidthDp(
            slotCount = normalizedSlotCount,
            iconSizeDp = minIconSizeDp.coerceAtLeast(0),
            itemSpacingDp = MIN_DOCK_ITEM_SPACING_DP,
        )

    return when {
        configuredWidthDp <= availableWidthDp.toLong() -> DockOverflowMode.Fits
        hardMinimumWidthDp <= availableWidthDp.toLong() -> DockOverflowMode.FitByCompaction
        else -> DockOverflowMode.RequiresOverflowNavigation
    }
}

private fun dockContentWidthDp(
    slotCount: Int,
    iconSizeDp: Int,
    itemSpacingDp: Int,
): Long =
    (slotCount.toLong() * iconSizeDp.toLong()) +
        ((slotCount - 1).coerceAtLeast(0).toLong() * itemSpacingDp.toLong())
