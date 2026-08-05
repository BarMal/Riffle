package com.riffle.app.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Continuous horizontal drag-to-switch-page pager state, mirroring
 * [rememberImmediateHomePagerState]'s fractional-position/settle-animation/pending-target-guard
 * shape but keyed on an abstract page count/index instead of Standard Home's page list.
 *
 * This is deliberately generic over what a "page" is -- [pageCount] and [selectedIndex] are plain
 * integers, and [onSettle] receives the settled index directly, rather than this function resolving
 * [com.riffle.core.domain.launcher.cards.AppStageId]s itself. The caller (which knows whether a given
 * index is a real app stage or a virtual page like "All notifications") decides what settling on it
 * means -- e.g. dispatching [LauncherShellAction.SelectAppStage] for a real stage's index, or just
 * updating local UI state for a virtual page's index.
 */
@Suppress("LongMethod")
@Composable
internal fun rememberAdaptiveStageStagePagerState(
    pageCount: Int,
    selectedIndex: Int,
    reducedMotion: Boolean = false,
    onSettle: (Int) -> Unit,
): AdaptiveStageStagePagerState {
    val coercedSelectedIndex = selectedIndex.coerceAtLeast(0)
    val dragStagePosition = remember { mutableFloatStateOf(coercedSelectedIndex.toFloat()) }
    val settleStagePosition = remember { Animatable(coercedSelectedIndex.toFloat()) }
    val isDragging = remember { mutableStateOf(false) }
    val isSettling = remember { mutableStateOf(false) }
    val pendingGestureTargetStageIndex = remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(
        coercedSelectedIndex,
        pageCount,
        isDragging.value,
        pendingGestureTargetStageIndex.value,
    ) {
        if (pendingGestureTargetStageIndex.value == coercedSelectedIndex) {
            pendingGestureTargetStageIndex.value = null
        }

        val shouldApplyExternalSelection =
            shouldApplyExternalHomePageSelection(
                isDragging = isDragging.value,
                isSettling = isSettling.value,
                hasPendingGestureTarget = pendingGestureTargetStageIndex.value != null,
                pageCount = pageCount,
                currentPagePosition = dragStagePosition.floatValue,
                selectedPageIndex = coercedSelectedIndex,
            )

        if (shouldApplyExternalSelection) {
            when (homePageExternalSelectionSettlePolicy(reducedMotion)) {
                HomePageExternalSelectionSettlePolicy.ImmediateSnap -> {
                    val targetStagePosition = coercedSelectedIndex.toFloat()
                    dragStagePosition.floatValue = targetStagePosition
                    settleStagePosition.snapTo(targetStagePosition)
                }

                HomePageExternalSelectionSettlePolicy.AnimatedSettle -> {
                    isSettling.value = true
                    try {
                        settleStagePosition.snapTo(dragStagePosition.floatValue)
                        settleStagePosition.animateTo(
                            targetValue = coercedSelectedIndex.toFloat(),
                            animationSpec =
                                adaptiveStageStageSettleAnimation(homePageSettleMotionPolicy(reducedMotion)),
                        ) {
                            dragStagePosition.floatValue = value
                        }
                        dragStagePosition.floatValue = coercedSelectedIndex.toFloat()
                    } finally {
                        isSettling.value = false
                    }
                }
            }
        }
    }

    return AdaptiveStageStagePagerState(
        pagePositionState = dragStagePosition,
        settlePagePosition = settleStagePosition,
        isSettling = isSettling,
        isDragging = isDragging,
        onDragStarted = {
            isDragging.value = true
            isSettling.value = false
        },
        onTargetStageSettling = { targetIndex ->
            pendingGestureTargetStageIndex.value =
                if (targetIndex == coercedSelectedIndex) null else targetIndex
        },
        onDragStopped = { targetIndex ->
            pendingGestureTargetStageIndex.value =
                if (targetIndex == coercedSelectedIndex) null else targetIndex

            if (targetIndex != coercedSelectedIndex) onSettle(targetIndex)
            isDragging.value = false
        },
    )
}

