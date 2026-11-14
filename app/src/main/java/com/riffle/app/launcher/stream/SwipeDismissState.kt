package com.riffle.app.launcher.stream

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.riffle.app.launcher.LauncherHapticEvent
import com.riffle.app.launcher.LocalLauncherHaptics
import com.riffle.core.domain.launcher.designsystem.RiffleMotionTokens
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.SwipeDismiss
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A card that follows a sideways finger and dismisses its notification (ADR 0003, Amendment 1; device fixes, round 5,
 * item 2). The platform's `draggable` (ADR 0002) gives the drag, the slop and the release velocity; the rules (how far
 * or how fast, the fade) are the pure [SwipeDismiss]. The card follows the finger with a slight fade; released early it
 * settles back on a spring; past the threshold, or flung, it flies off the way it was taken and [onDismiss] runs. Under
 * reduced motion it does not fly off: it fades out in place, and settles back at once. A dismissal that does not take
 * (the notification is still there [SwipeDismiss.RETURN_AFTER_MILLIS] later) brings the card back. The offset and the
 * fade are read where the card is drawn ([swipeLayer]), so a drag recomposes nothing.
 */
@Stable
internal class SwipeDismissState(
    private val scope: CoroutineScope,
    private val reducedMotion: () -> Boolean,
    private val density: Float,
    private val onDismiss: () -> Unit,
) {
    /** How far the card has been taken sideways, in px (positive: right). */
    var offsetPx by mutableFloatStateOf(0f)
        private set

    private var outFade by mutableFloatStateOf(1f)
    private var job: Job? = null

    /** The card's width, in px, set when it is measured. */
    var widthPx: Float = 0f

    /** The card's opacity: the slight fade of the way it has gone, and the fade-out of a reduced-motion dismissal. */
    fun alpha(): Float = SwipeDismiss.alpha(offsetPx, widthPx) * outFade

    val draggable: DraggableState = DraggableState { delta -> offsetPx += delta }

    /** The finger landed on the card (or took it again): whatever was animating stops where it is. */
    fun grab() {
        job?.cancel()
        job = null
    }

    /** The finger lifted at [velocityPxPerSecond] (positive: right). */
    fun release(velocityPxPerSecond: Float) {
        val outcome = SwipeDismiss.decide(offsetPx, widthPx, velocityPxPerSecond, density)
        job?.cancel()
        job =
            scope.launch {
                if (outcome == SwipeDismiss.Outcome.DISMISS) {
                    sendOff(velocityPxPerSecond)
                    onDismiss()
                    // Still on screen after this: the dismissal did not take, so the card comes back.
                    delay(SwipeDismiss.RETURN_AFTER_MILLIS)
                }
                settleBack()
            }
    }

    private suspend fun sendOff(velocityPxPerSecond: Float) {
        if (reducedMotion()) {
            animate(1f, 0f, animationSpec = tween(RiffleMotionTokens.DURATION_REDUCED_MOTION_MILLIS)) { value, _ ->
                outFade = value
            }
        } else {
            val target = SwipeDismiss.flyOffTarget(offsetPx, velocityPxPerSecond, widthPx)
            animate(offsetPx, target, animationSpec = tween(FLY_OFF_MILLIS)) { value, _ -> offsetPx = value }
        }
    }

    private suspend fun settleBack() {
        if (reducedMotion()) {
            offsetPx = 0f
        } else {
            val spec = spring<Float>(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)
            animate(offsetPx, 0f, animationSpec = spec) { value, _ -> offsetPx = value }
        }
        outFade = 1f
    }

    private companion object {
        const val FLY_OFF_MILLIS = 180
    }
}

/**
 * The swipe state of [content]'s card (null when it cannot be swiped: [SwipeDismiss.offers]): its dismissal asks the
 * Stream to dismiss the notification and plays one light tick (the tick wrapper honours the "light ticks" setting).
 */
@Composable
internal fun rememberSwipeDismissState(
    content: StreamContent?,
    callbacks: StreamCallbacks,
    reducedMotion: Boolean,
): SwipeDismissState? {
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val haptics by rememberUpdatedState(LocalLauncherHaptics.current)
    val latestCallbacks by rememberUpdatedState(callbacks)
    val latestReduced by rememberUpdatedState(reducedMotion)
    val latestContent by rememberUpdatedState(content)
    val offered = content != null && SwipeDismiss.offers(content)
    return remember(offered, density) {
        if (!offered) {
            null
        } else {
            SwipeDismissState(scope, { latestReduced }, density) {
                haptics.perform(LauncherHapticEvent.DETENT)
                latestContent?.let { latestCallbacks.onAction(it, StreamAction.Dismiss) }
            }
        }
    }
}

/** The part of [swipeLayer] a card draws: its offset and its fade, read when drawing. */
internal fun Modifier.swipeLayer(state: SwipeDismissState?): Modifier =
    if (state == null) {
        this
    } else {
        graphicsLayer {
            translationX = state.offsetPx
            alpha = state.alpha()
        }.onSizeChanged { state.widthPx = it.width.toFloat() }
    }

/** The drag of a card that can be swiped away: sideways, from where the finger lands on the card. */
internal fun Modifier.swipeToDismiss(state: SwipeDismissState?): Modifier =
    if (state == null) {
        this
    } else {
        draggable(
            state = state.draggable,
            orientation = Orientation.Horizontal,
            onDragStarted = { state.grab() },
            onDragStopped = { velocity -> state.release(velocity) },
        )
    }

/** The notification [this] entry draws as a card that can be swiped away, or null. */
internal fun StackEntry.swipeableNotification(): NotificationContent? =
    (openContent as? NotificationContent)?.takeIf { this is ContentEntry && SwipeDismiss.offers(it) }
