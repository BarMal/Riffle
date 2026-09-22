package com.riffle.app.launcher

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter

@Composable
internal fun rememberImmediateHomePagerState(
    layout: HomeLayout,
    reducedMotion: Boolean = false,
    actions: HomeWorkspaceActions,
): ImmediateHomePagerState {
    val selectedPageIndex = layout.selectedPageIndex.coerceIn(0, layout.lastPageIndex)
    val pageCount = layout.pages.size
    val foundationPagerState =
        rememberPagerState(initialPage = selectedPageIndex) { pageCount.coerceAtLeast(1) }
    val pagerState = remember(foundationPagerState) { ImmediateHomePagerState(foundationPagerState) }

    // Applies an externally-driven page selection (e.g. a PageIndicator tap) to the pager. Gated on
    // isScrollInProgress so it never fights a page the pager's own gesture is still mid-flight on --
    // the settle-effect below is what reports a user-driven page change back upstream, so by the time
    // this key combination changes again the pager and caller already agree and this is a no-op.
    //
    // selectedPageIndex only changes here through a genuine commit -- the scrub's own onPageSelected
    // included -- so it is also a reliable point to drop any uncommitted-scrub marker the settle
    // effect below is still holding: that effect only clears it when it happens to observe the
    // matching isScrollInProgress transition, which a fast run of instant scrubToPage jumps can
    // coalesce away before it ever gets the chance to. Clearing it here too bounds how long a stale
    // marker could otherwise survive to a single genuine commit, rather than indefinitely.
    LaunchedEffect(selectedPageIndex, pageCount, reducedMotion) {
        pagerState.lastUncommittedScrubPage = null
        if (
            pageCount > 0 &&
            !foundationPagerState.isScrollInProgress &&
            foundationPagerState.currentPage != selectedPageIndex
        ) {
            foundationPagerState.animateScrollToPage(
                page = selectedPageIndex,
                animationSpec = homePageSettleAnimation(homePageSettleMotionPolicy(reducedMotion)),
            )
        }
    }

    val latestPages = rememberUpdatedState(layout.pages)
    val latestSelectedPageId = rememberUpdatedState(layout.selectedPageId)
    val latestOnAction = rememberUpdatedState(actions.onAction)

    // Reports the pager's own settled page upstream once a user-driven drag/fling finishes.
    //
    // snapToPage's instant jumps also flip isScrollInProgress false→true→false around themselves
    // (they sit on the same scroll machinery a real drag does), so this would otherwise fire once
    // per page a live indicator scrub crosses -- each one dispatching SelectHomePage, and with it a
    // home-layout disk write, for a page the drag hasn't even committed to yet. lastUncommittedScrubPage
    // is how a scrub's own jumps are told apart from an actual user gesture settling: it names the
    // page snapToPage most recently moved to without a matching commit, and is cleared the moment
    // this effect has used it once, so a later real settle on that same page still reports normally.
    LaunchedEffect(foundationPagerState) {
        snapshotFlow { foundationPagerState.isScrollInProgress }
            .filter { isScrollInProgress -> !isScrollInProgress }
            .collect {
                val currentPage = foundationPagerState.currentPage
                val wasUncommittedScrubJump = pagerState.lastUncommittedScrubPage == currentPage
                pagerState.lastUncommittedScrubPage = null
                if (wasUncommittedScrubJump) return@collect
                latestPages.value
                    .getOrNull(currentPage)
                    ?.id
                    ?.takeIf { pageId -> pageId != latestSelectedPageId.value }
                    ?.let { pageId -> latestOnAction.value(LauncherShellAction.SelectHomePage(pageId)) }
            }
    }

    return pagerState
}

internal class ImmediateHomePagerState(
    val foundationPagerState: PagerState,
) {
    val visualSelectedPageIndex: Int
        get() = foundationPagerState.currentPage

    val isPageGestureActive: Boolean
        get() = foundationPagerState.isScrollInProgress

    /**
     * The page [snapToPage] most recently moved to without [rememberImmediateHomePagerState]'s own
     * settle-report effect having seen it yet -- how that effect tells its own scrub-driven jumps
     * apart from a real user gesture settling. Null once nothing is waiting to be told apart.
     */
    internal var lastUncommittedScrubPage: Int? = null

    /**
     * Jumps the pager straight to [index] with no animation, for a caller that is already
     * animating its own handle for the transition (the page-indicator scrub drag) and would
     * otherwise fight [rememberImmediateHomePagerState]'s own settle animation for the same move.
     *
     * A later call preempts an earlier one still in flight -- the scroll APIs this sits on already
     * arbitrate concurrent scrolls on the same [PagerState], so a fast drag across several pages
     * just lands on whichever index was requested last.
     */
    suspend fun snapToPage(index: Int) {
        lastUncommittedScrubPage = index
        foundationPagerState.scrollToPage(index)
    }
}

