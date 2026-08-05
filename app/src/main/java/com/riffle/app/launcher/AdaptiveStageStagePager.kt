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
import com.riffle.core.domain.launcher.cards.AppStage
import com.riffle.core.domain.launcher.cards.AppStageId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Continuous horizontal drag-to-switch-stage pager state, mirroring
 * [rememberImmediateHomePagerState]'s fractional-position/settle-animation/pending-target-guard
 * shape but keyed on AdaptiveStage's stage list instead of Standard Home's page list.
 *
 * Settling dispatches [LauncherShellAction.SelectAppStage] -- the same action a rail tap or the
 * stage selector already dispatches -- so no reducer changes are needed.
 */
@Suppress("LongMethod")
@Composable
internal fun rememberAdaptiveStageStagePagerState(
    stages: List<AppStage>,
    selectedStageId: AppStageId?,
    reducedMotion: Boolean = false,
    onAction: (LauncherShellAction) -> Unit,
): AdaptiveStageStagePagerState {
    val selectedStageIndex = stages.indexOfFirst { stage -> stage.id == selectedStageId }.coerceAtLeast(0)
    val dragStagePosition = remember { mutableFloatStateOf(selectedStageIndex.toFloat()) }
    val settleStagePosition = remember { Animatable(selectedStageIndex.toFloat()) }
    val isDragging = remember { mutableStateOf(false) }
    val isSettling = remember { mutableStateOf(false) }
    val pendingGestureTargetStageIndex = remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(
        selectedStageIndex,
        stages.size,
        isDragging.value,
        pendingGestureTargetStageIndex.value,
    ) {
        if (pendingGestureTargetStageIndex.value == selectedStageIndex) {
            pendingGestureTargetStageIndex.value = null
        }

        val shouldApplyExternalSelection =
            shouldApplyExternalHomePageSelection(
                isDragging = isDragging.value,
                isSettling = isSettling.value,
                hasPendingGestureTarget = pendingGestureTargetStageIndex.value != null,
                pageCount = stages.size,
                currentPagePosition = dragStagePosition.floatValue,
                selectedPageIndex = selectedStageIndex,
            )

        if (shouldApplyExternalSelection) {
            when (homePageExternalSelectionSettlePolicy(reducedMotion)) {
                HomePageExternalSelectionSettlePolicy.ImmediateSnap -> {
                    val targetStagePosition = selectedStageIndex.toFloat()
                    dragStagePosition.floatValue = targetStagePosition
                    settleStagePosition.snapTo(targetStagePosition)
                }

                HomePageExternalSelectionSettlePolicy.AnimatedSettle -> {
                    isSettling.value = true
                    try {
                        settleStagePosition.snapTo(dragStagePosition.floatValue)
                        settleStagePosition.animateTo(
                            targetValue = selectedStageIndex.toFloat(),
                            animationSpec =
                                adaptiveStageStageSettleAnimation(homePageSettleMotionPolicy(reducedMotion)),
                        ) {
                            dragStagePosition.floatValue = value
                        }
                        dragStagePosition.floatValue = selectedStageIndex.toFloat()
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
                when (stages.getOrNull(targetIndex)?.id) {
                    selectedStageId -> null
                    null -> null
                    else -> targetIndex
                }
        },
        onDragStopped = { targetIndex ->
            val targetStageId = stages.getOrNull(targetIndex)?.id
            pendingGestureTargetStageIndex.value =
                when (targetStageId) {
                    selectedStageId -> null
                    null -> null
                    else -> targetIndex
                }

            targetStageId
                ?.takeIf { stageId -> stageId != selectedStageId }
                ?.let { stageId -> onAction(LauncherShellAction.SelectAppStage(stageId)) }
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
 */
internal fun Modifier.adaptiveStageStagePagerDrag(
    enabled: Boolean,
    stageWidthPx: Float,
    stages: List<AppStage>,
    selectedStageId: AppStageId?,
    pagerState: AdaptiveStageStagePagerState,
    reducedMotion: Boolean,
    launchStageMotion: (suspend () -> Unit) -> Unit,
): Modifier =
    if (!enabled) {
        this
    } else {
        val stageIdsKey = stages.joinToString(separator = "|") { stage -> adaptiveStageStageKey(stage.id) }
        pointerInput(stageWidthPx, selectedStageId, stageIdsKey) {
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
                val lastStageIndex = stages.lastIndex.coerceAtLeast(0)

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
                            pageCount = stages.size,
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
