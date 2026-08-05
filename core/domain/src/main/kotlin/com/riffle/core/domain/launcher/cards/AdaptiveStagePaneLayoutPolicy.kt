package com.riffle.core.domain.launcher.cards

import kotlin.math.roundToInt

/** Framework-independent window and separating-hinge inputs for the adaptive AdaptiveStage surface. */
data class AdaptiveStageWindowLayout(
    val widthDp: Int,
    val heightDp: Int,
    val safeStartDp: Int = 0,
    val safeTopDp: Int = 0,
    val safeEndDp: Int = 0,
    val safeBottomDp: Int = 0,
    val separatingHinges: List<AdaptiveStageHingeBounds> = emptyList(),
    val posture: AdaptiveStagePosture = AdaptiveStagePosture.UNKNOWN,
)

data class AdaptiveStageHingeBounds(
    val leftDp: Int,
    val topDp: Int,
    val rightDp: Int,
    val bottomDp: Int,
) {
    val isVertical: Boolean get() = heightDp >= widthDp
    val widthDp: Int get() = (rightDp - leftDp).coerceAtLeast(0)
    val heightDp: Int get() = (bottomDp - topDp).coerceAtLeast(0)
}

enum class AdaptiveStagePaneMode {
    COMPACT,
    TWO_PANE,
    THREE_PANE,

    /**
     * User-opted alternative to [COMPACT]: a top focus/detail region over a bottom card-stack
     * strip. Never produced from window geometry alone -- only when [AdaptiveStagePaneArrangement.SPLIT]
     * is explicitly requested and the window is at least as wide as a workable [COMPACT] surface.
     */
    SPLIT,
}

/**
 * User-selectable choice between the existing full-stack AdaptiveStage surface ([STACK], today's only
 * behavior) and the [SPLIT] top-detail/bottom-stack layout. This is independent of window
 * geometry -- [AdaptiveStagePaneLayoutPolicy] still decides [AdaptiveStagePaneMode.COMPACT] vs wider modes
 * from the window itself, and only promotes a would-be-[AdaptiveStagePaneMode.COMPACT] result to
 * [AdaptiveStagePaneMode.SPLIT] when [SPLIT] is requested and the window is workable.
 */
enum class AdaptiveStagePaneArrangement {
    STACK,
    SPLIT,
}

/**
 * Concrete pane sizes with bounded Stack and detail surfaces. A vertical separating hinge is a
 * real layout gap; callers must render content on either side rather than under it.
 */
data class AdaptiveStagePaneLayout(
    val mode: AdaptiveStagePaneMode,
    val railWidthDp: Int,
    val stackWidthDp: Int,
    val detailWidthDp: Int,
    val hingeGapDp: Int = 0,
    val leadingRegionWidthDp: Int = 0,
    val trailingRegionWidthDp: Int = 0,
    /** Empty leading-side space before a vertical separating hinge. */
    val leadingRemainderDp: Int = 0,
    val contentStartDp: Int = 0,
    val contentWidthDp: Int = 0,
    val contentTopDp: Int = 0,
    val contentHeightDp: Int = 0,
    /** [AdaptiveStagePaneMode.SPLIT]-only: height of the upper focus/detail region. */
    val upperRegionHeightDp: Int = 0,
    /** [AdaptiveStagePaneMode.SPLIT]-only: height of the lower card-stack + spine region. */
    val lowerRegionHeightDp: Int = 0,
    /** Non-zero only when the rail runs along [AdaptiveStageRailSide.TOP]/[AdaptiveStageRailSide.BOTTOM]. */
    val railHeightDp: Int = 0,
) {
    val showsRail: Boolean get() = mode == AdaptiveStagePaneMode.TWO_PANE || mode == AdaptiveStagePaneMode.THREE_PANE
    val showsDetailPane: Boolean get() = mode == AdaptiveStagePaneMode.THREE_PANE
}

