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
    val leadingRegionWidthDp: Int = 0,
    val trailingRegionWidthDp: Int = 0,
    val contentStartDp: Int = 0,
    val contentWidthDp: Int = 0,
    val contentTopDp: Int = 0,
    val contentHeightDp: Int = 0,
) {
    val showsRail: Boolean get() = mode != TimeScapePaneMode.COMPACT
    val showsDetailPane: Boolean get() = mode == TimeScapePaneMode.THREE_PANE
}

/** Chooses TimeScape panes from the current usable window, never a device-name classification. */
class TimeScapePaneLayoutPolicy {
    @Suppress("CyclomaticComplexMethod", "LongMethod", "MaxLineLength", "ReturnCount")
    fun layoutFor(window: TimeScapeWindowLayout): TimeScapePaneLayout {
        val safeWidth = (window.widthDp - window.safeStartDp - window.safeEndDp).coerceAtLeast(0)
        val safeHeight = (window.heightDp - window.safeTopDp - window.safeBottomDp).coerceAtLeast(0)
        val verticalHinge =
            window.separatingHinges.firstOrNull { hinge ->
                hinge.isVertical && hinge.rightDp > window.safeStartDp && hinge.leftDp < window.widthDp - window.safeEndDp
            }
        val horizontalHinge =
            window.separatingHinges.firstOrNull { hinge ->
                !hinge.isVertical && hinge.bottomDp > window.safeTopDp && hinge.topDp < window.heightDp - window.safeBottomDp
            }
        val hingeGap = verticalHinge?.widthDp ?: 0
        val usableWidth = (safeWidth - hingeGap).coerceAtLeast(0)
        val leadingWidth =
            verticalHinge?.let { (it.leftDp - window.safeStartDp).coerceIn(0, safeWidth) } ?: usableWidth
        val trailingWidth =
            verticalHinge?.let { (window.widthDp - window.safeEndDp - it.rightDp).coerceIn(0, safeWidth) } ?: 0
        val topRegionHeight = horizontalHinge?.let { (it.topDp - window.safeTopDp).coerceIn(0, safeHeight) }
        val bottomRegionHeight =
            horizontalHinge?.let { (window.heightDp - window.safeBottomDp - it.bottomDp).coerceIn(0, safeHeight) }
        val useBottomRegion = horizontalHinge != null && (bottomRegionHeight ?: 0) > (topRegionHeight ?: 0)
        val contentTop = if (useBottomRegion) horizontalHinge!!.bottomDp - window.safeTopDp else 0
        val contentHeight =
            when {
                horizontalHinge == null -> safeHeight
                useBottomRegion -> bottomRegionHeight ?: 0
                else -> topRegionHeight ?: 0
            }

        if (verticalHinge != null && leadingWidth < RAIL_WIDTH_DP + MIN_SPLINE_WIDTH_DP) {
            val useTrailingRegion = trailingWidth > leadingWidth
            val compactWidth = if (useTrailingRegion) trailingWidth else leadingWidth
            return TimeScapePaneLayout(
                mode = TimeScapePaneMode.COMPACT,
                railWidthDp = 0,
                splineWidthDp = compactWidth,
                detailWidthDp = 0,
                hingeGapDp = 0,
                leadingRegionWidthDp = leadingWidth,
                trailingRegionWidthDp = trailingWidth,
                contentStartDp = if (useTrailingRegion) verticalHinge.rightDp - window.safeStartDp else 0,
                contentWidthDp = compactWidth,
                contentTopDp = contentTop,
                contentHeightDp = contentHeight,
            )
        }

        if (verticalHinge != null && leadingWidth >= RAIL_WIDTH_DP + MIN_SPLINE_WIDTH_DP && trailingWidth >= DETAIL_WIDTH_DP) {
            return TimeScapePaneLayout(
                mode = TimeScapePaneMode.THREE_PANE,
                railWidthDp = RAIL_WIDTH_DP,
                splineWidthDp = (leadingWidth - RAIL_WIDTH_DP).coerceAtMost(MAX_SPLINE_WIDTH_DP),
                detailWidthDp = trailingWidth.coerceAtMost(DETAIL_WIDTH_DP),
                hingeGapDp = hingeGap,
                leadingRegionWidthDp = leadingWidth,
                trailingRegionWidthDp = trailingWidth,
                contentWidthDp = safeWidth,
                contentTopDp = contentTop,
                contentHeightDp = contentHeight,
            )
        }

        if (verticalHinge != null) {
            return TimeScapePaneLayout(
                mode = TimeScapePaneMode.TWO_PANE,
                railWidthDp = RAIL_WIDTH_DP,
                splineWidthDp = (leadingWidth - RAIL_WIDTH_DP).coerceAtMost(MAX_SPLINE_WIDTH_DP),
                detailWidthDp = 0,
                hingeGapDp = hingeGap,
                leadingRegionWidthDp = leadingWidth,
                trailingRegionWidthDp = trailingWidth,
                contentWidthDp = safeWidth,
                contentTopDp = contentTop,
                contentHeightDp = contentHeight,
            )
        }

        return when {
            usableWidth < MIN_TWO_PANE_WIDTH_DP ->
                TimeScapePaneLayout(
                    TimeScapePaneMode.COMPACT,
                    0,
                    usableWidth,
                    0,
                    hingeGap,
                    leadingWidth,
                    trailingWidth,
                    0,
                    safeWidth,
                    contentTop,
                    contentHeight,
                )

            usableWidth < MIN_THREE_PANE_WIDTH_DP ->
                TimeScapePaneLayout(
                    mode = TimeScapePaneMode.TWO_PANE,
                    railWidthDp = RAIL_WIDTH_DP,
                    splineWidthDp = (usableWidth - RAIL_WIDTH_DP).coerceIn(MIN_SPLINE_WIDTH_DP, MAX_SPLINE_WIDTH_DP),
                    detailWidthDp = 0,
                    hingeGapDp = hingeGap,
                    leadingRegionWidthDp = leadingWidth,
                    trailingRegionWidthDp = trailingWidth,
                    contentWidthDp = safeWidth,
                    contentTopDp = contentTop,
                    contentHeightDp = contentHeight,
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
                    leadingRegionWidthDp = leadingWidth,
                    trailingRegionWidthDp = trailingWidth,
                    contentWidthDp = safeWidth,
                    contentTopDp = contentTop,
                    contentHeightDp = contentHeight,
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
