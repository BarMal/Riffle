package com.riffle.app.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageType
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun rememberImmediateHomePagerState(
    layout: HomeLayout,
    reducedMotion: Boolean = false,
    actions: HomeWorkspaceActions,
): ImmediateHomePagerState {
    val selectedPageIndex = layout.selectedPageIndex.coerceIn(0, layout.lastPageIndex)
    val dragPagePosition = remember { mutableFloatStateOf(selectedPageIndex.toFloat()) }
    val settlePagePosition = remember { Animatable(selectedPageIndex.toFloat()) }
    val isDragging = remember { mutableStateOf(false) }
    val isSettling = remember { mutableStateOf(false) }
    val pendingGestureTargetPageIndex = remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(selectedPageIndex, layout.pages.size, isDragging.value, pendingGestureTargetPageIndex.value) {
        if (pendingGestureTargetPageIndex.value == selectedPageIndex) {
            pendingGestureTargetPageIndex.value = null
        }

        val shouldApplyExternalPageSelection =
            shouldApplyExternalHomePageSelection(
                isDragging = isDragging.value,
                isSettling = isSettling.value,
                hasPendingGestureTarget = pendingGestureTargetPageIndex.value != null,
                pageCount = layout.pages.size,
                currentPagePosition = dragPagePosition.floatValue,
                selectedPageIndex = selectedPageIndex,
            )

        if (shouldApplyExternalPageSelection) {
            when (homePageExternalSelectionSettlePolicy(reducedMotion)) {
                HomePageExternalSelectionSettlePolicy.ImmediateSnap -> {
                    val targetPagePosition = selectedPageIndex.toFloat()
                    dragPagePosition.floatValue = targetPagePosition
                    settlePagePosition.snapTo(targetPagePosition)
                }

                HomePageExternalSelectionSettlePolicy.AnimatedSettle -> {
                    isSettling.value = true
                    try {
                        settlePagePosition.snapTo(dragPagePosition.floatValue)
                        settlePagePosition.animateTo(
                            targetValue = selectedPageIndex.toFloat(),
                            animationSpec = homePageSettleAnimation(homePageSettleMotionPolicy(reducedMotion)),
                        ) {
                            dragPagePosition.floatValue = value
                        }
                        dragPagePosition.floatValue = selectedPageIndex.toFloat()
                    } finally {
                        isSettling.value = false
                    }
                }
            }
        }
    }

    return ImmediateHomePagerState(
        pagePositionState = dragPagePosition,
        settlePagePosition = settlePagePosition,
        isSettling = isSettling,
        isDragging = isDragging,
        onDragStarted = {
            isDragging.value = true
            isSettling.value = false
        },
        onTargetPageSettling = { targetIndex ->
            pendingGestureTargetPageIndex.value =
                when (layout.pages.getOrNull(targetIndex)?.id) {
                    layout.selectedPageId -> null
                    null -> null
                    else -> targetIndex
                }
        },
        onDragStopped = { targetIndex ->
            val targetPageId = layout.pages.getOrNull(targetIndex)?.id
            pendingGestureTargetPageIndex.value =
                when (targetPageId) {
                    layout.selectedPageId -> null
                    null -> null
                    else -> targetIndex
                }

            targetPageId
                ?.takeIf { pageId -> pageId != layout.selectedPageId }
                ?.let { pageId ->
                    actions.onAction(LauncherShellAction.SelectHomePage(pageId))
                }
            isDragging.value = false
        },
    )
}

internal class ImmediateHomePagerState(
    private val pagePositionState: MutableFloatState,
    private val settlePagePosition: Animatable<Float, *>,
    private val isSettling: MutableState<Boolean>,
    private val isDragging: MutableState<Boolean>,
    val onDragStarted: () -> Unit,
    val onTargetPageSettling: (Int) -> Unit,
    val onDragStopped: (Int) -> Unit,
) {
    val pagePosition: Float
        get() = pagePositionState.floatValue

    val visualSelectedPageIndex: Int
        get() = pagePosition.roundToInt()

    val isPageGestureActive: Boolean
        get() = isDragging.value || isSettling.value

    suspend fun stopSettling() {
        settlePagePosition.stop()
        isSettling.value = false
    }

    fun snapTo(pagePosition: Float) {
        pagePositionState.floatValue = pagePosition
    }

    suspend fun animateToPage(
        targetPagePosition: Float,
        initialVelocity: Float,
        reducedMotion: Boolean,
    ) {
        isSettling.value = true
        try {
            settlePagePosition.snapTo(pagePositionState.floatValue)
            settlePagePosition.animateTo(
                targetValue = targetPagePosition,
                animationSpec = homePageSettleAnimation(homePageSettleMotionPolicy(reducedMotion)),
                initialVelocity = initialVelocity,
            ) {
                pagePositionState.floatValue = value
            }
            pagePositionState.floatValue = targetPagePosition
        } finally {
            isSettling.value = false
        }
    }
}

