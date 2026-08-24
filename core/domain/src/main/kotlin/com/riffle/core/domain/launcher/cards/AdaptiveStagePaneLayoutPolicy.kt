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
) {
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
        arrangement: AdaptiveStagePaneArrangement = AdaptiveStagePaneArrangement.STACK,
    ): AdaptiveStagePaneLayout {
        val stackLayout = resolveStackLayout(window)
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
    private fun resolveStackLayout(window: AdaptiveStageWindowLayout): AdaptiveStagePaneLayout {
        val safeWidth = (window.widthDp - window.safeStartDp - window.safeEndDp).coerceAtLeast(0)
        val safeHeight = (window.heightDp - window.safeTopDp - window.safeBottomDp).coerceAtLeast(0)
        val verticalHinge =
            window.separatingHinges.firstOrNull { hinge ->
                hinge.isVertical &&
                    hinge.rightDp > window.safeStartDp &&
                    hinge.leftDp < window.widthDp - window.safeEndDp
            }
        val horizontalHinge =
            window.separatingHinges.firstOrNull { hinge ->
                !hinge.isVertical &&
                    hinge.bottomDp > window.safeTopDp &&
                    hinge.topDp < window.heightDp - window.safeBottomDp
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
        // reported bounds are large. The multi-pane layout is reserved for a confirmed flat posture.
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

        val leadingRegionIsTooNarrow = leadingWidth < MIN_STACK_WIDTH_DP
        if (verticalHinge != null && leadingRegionIsTooNarrow) {
            val useTrailingRegion = trailingWidth > leadingWidth
            val compactWidth = if (useTrailingRegion) trailingWidth else leadingWidth
            return AdaptiveStagePaneLayout(
                mode = AdaptiveStagePaneMode.COMPACT,
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

        val hasThreePaneLeadingRegion = leadingWidth >= MIN_STACK_WIDTH_DP
        val hasThreePaneTrailingRegion = trailingWidth >= DETAIL_WIDTH_DP
        if (verticalHinge != null && hasThreePaneLeadingRegion && hasThreePaneTrailingRegion) {
            val stackWidth = leadingWidth.coerceAtMost(MAX_STACK_WIDTH_DP)
            return AdaptiveStagePaneLayout(
                mode = AdaptiveStagePaneMode.THREE_PANE,
                stackWidthDp = stackWidth,
                detailWidthDp = trailingWidth.coerceAtMost(DETAIL_WIDTH_DP),
                hingeGapDp = hingeGap,
                leadingRegionWidthDp = leadingWidth,
                trailingRegionWidthDp = trailingWidth,
                leadingRemainderDp = (leadingWidth - stackWidth).coerceAtLeast(0),
                contentWidthDp = safeWidth,
                contentTopDp = contentTop,
                contentHeightDp = contentHeight,
            )
        }

        if (verticalHinge != null) {
            val stackWidth = leadingWidth.coerceAtMost(MAX_STACK_WIDTH_DP)
            return AdaptiveStagePaneLayout(
                mode = AdaptiveStagePaneMode.TWO_PANE,
                stackWidthDp = stackWidth,
                detailWidthDp = 0,
                hingeGapDp = hingeGap,
                leadingRegionWidthDp = leadingWidth,
                trailingRegionWidthDp = trailingWidth,
                leadingRemainderDp = (leadingWidth - stackWidth).coerceAtLeast(0),
                contentWidthDp = safeWidth,
                contentTopDp = contentTop,
                contentHeightDp = contentHeight,
            )
        }

        return when {
            usableWidth < MIN_TWO_PANE_WIDTH_DP ->
                AdaptiveStagePaneLayout(
                    mode = AdaptiveStagePaneMode.COMPACT,
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
                    stackWidthDp = usableWidth.coerceIn(MIN_STACK_WIDTH_DP, MAX_UNFOLDED_STACK_WIDTH_DP),
                    detailWidthDp = 0,
                    hingeGapDp = hingeGap,
                    leadingRegionWidthDp = leadingWidth,
                    trailingRegionWidthDp = trailingWidth,
                    contentWidthDp = safeWidth,
                    contentTopDp = contentTop,
                    contentHeightDp = contentHeight,
                )

            else -> {
                // Unlike the separating-hinge branches above -- where the stack is flush against a
                // real physical crease and DETAIL_WIDTH_DP/MAX_STACK_WIDTH_DP's compact-sized caps
                // are the right fit -- there's no hinge here to anchor against, so both panes should
                // grow with the window instead of leaving most of a wide (unfolded/tablet/desktop)
                // window's width unused. The detail pane takes a fixed share of the usable width and
                // the stack takes the rest, each still bounded so neither becomes unreadably wide on
                // an ultra-wide window.
                val detailWidth =
                    (usableWidth * UNFOLDED_DETAIL_WIDTH_RATIO).roundToInt()
                        .coerceIn(DETAIL_WIDTH_DP, MAX_UNFOLDED_DETAIL_WIDTH_DP)
                val stackWidth = (usableWidth - detailWidth).coerceIn(MIN_STACK_WIDTH_DP, MAX_UNFOLDED_STACK_WIDTH_DP)
                AdaptiveStagePaneLayout(
                    mode = AdaptiveStagePaneMode.THREE_PANE,
                    stackWidthDp = stackWidth,
                    detailWidthDp = detailWidth,
                    hingeGapDp = hingeGap,
                    leadingRegionWidthDp = leadingWidth,
                    trailingRegionWidthDp = trailingWidth,
                    contentWidthDp = safeWidth,
                    contentTopDp = contentTop,
                    contentHeightDp = contentHeight,
                )
            }
        }
    }

    private companion object {
        const val MIN_TWO_PANE_WIDTH_DP = 600
        const val MIN_THREE_PANE_WIDTH_DP = 1_000
        const val MIN_STACK_WIDTH_DP = 360
        const val MAX_STACK_WIDTH_DP = 560
        const val DETAIL_WIDTH_DP = 360

        /**
         * Caps for the no-separating-hinge TWO_PANE/THREE_PANE branches only (see [resolveStackLayout]).
         * [MAX_STACK_WIDTH_DP]/[DETAIL_WIDTH_DP] stay compact-sized on purpose for the hinge branches,
         * where the stack is flush against a real physical crease; without a hinge there's nothing to
         * anchor against, so both panes instead grow with the window, bounded by these larger caps
         * rather than left mostly unused on a wide (unfolded/tablet/desktop) window (#1172).
         */
        const val MAX_UNFOLDED_STACK_WIDTH_DP = 960
        const val MAX_UNFOLDED_DETAIL_WIDTH_DP = 560
        const val UNFOLDED_DETAIL_WIDTH_RATIO = 0.32f

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

private fun AdaptiveStagePosture.isCompactFallback(): Boolean =
    when (this) {
        AdaptiveStagePosture.UNKNOWN,
        AdaptiveStagePosture.COMPACT,
        AdaptiveStagePosture.PARTIALLY_FOLDED,
        AdaptiveStagePosture.TABLETOP,
        -> true
        AdaptiveStagePosture.UNFOLDED -> false
    }
