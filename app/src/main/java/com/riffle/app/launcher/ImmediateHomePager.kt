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
import kotlin.math.abs
import kotlin.math.roundToInt

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

    // Applies an externally-driven page selection (e.g. a PageIndicator tap) to the pager. Gated on
    // isScrollInProgress so it never fights a page the pager's own gesture is still mid-flight on --
    // the settle-effect below is what reports a user-driven page change back upstream, so by the time
    // this key combination changes again the pager and caller already agree and this is a no-op.
    LaunchedEffect(selectedPageIndex, pageCount, reducedMotion) {
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
    LaunchedEffect(foundationPagerState) {
        snapshotFlow { foundationPagerState.isScrollInProgress }
            .filter { isScrollInProgress -> !isScrollInProgress }
            .collect {
                latestPages.value
                    .getOrNull(foundationPagerState.currentPage)
                    ?.id
                    ?.takeIf { pageId -> pageId != latestSelectedPageId.value }
                    ?.let { pageId -> latestOnAction.value(LauncherShellAction.SelectHomePage(pageId)) }
            }
    }

    return ImmediateHomePagerState(foundationPagerState)
}

internal class ImmediateHomePagerState(
    val foundationPagerState: PagerState,
) {
    val visualSelectedPageIndex: Int
        get() = foundationPagerState.currentPage

    val isPageGestureActive: Boolean
        get() = foundationPagerState.isScrollInProgress
}

@Suppress("LongParameterList")
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

/**
 * Settle-target arithmetic shared with [AdaptiveStageStagePagerState]'s own hand-rolled drag, which
 * hasn't migrated to a Foundation primitive yet -- kept here rather than duplicated per
 * [AdaptiveStageStagePager.kt]'s doc comments on the functions below.
 */
internal fun pageSettleTargetIndex(
    startPagePosition: Float,
    releasedPagePosition: Float,
    horizontalDragPx: Float,
    pageWidthPx: Float,
    horizontalVelocityPxPerSecond: Float,
    pageCount: Int,
): Int {
    val draggedPageFraction = abs(horizontalDragPx) / pageWidthPx.coerceAtLeast(1f)
    val startPageIndex = startPagePosition.roundToInt()
    val hasMeaningfulLeftFling =
        horizontalDragPx < 0f &&
            horizontalVelocityPxPerSecond <= -PAGE_FLING_VELOCITY_THRESHOLD_PX_PER_SECOND
    val hasMeaningfulRightFling =
        horizontalDragPx > 0f &&
            horizontalVelocityPxPerSecond >= PAGE_FLING_VELOCITY_THRESHOLD_PX_PER_SECOND

    return when {
        horizontalDragPx < 0f && draggedPageFraction >= PAGE_CHANGE_DISTANCE_THRESHOLD -> startPageIndex + 1

        horizontalDragPx > 0f && draggedPageFraction >= PAGE_CHANGE_DISTANCE_THRESHOLD -> startPageIndex - 1

        hasMeaningfulLeftFling -> startPageIndex + 1

        hasMeaningfulRightFling -> startPageIndex - 1

        else -> releasedPagePosition.roundToInt()
    }.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
}

internal fun shouldApplyExternalHomePageSelection(
    isDragging: Boolean,
    isSettling: Boolean,
    hasPendingGestureTarget: Boolean,
    pageCount: Int,
    currentPagePosition: Float,
    selectedPageIndex: Int,
): Boolean =
    !isDragging &&
        !isSettling &&
        !hasPendingGestureTarget &&
        pageCount > 0 &&
        currentPagePosition != selectedPageIndex.toFloat()

internal fun homePageExternalSelectionSettlePolicy(reducedMotion: Boolean): HomePageExternalSelectionSettlePolicy =
    when (homePageSettleMotionPolicy(reducedMotion)) {
        HomePageSettleMotionPolicy.StandardSpring,
        HomePageSettleMotionPolicy.ReducedShortTween,
        -> HomePageExternalSelectionSettlePolicy.AnimatedSettle
    }

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

internal enum class HomePageExternalSelectionSettlePolicy {
    AnimatedSettle,
    ImmediateSnap,
}

internal const val REDUCED_MOTION_PAGE_SETTLE_DURATION_MILLIS = 80

private const val PAGE_CHANGE_DISTANCE_THRESHOLD = 0.22f
private const val PAGE_FLING_VELOCITY_THRESHOLD_PX_PER_SECOND = 900f
private const val DRAG_PAGE_EDGE_FRACTION = 0.42f
private const val DRAG_PAGE_EDGE_HOVER_MILLIS = 180L