@Composable
internal fun ImmediateWorkspacePager(
    layout: HomeLayout,
    pagerState: ImmediateHomePagerState,
    gridState: HomeGridState,
    presentation: HomeGridPresentation,
    appIconLoader: AppIconLoader,
    actions: HomeWorkspaceActions,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val pageWidth = maxWidth * PAGE_WIDTH_FRACTION
        val pageWidthPx = with(LocalDensity.current) { pageWidth.toPx() }
        val pageCenterOffsetPx = with(LocalDensity.current) { ((maxWidth - pageWidth) / 2).toPx() }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .immediateHomePageDrag(
                        enabled = layout.pages.size > 1,
                        pageWidthPx = pageWidthPx,
                        layout = layout,
                        pagerState = pagerState,
                        reducedMotion = presentation.reducedMotion,
                        launchPageMotion = { action ->
                            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { action() }
                        },
                    ),
        ) {
            layout.pages.forEachIndexed { index, page ->
                val pageModifier =
                    Modifier
                        .width(pageWidth)
                        .fillMaxHeight()
                        .graphicsLayer {
                            translationX = pageCenterOffsetPx + ((index - pagerState.pagePosition) * pageWidthPx)
                            clip = true
                        }
                if (page.isNotificationCardsPage) {
                    GeneratedNotificationCardsPage(
                        groups = presentation.generatedPage.notificationGroupsByApp,
                        notificationAccessStatus = presentation.generatedPage.notificationAccessStatus,
                        apps = presentation.generatedPage.installedApps,
                        onAction = presentation.generatedPage.onAction,
                        reducedMotion = presentation.reducedMotion,
                        timeScapeAppearance = presentation.generatedPage.timeScapeAppearance,
                        haptics = actions.haptics,
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
}

private val LauncherPage.isNotificationCardsPage: Boolean
    get() = (type as? LauncherPageType.Generated)?.kind == GeneratedLauncherPageKind.NOTIFICATION_CARDS

private fun Modifier.immediateHomePageDrag(
    enabled: Boolean,
    pageWidthPx: Float,
    layout: HomeLayout,
    pagerState: ImmediateHomePagerState,
    reducedMotion: Boolean,
    launchPageMotion: (suspend () -> Unit) -> Unit,
): Modifier =
    if (!enabled) {
        this
    } else {
        val pageIdsKey = layout.pages.joinToString(separator = "|") { page -> page.id.value }
        pointerInput(pageWidthPx, layout.selectedPageId, pageIdsKey) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (pageWidthPx <= 0f) {
                    return@awaitEachGesture
                }

                launchPageMotion { pagerState.stopSettling() }
                val velocityTracker = VelocityTracker()
                velocityTracker.addPosition(down.uptimeMillis, down.position)

                val startPagePosition = pagerState.pagePosition
                var dragX = 0f
                var dragY = 0f
                var isPageDrag = false
                var pointerIsDown = true

                while (pointerIsDown) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { pointer -> pointer.id == down.id }
                    if (change == null || !change.pressed) {
                        pointerIsDown = false
                    } else {
                        val delta = change.position - down.position
                        dragX = delta.x
                        dragY = delta.y
                        velocityTracker.addPosition(change.uptimeMillis, change.position)

                        if (!isPageDrag && abs(dragX) >= HORIZONTAL_DRAG_INTENT_PX && abs(dragX) >= abs(dragY)) {
                            isPageDrag = true
                            pagerState.onDragStarted()
                        }

                        if (isPageDrag) {
                            pagerState.snapTo(
                                (startPagePosition - (dragX / pageWidthPx))
                                    .coerceIn(0f, layout.lastPageIndex.toFloat()),
                            )
                            change.consume()
                        }
                    }
                }

                if (isPageDrag) {
                    val velocity = velocityTracker.calculateVelocity().x
                    val releasedPagePosition =
                        (startPagePosition - (dragX / pageWidthPx))
                            .coerceIn(0f, layout.lastPageIndex.toFloat())
                    val targetIndex =
                        pageSettleTargetIndex(
                            startPagePosition = startPagePosition,
                            releasedPagePosition = releasedPagePosition,
                            horizontalDragPx = dragX,
                            pageWidthPx = pageWidthPx,
                            horizontalVelocityPxPerSecond = velocity,
                            pageCount = layout.pages.size,
                        )
                    launchPageMotion {
                        pagerState.onTargetPageSettling(targetIndex)
                        pagerState.animateToPage(
                            targetPagePosition = targetIndex.toFloat(),
                            initialVelocity = -velocity / pageWidthPx.coerceAtLeast(1f),
                            reducedMotion = reducedMotion,
                        )
                        pagerState.onDragStopped(targetIndex)
                    }
                }
            }
        }
    }

private fun pageSettleTargetIndex(
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

private const val PAGE_WIDTH_FRACTION = 1f
private const val HORIZONTAL_DRAG_INTENT_PX = 18f
private const val PAGE_CHANGE_DISTANCE_THRESHOLD = 0.22f
private const val PAGE_FLING_VELOCITY_THRESHOLD_PX_PER_SECOND = 900f