internal class AdaptiveStageStagePagerState(
    private val pagePositionState: MutableFloatState,
    private val settlePagePosition: Animatable<Float, *>,
    private val isSettling: MutableState<Boolean>,
    private val isDragging: MutableState<Boolean>,
    val onDragStarted: () -> Unit,
    val onTargetStageSettling: (Int) -> Unit,
    val onDragStopped: (Int) -> Unit,
) {
    val pagePosition: Float
        get() = pagePositionState.floatValue

    val visualSelectedStageIndex: Int
        get() = pagePosition.roundToInt()

    val isStageGestureActive: Boolean
        get() = isDragging.value || isSettling.value

    suspend fun stopSettling() {
        settlePagePosition.stop()
        isSettling.value = false
    }

    fun snapTo(pagePosition: Float) {
        pagePositionState.floatValue = pagePosition
    }

    suspend fun animateToStage(
        targetStagePosition: Float,
        initialVelocity: Float,
        reducedMotion: Boolean,
    ) {
        isSettling.value = true
        try {
            settlePagePosition.snapTo(pagePositionState.floatValue)
            settlePagePosition.animateTo(
                targetValue = targetStagePosition,
                animationSpec = adaptiveStageStageSettleAnimation(homePageSettleMotionPolicy(reducedMotion)),
                initialVelocity = initialVelocity,
            ) {
                pagePositionState.floatValue = value
            }
            pagePositionState.floatValue = targetStagePosition
        } finally {
            isSettling.value = false
        }
    }
}

private fun adaptiveStageStageSettleAnimation(policy: HomePageSettleMotionPolicy) =
    when (policy) {
        HomePageSettleMotionPolicy.ReducedShortTween ->
            tween<Float>(
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

/**
 * Claims horizontal drags at the stage-body level, mirroring [immediateHomePageDrag]'s
 * horizontal-intent-threshold/continuous-snapTo/settle-threshold-or-fling logic. Runs on
 * [PointerEventPass.Initial] so it sees drags before [cardStackPointerInput] (which only consumes
 * vertical drags on the default Main pass) -- the two gestures coexist without conflict.
 *
 * [pageCount] and [selectedIndex] are the same abstract page identity [rememberAdaptiveStageStagePagerState]
 * uses; [navigationKey] should change whenever the underlying set of pages changes shape (a stage
 * added/removed, or a virtual page appearing/disappearing) so the gesture recognizer resets.
 */
internal fun Modifier.adaptiveStageStagePagerDrag(
    enabled: Boolean,
    stageWidthPx: Float,
    pageCount: Int,
    selectedIndex: Int,
    navigationKey: String,
    pagerState: AdaptiveStageStagePagerState,
    reducedMotion: Boolean,
    launchStageMotion: (suspend () -> Unit) -> Unit,
): Modifier =
    if (!enabled) {
        this
    } else {
        pointerInput(stageWidthPx, selectedIndex, navigationKey) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (stageWidthPx <= 0f) {
                    return@awaitEachGesture
                }

                launchStageMotion { pagerState.stopSettling() }
                val velocityTracker = VelocityTracker()
                velocityTracker.addPosition(down.uptimeMillis, down.position)

                val startStagePosition = pagerState.pagePosition
                var dragX = 0f
                var dragY = 0f
                var isStageDrag = false
                var pointerIsDown = true
                val lastStageIndex = (pageCount - 1).coerceAtLeast(0)

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

                        if (
                            !isStageDrag &&
                            abs(dragX) >= STAGE_HORIZONTAL_DRAG_INTENT_PX &&
                            abs(dragX) >= abs(dragY)
                        ) {
                            isStageDrag = true
                            pagerState.onDragStarted()
                        }

                        if (isStageDrag) {
                            pagerState.snapTo(
                                (startStagePosition - (dragX / stageWidthPx)).coerceIn(0f, lastStageIndex.toFloat()),
                            )
                            change.consume()
                        }
                    }
                }

                if (isStageDrag) {
                    val velocity = velocityTracker.calculateVelocity().x
                    val releasedStagePosition =
                        (startStagePosition - (dragX / stageWidthPx)).coerceIn(0f, lastStageIndex.toFloat())
                    val targetIndex =
                        pageSettleTargetIndex(
                            startPagePosition = startStagePosition,
                            releasedPagePosition = releasedStagePosition,
                            horizontalDragPx = dragX,
                            pageWidthPx = stageWidthPx,
                            horizontalVelocityPxPerSecond = velocity,
                            pageCount = pageCount,
                        )
                    launchStageMotion {
                        pagerState.onTargetStageSettling(targetIndex)
                        pagerState.animateToStage(
                            targetStagePosition = targetIndex.toFloat(),
                            initialVelocity = -velocity / stageWidthPx.coerceAtLeast(1f),
                            reducedMotion = reducedMotion,
                        )
                        pagerState.onDragStopped(targetIndex)
                    }
                }
            }
        }
    }

/** Same horizontal-intent threshold as [immediateHomePageDrag], kept as an independent constant. */
internal const val STAGE_HORIZONTAL_DRAG_INTENT_PX = 18f
