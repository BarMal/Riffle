package com.riffle.core.domain.launcher.cards

/** Framework-independent window and separating-hinge inputs for the adaptive TimeScape surface. */
data class TimeScapeWindowLayout(
    val widthDp: Int,
    val heightDp: Int,
    val safeStartDp: Int = 0,
    val safeTopDp: Int = 0,
    val safeEndDp: Int = 0,
    val safeBottomDp: Int = 0,
    val separatingHinges: List<TimeScapeHingeBounds> = emptyList(),
)

data class TimeScapeHingeBounds(
    val leftDp: Int,
    val topDp: Int,
    val rightDp: Int,
    val bottomDp: Int,
) {
    val isVertical: Boolean get() = heightDp >= widthDp
    val widthDp: Int get() = (rightDp - leftDp).coerceAtLeast(0)
    val heightDp: Int get() = (bottomDp - topDp).coerceAtLeast(0)
}

enum class TimeScapePaneMode {
    COMPACT,
    TWO_PANE,
    THREE_PANE,
}

/**
 * Concrete pane sizes with bounded Spline and detail surfaces. A vertical separating hinge is a
 * real layout gap; callers must render content on either side rather than under it.
 */
data class TimeScapePaneLayout(
    val mode: TimeScapePaneMode,
    val railWidthDp: Int,
    val splineWidthDp: Int,
    val detailWidthDp: Int,
    val hingeGapDp: Int = 0,
) {
    val showsRail: Boolean get() = mode != TimeScapePaneMode.COMPACT
    val showsDetailPane: Boolean get() = mode == TimeScapePaneMode.THREE_PANE
}

/** Chooses TimeScape panes from the current usable window, never a device-name classification. */
class TimeScapePaneLayoutPolicy {
    fun layoutFor(window: TimeScapeWindowLayout): TimeScapePaneLayout {
        val safeWidth = (window.widthDp - window.safeStartDp - window.safeEndDp).coerceAtLeast(0)
        val verticalHinge = window.separatingHinges.firstOrNull { hinge -> hinge.isVertical }
        val hingeGap = verticalHinge?.widthDp ?: 0
        val usableWidth = (safeWidth - hingeGap).coerceAtLeast(0)

        return when {
            usableWidth < MIN_TWO_PANE_WIDTH_DP ->
                TimeScapePaneLayout(TimeScapePaneMode.COMPACT, 0, usableWidth, 0, hingeGap)

            usableWidth < MIN_THREE_PANE_WIDTH_DP ->
                TimeScapePaneLayout(
                    mode = TimeScapePaneMode.TWO_PANE,
                    railWidthDp = RAIL_WIDTH_DP,
                    splineWidthDp = (usableWidth - RAIL_WIDTH_DP).coerceIn(MIN_SPLINE_WIDTH_DP, MAX_SPLINE_WIDTH_DP),
                    detailWidthDp = 0,
                    hingeGapDp = hingeGap,
                )

            else ->
                TimeScapePaneLayout(
                    mode = TimeScapePaneMode.THREE_PANE,
                    railWidthDp = RAIL_WIDTH_DP,
                    splineWidthDp =
                        (usableWidth - RAIL_WIDTH_DP - DETAIL_WIDTH_DP)
                            .coerceIn(MIN_SPLINE_WIDTH_DP, MAX_SPLINE_WIDTH_DP),
                    detailWidthDp = DETAIL_WIDTH_DP,
                    hingeGapDp = hingeGap,
                )
        }
    }

    private companion object {
        const val MIN_TWO_PANE_WIDTH_DP = 600
        const val MIN_THREE_PANE_WIDTH_DP = 1_000
        const val RAIL_WIDTH_DP = 104
        const val MIN_SPLINE_WIDTH_DP = 360
        const val MAX_SPLINE_WIDTH_DP = 560
        const val DETAIL_WIDTH_DP = 360
    }
}
