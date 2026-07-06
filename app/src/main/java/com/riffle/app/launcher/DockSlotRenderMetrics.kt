package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockOverflowMode
import com.riffle.core.domain.launcher.home.MIN_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_ITEM_SPACING_DP
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
    val gapCount = slotCount - 1
    val widthAfterIcons = availableContentWidthDp - (slotCount * iconSizeDp)

    if (slotCount <= 0 || availableContentWidthDp <= 0) {
        return configuredMetrics
    }

    return when (overflowMode) {
        DockOverflowMode.Fits -> configuredMetrics
        DockOverflowMode.FitByCompaction ->
            configuredMetrics.copy(
                iconSizeDp =
                    if (gapCount > 0 && widthAfterIcons >= gapCount * MIN_DOCK_ITEM_SPACING_DP) {
                        iconSizeDp
                    } else {
                        compactedIconSizeDp(
                            slotCount = slotCount,
                            gapCount = gapCount,
                            availableContentWidthDp = availableContentWidthDp,
                            iconSizeDp = iconSizeDp,
                        )
                    },
                itemSpacingDp =
                    if (gapCount > 0 && widthAfterIcons >= gapCount * MIN_DOCK_ITEM_SPACING_DP) {
                        (widthAfterIcons / gapCount).coerceAtMost(itemSpacingDp)
                    } else {
                        MIN_DOCK_ITEM_SPACING_DP
                    },
            )
        DockOverflowMode.RequiresOverflowNavigation ->
            DockSlotRenderMetrics(
                iconSizeDp =
                    compactedIconSizeDp(
                        slotCount = slotCount,
                        gapCount = gapCount,
                        availableContentWidthDp = availableContentWidthDp,
                        iconSizeDp = iconSizeDp,
                    ),
                itemSpacingDp = MIN_DOCK_ITEM_SPACING_DP,
                overflowMode = overflowMode,
            )
    }
}

private fun compactedIconSizeDp(
    slotCount: Int,
    gapCount: Int,
    availableContentWidthDp: Int,
    iconSizeDp: Int,
): Int =
    ((availableContentWidthDp - (gapCount * MIN_DOCK_ITEM_SPACING_DP)) / slotCount)
        .coerceAtLeast(MIN_DOCK_ICON_SIZE_DP)
        .coerceAtMost(iconSizeDp)
