package com.riffle.app.launcher

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter

/**
 * Continuous horizontal drag-to-switch-page pager state, wrapping Compose Foundation's [PagerState]
 * the same way [rememberImmediateHomePagerState] does for Standard Home's own pages: external
 * selection (e.g. a spine chip tap) syncs in via `animateScrollToPage` when the pager is idle, and
 * the pager's own settled page reports back upstream once a user-driven drag/fling finishes.
 *
 * This is deliberately generic over what a "page" is -- [pageCount] and [selectedIndex] are plain
 * integers, and [onSettle] receives the settled index directly, rather than this function resolving
 * [com.riffle.core.domain.launcher.cards.AppStageId]s itself. The caller (which knows whether a given
 * index is a real app stage or a virtual page like "All notifications") decides what settling on it
 * means -- e.g. dispatching [LauncherShellAction.SelectAppStage] for a real stage's index, or just
 * updating local UI state for a virtual page's index.
 */
@Composable
internal fun rememberAdaptiveStageStagePagerState(
    pageCount: Int,
    selectedIndex: Int,
    reducedMotion: Boolean = false,
    onSettle: (Int) -> Unit,
): AdaptiveStageStagePagerState {
    val coercedSelectedIndex = selectedIndex.coerceAtLeast(0)
    val foundationPagerState =
        rememberPagerState(initialPage = coercedSelectedIndex) { pageCount.coerceAtLeast(1) }

    // Applies an externally-driven page selection (e.g. a spine chip tap) to the pager. Gated on
    // isScrollInProgress so it never fights a page the pager's own gesture is still mid-flight on --
    // the settle-effect below is what reports a user-driven page change back upstream, so by the time
    // this key combination changes again the pager and caller already agree and this is a no-op.
    LaunchedEffect(coercedSelectedIndex, pageCount, reducedMotion) {
        if (
            pageCount > 0 &&
            !foundationPagerState.isScrollInProgress &&
            foundationPagerState.currentPage != coercedSelectedIndex
        ) {
            foundationPagerState.animateScrollToPage(
                page = coercedSelectedIndex,
                animationSpec = adaptiveStageStageSettleAnimation(homePageSettleMotionPolicy(reducedMotion)),
            )
        }
    }

    val latestOnSettle = rememberUpdatedState(onSettle)
    val latestSelectedIndex = rememberUpdatedState(coercedSelectedIndex)

    // Reports the pager's own settled page upstream once a user-driven drag/fling finishes.
    LaunchedEffect(foundationPagerState) {
        snapshotFlow { foundationPagerState.isScrollInProgress }
            .filter { isScrollInProgress -> !isScrollInProgress }
            .collect {
                val settledIndex = foundationPagerState.currentPage
                if (settledIndex != latestSelectedIndex.value) {
                    latestOnSettle.value(settledIndex)
                }
            }
    }

    return AdaptiveStageStagePagerState(foundationPagerState)
}

internal class AdaptiveStageStagePagerState(
    val foundationPagerState: PagerState,
) {
    /** Continuous position (e.g. `1.35`) for consumers -- like the stage spine -- that animate off it. */
    val pagePosition: Float
        get() = foundationPagerState.currentPage + foundationPagerState.currentPageOffsetFraction

    val visualSelectedStageIndex: Int
        get() = foundationPagerState.currentPage

    val isStageGestureActive: Boolean
        get() = foundationPagerState.isScrollInProgress
}

/**
 * Same threshold/animation-spec helpers [ImmediateHomePager.kt]'s `ImmediateWorkspacePager` seeds its
 * own `PagerDefaults.flingBehavior` with, kept as an independent constant/function pair rather than
 * shared -- [homePageSettleMotionPolicy] and [REDUCED_MOTION_PAGE_SETTLE_DURATION_MILLIS] are the only
 * pieces actually reused across the two files.
 */
internal fun adaptiveStageStageSettleAnimation(policy: HomePageSettleMotionPolicy): AnimationSpec<Float> =
    when (policy) {
        HomePageSettleMotionPolicy.ReducedShortTween ->
            tween(
                durationMillis = REDUCED_MOTION_PAGE_SETTLE_DURATION_MILLIS,
                easing = LinearOutSlowInEasing,
            )

        HomePageSettleMotionPolicy.StandardSpring ->
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = 0.001f,
            )
    }

internal const val STAGE_CHANGE_DISTANCE_THRESHOLD = 0.22f