@Suppress("LongParameterList", "CyclomaticComplexMethod")
@Composable
internal fun ImmediateWorkspacePager(
    layout: HomeLayout,
    pagerState: ImmediateHomePagerState,
    gridState: HomeGridState,
    presentation: HomeGridPresentation,
    appIconLoader: AppIconLoader,
    actions: HomeWorkspaceActions,
    activeDragSession: HomeDragSession? = null,
    onDragPageTargetChanged: (com.riffle.core.domain.launcher.home.LauncherPageId) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val pageWidthPx = with(LocalDensity.current) { maxWidth.toPx() }

        val sourcePageIndex =
            activeDragSession?.let { session -> layout.pages.indexOfFirst { it.id == session.originPageId } }
                ?: -1
        val nextTargetIndex =
            if (activeDragSession != null && sourcePageIndex >= 0) {
                when {
                    activeDragSession.dragOffsetX <= -(pageWidthPx * DRAG_PAGE_EDGE_FRACTION) -> sourcePageIndex + 1
                    activeDragSession.dragOffsetX >= pageWidthPx * DRAG_PAGE_EDGE_FRACTION -> sourcePageIndex - 1
                    else -> -1
                }
            } else {
                -1
            }
        LaunchedEffect(activeDragSession?.originPageId, activeDragSession?.targetPageId, nextTargetIndex) {
            if (activeDragSession != null &&
                activeDragSession.targetPageId == activeDragSession.originPageId &&
                nextTargetIndex in layout.pages.indices
            ) {
                delay(DRAG_PAGE_EDGE_HOVER_MILLIS)
                layout.pages.getOrNull(nextTargetIndex)?.let { page -> onDragPageTargetChanged(page.id) }
            }
        }

        HorizontalPager(
            state = pagerState.foundationPagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = layout.pages.size > 1 && activeDragSession == null,
            flingBehavior =
                PagerDefaults.flingBehavior(
                    state = pagerState.foundationPagerState,
                    snapAnimationSpec = homePageSettleAnimation(homePageSettleMotionPolicy(presentation.reducedMotion)),
                    snapPositionalThreshold = PAGE_CHANGE_DISTANCE_THRESHOLD,
                ),
            key = { index -> layout.pages.getOrNull(index)?.id?.value ?: index },
        ) { index ->
            val page = layout.pages.getOrNull(index) ?: return@HorizontalPager
            val pageModifier = Modifier.fillMaxSize().clipToBounds()
            if (page.isNotificationCardsPage) {
                GeneratedNotificationCardsPage(
                    groups = presentation.generatedPage.notificationGroupsByApp,
                    notificationAccessStatus = presentation.generatedPage.notificationAccessStatus,
                    apps = presentation.generatedPage.installedApps,
                    onAction = presentation.generatedPage.onAction,
                    reducedMotion = presentation.reducedMotion,
                    adaptiveStageAppearance = presentation.generatedPage.adaptiveStageAppearance,
                    haptics = actions.haptics,
                    appIconLoader = appIconLoader,
                    modifier = pageModifier,
                )
            } else {
                WorkspaceGrid(
                    page = page,
                    gridState = gridState,
                    presentation = presentation,
                    appIconLoader = appIconLoader,
                    actions = actions,
                    modifier = pageModifier,
                )
            }
        }
    }
}

private val LauncherPage.isNotificationCardsPage: Boolean
    get() = (type as? LauncherPageType.Generated)?.kind == GeneratedLauncherPageKind.NOTIFICATION_CARDS

internal fun homePageSettleMotionPolicy(reducedMotion: Boolean): HomePageSettleMotionPolicy =
    if (reducedMotion) {
        HomePageSettleMotionPolicy.ReducedShortTween
    } else {
        HomePageSettleMotionPolicy.StandardSpring
    }

private fun homePageSettleAnimation(policy: HomePageSettleMotionPolicy): AnimationSpec<Float> =
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

private val HomeLayout.lastPageIndex: Int
    get() = pages.lastIndex.coerceAtLeast(0)

internal enum class HomePageSettleMotionPolicy {
    StandardSpring,
    ReducedShortTween,
}

internal const val REDUCED_MOTION_PAGE_SETTLE_DURATION_MILLIS = 80

private const val PAGE_CHANGE_DISTANCE_THRESHOLD = 0.22f
private const val DRAG_PAGE_EDGE_FRACTION = 0.42f
private const val DRAG_PAGE_EDGE_HOVER_MILLIS = 180L
