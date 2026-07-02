package com.riffle.core.domain.launcher.settings

import kotlin.math.roundToInt

fun overlayDockVerticalOffsetFromDrag(
    startOffsetDp: Int,
    dragDeltaPx: Float,
    density: Float,
): Int {
    if (density <= 0f) return startOffsetDp.coerceOverlayDockVerticalOffset()

    return (startOffsetDp + (dragDeltaPx / density).roundToInt()).coerceOverlayDockVerticalOffset()
}

fun Int.coerceOverlayDockVerticalOffset(): Int =
    coerceIn(
        MIN_OVERLAY_DOCK_VERTICAL_OFFSET_DP,
        MAX_OVERLAY_DOCK_VERTICAL_OFFSET_DP,
    )

fun OverlayDockSettings.coerceOverlayDockSettings(): OverlayDockSettings =
    copy(
        handleThicknessDp =
            handleThicknessDp.coerceIn(
                MIN_OVERLAY_DOCK_HANDLE_THICKNESS_DP,
                MAX_OVERLAY_DOCK_HANDLE_THICKNESS_DP,
            ),
        handleHeightDp =
            handleHeightDp.coerceIn(
                MIN_OVERLAY_DOCK_HANDLE_HEIGHT_DP,
                MAX_OVERLAY_DOCK_HANDLE_HEIGHT_DP,
            ),
        verticalOffsetDp = verticalOffsetDp.coerceOverlayDockVerticalOffset(),
        handleAlphaPercent =
            handleAlphaPercent.coerceIn(
                MIN_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT,
                MAX_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT,
            ),
        expandedIconSizeDp =
            expandedIconSizeDp.coerceIn(
                MIN_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP,
                MAX_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP,
            ),
    )