/** Chooses AdaptiveStage panes from the current usable window, never a device-name classification. */
class AdaptiveStagePaneLayoutPolicy {
    /**
     * [arrangement] is a user preference, not a geometry input: [resolveStackLayout] below is run
     * exactly as before (byte-for-byte, for [AdaptiveStagePaneArrangement.STACK]) and only when
     * [AdaptiveStagePaneArrangement.SPLIT] is requested and the geometry-only result would have been
     * [AdaptiveStagePaneMode.COMPACT] do we promote it to [AdaptiveStagePaneMode.SPLIT] -- and only if the
     * window is workable (mirrors the same non-degenerate-bounds spirit as the compact-fallback
     * paths above, using a minimum content height instead of width since the split adds a second
     * vertical region). An unworkably small window keeps [AdaptiveStagePaneMode.COMPACT] even though
     * [AdaptiveStagePaneArrangement.SPLIT] was requested.
     */
    fun layoutFor(
        window: AdaptiveStageWindowLayout,
        railSide: AdaptiveStageRailSide = AdaptiveStageRailSide.LEADING,
        arrangement: AdaptiveStagePaneArrangement = AdaptiveStagePaneArrangement.STACK,
    ): AdaptiveStagePaneLayout {
        val stackLayout = resolveStackLayout(window, railSide).reserveHorizontalRail(railSide)
        val canSplit =
            arrangement == AdaptiveStagePaneArrangement.SPLIT &&
                stackLayout.mode == AdaptiveStagePaneMode.COMPACT &&
                stackLayout.contentWidthDp > 0 &&
                stackLayout.contentHeightDp >= MIN_SPLIT_CONTENT_HEIGHT_DP
        if (!canSplit) return stackLayout

        val upperHeight =
            (stackLayout.contentHeightDp * SPLIT_UPPER_REGION_RATIO)
                .roundToInt()
                .coerceIn(0, stackLayout.contentHeightDp)
        return stackLayout.copy(
            mode = AdaptiveStagePaneMode.SPLIT,
            upperRegionHeightDp = upperHeight,
            lowerRegionHeightDp = stackLayout.contentHeightDp - upperHeight,
        )
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "MaxLineLength", "ReturnCount")
    private fun resolveStackLayout(
        window: AdaptiveStageWindowLayout,
        railSide: AdaptiveStageRailSide,
    ): AdaptiveStagePaneLayout {
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

        // A half-open or tabletop device must remain a usable compact surface even when the
        // reported bounds are large. The docked-rail layout is reserved for a confirmed flat posture.
        if (window.posture.isCompactFallback()) {
            val useTrailingRegion = verticalHinge != null && trailingWidth > leadingWidth
            val compactWidth =
                if (verticalHinge == null) {
                    safeWidth
                } else if (useTrailingRegion) {
                    trailingWidth
                } else {
                    leadingWidth
                }
            return AdaptiveStagePaneLayout(
                mode = AdaptiveStagePaneMode.COMPACT,
                railWidthDp = 0,
                stackWidthDp = compactWidth,
                detailWidthDp = 0,
                hingeGapDp = 0,
                leadingRegionWidthDp = leadingWidth,
                trailingRegionWidthDp = trailingWidth,
                contentStartDp = if (useTrailingRegion) verticalHinge!!.rightDp - window.safeStartDp else 0,
                contentWidthDp = compactWidth,
                contentTopDp = contentTop,
                contentHeightDp = contentHeight,
            )
        }

        val leadingRailWidth = if (railSide == AdaptiveStageRailSide.LEADING) RAIL_WIDTH_DP else 0
        val trailingRailWidth = if (railSide == AdaptiveStageRailSide.TRAILING) RAIL_WIDTH_DP else 0
        val leadingRegionIsTooNarrow = leadingWidth < leadingRailWidth + MIN_STACK_WIDTH_DP
        val trailingRegionIsTooNarrow = trailingWidth < trailingRailWidth
        if (verticalHinge != null && (leadingRegionIsTooNarrow || trailingRegionIsTooNarrow)) {
            val useTrailingRegion = trailingWidth > leadingWidth
            val compactWidth = if (useTrailingRegion) trailingWidth else leadingWidth
            return AdaptiveStagePaneLayout(
                mode = AdaptiveStagePaneMode.COMPACT,
                railWidthDp = 0,
                stackWidthDp = compactWidth,
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

        val hasThreePaneLeadingRegion = leadingWidth >= leadingRailWidth + MIN_STACK_WIDTH_DP
        val hasThreePaneTrailingRegion = trailingWidth >= DETAIL_WIDTH_DP + trailingRailWidth
        if (verticalHinge != null && hasThreePaneLeadingRegion && hasThreePaneTrailingRegion) {
            val stackWidth = (leadingWidth - leadingRailWidth).coerceAtMost(MAX_STACK_WIDTH_DP)
            return AdaptiveStagePaneLayout(
                mode = AdaptiveStagePaneMode.THREE_PANE,
                railWidthDp = RAIL_WIDTH_DP,
                stackWidthDp = stackWidth,
                detailWidthDp = (trailingWidth - trailingRailWidth).coerceAtMost(DETAIL_WIDTH_DP),
                hingeGapDp = hingeGap,
                leadingRegionWidthDp = leadingWidth,
                trailingRegionWidthDp = trailingWidth,
                leadingRemainderDp =
                    (leadingWidth - leadingRailWidth - stackWidth).coerceAtLeast(0),
                contentWidthDp = safeWidth,
                contentTopDp = contentTop,
                contentHeightDp = contentHeight,
            )
        }

        if (verticalHinge != null) {
            val stackWidth = (leadingWidth - leadingRailWidth).coerceAtMost(MAX_STACK_WIDTH_DP)
            return AdaptiveStagePaneLayout(
                mode = AdaptiveStagePaneMode.TWO_PANE,
                railWidthDp = RAIL_WIDTH_DP,
                stackWidthDp = stackWidth,
                detailWidthDp = 0,
                hingeGapDp = hingeGap,
                leadingRegionWidthDp = leadingWidth,
                trailingRegionWidthDp = trailingWidth,
                leadingRemainderDp =
                    (leadingWidth - leadingRailWidth - stackWidth).coerceAtLeast(0),
                contentWidthDp = safeWidth,
                contentTopDp = contentTop,
                contentHeightDp = contentHeight,
            )
        }

        // A TOP/BOTTOM rail reserves height (see reserveHorizontalRail below), not width, so it
        // contributes nothing to the width math here -- unlike LEADING/TRAILING, which always
        // reserve RAIL_WIDTH_DP of width in this no-hinge layout regardless of which side it ends
        // up drawn on.
        val noHingeRailWidth = if (railSide.isHorizontalEdge) 0 else RAIL_WIDTH_DP
        return when {
            usableWidth < MIN_TWO_PANE_WIDTH_DP ->
                AdaptiveStagePaneLayout(
                    mode = AdaptiveStagePaneMode.COMPACT,
                    railWidthDp = 0,
                    stackWidthDp = usableWidth,
                    detailWidthDp = 0,
                    hingeGapDp = hingeGap,
                    leadingRegionWidthDp = leadingWidth,
                    trailingRegionWidthDp = trailingWidth,
                    contentWidthDp = safeWidth,
                    contentTopDp = contentTop,
                    contentHeightDp = contentHeight,
                )

            usableWidth < MIN_THREE_PANE_WIDTH_DP ->
                AdaptiveStagePaneLayout(
                    mode = AdaptiveStagePaneMode.TWO_PANE,
                    railWidthDp = noHingeRailWidth,
                    stackWidthDp =
                        (usableWidth - noHingeRailWidth).coerceIn(MIN_STACK_WIDTH_DP, MAX_STACK_WIDTH_DP),
                    detailWidthDp = 0,
                    hingeGapDp = hingeGap,
                    leadingRegionWidthDp = leadingWidth,
                    trailingRegionWidthDp = trailingWidth,
                    contentWidthDp = safeWidth,
                    contentTopDp = contentTop,
                    contentHeightDp = contentHeight,
                )

            else ->
                AdaptiveStagePaneLayout(
                    mode = AdaptiveStagePaneMode.THREE_PANE,
                    railWidthDp = noHingeRailWidth,
                    stackWidthDp =
                        (usableWidth - noHingeRailWidth - DETAIL_WIDTH_DP)
                            .coerceIn(MIN_STACK_WIDTH_DP, MAX_STACK_WIDTH_DP),
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
        const val MIN_STACK_WIDTH_DP = 360
        const val MAX_STACK_WIDTH_DP = 560
        const val DETAIL_WIDTH_DP = 360

        /**
         * The upper focus/detail region gets 60% of the available content height in
         * [AdaptiveStagePaneMode.SPLIT] -- roughly the upper 3/5, inside the requested 1/2-2/3 range and a
         * clean fit against the existing hinge-region math above, which also favors whichever side
         * gets the (implicitly larger) remainder rather than an even split.
         */
        const val SPLIT_UPPER_REGION_RATIO = 0.6f

        /**
         * A phone-width [AdaptiveStagePaneMode.COMPACT] window can still be too short to host a top
         * detail region plus a bottom stage pager and spine without either becoming unusably small.
         * 400dp comfortably fits a compact header (~64dp) + a minimal detail summary above a pager
         * and spine strip below; shorter than this, [AdaptiveStagePaneArrangement.SPLIT] falls back to
         * [AdaptiveStagePaneMode.COMPACT] rather than rendering a degenerate split.
         */
        const val MIN_SPLIT_CONTENT_HEIGHT_DP = 400
    }
}

/**
 * Height reserved for a [AdaptiveStageRailSide.TOP]/[AdaptiveStageRailSide.BOTTOM] rail, sized for a row of
 * [AdaptiveStageStageRailTile]-shaped tiles (icon + label) plus padding -- deliberately smaller than
 * [AdaptiveStagePaneLayoutPolicy]'s `RAIL_WIDTH_DP` column, since a horizontal strip only needs to fit
 * one tile's height rather than a label column beside it.
 */
private const val RAIL_HEIGHT_DP = 96

/**
 * When the rail runs along the top or bottom edge it consumes vertical rather than horizontal
 * space: reserve it from [AdaptiveStagePaneLayout.contentHeightDp] rather than touching any of the
 * width-based pane-mode math above (which stays exactly as before for
 * [AdaptiveStageRailSide.LEADING]/[AdaptiveStageRailSide.TRAILING]).
 *
 * [AdaptiveStagePaneLayout.contentTopDp] is deliberately left untouched here: it positions the whole
 * content [androidx.compose.foundation.layout.Box] -- which contains the rail itself, not just the
 * area below it -- within the window, mirroring how a horizontal separating hinge already shifts
 * that same offset. Adjusting it here would push the rail down along with everything else instead
 * of just reserving its height; the rail-then-content ordering inside that box (a plain
 * top-to-bottom [androidx.compose.foundation.layout.Column]) is what actually keeps the content
 * below the rail, no extra offset required.
 */
private fun AdaptiveStagePaneLayout.reserveHorizontalRail(railSide: AdaptiveStageRailSide): AdaptiveStagePaneLayout {
    if (!railSide.isHorizontalEdge || !showsRail) return this
    val reservedHeight = RAIL_HEIGHT_DP.coerceAtMost(contentHeightDp)
    return copy(
        railWidthDp = 0,
        railHeightDp = reservedHeight,
        contentHeightDp = contentHeightDp - reservedHeight,
    )
}

private fun AdaptiveStagePosture.isCompactFallback(): Boolean =
    when (this) {
        AdaptiveStagePosture.UNKNOWN,
        AdaptiveStagePosture.COMPACT,
        AdaptiveStagePosture.PARTIALLY_FOLDED,
        AdaptiveStagePosture.TABLETOP,
        -> true
        AdaptiveStagePosture.UNFOLDED -> false
    }
