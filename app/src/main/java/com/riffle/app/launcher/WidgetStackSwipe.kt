package com.riffle.app.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.riffle.app.launcher.designsystem.RiffleMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The motion of a widget stack (ADR 0003, Amendment 14): where the stack is between its widgets ([position], in whole
 * widgets: 2.0 is the third at rest, 2.4 is the fourth coming in) and how visible its page dots are ([dotsAlpha]).
 * Both are animated values read only in layout, draw and graphics-layer lambdas, so a swipe never recomposes the
 * stack. A swipe moves at most one widget; it settles on the Page and panel spring ([RiffleMotion.smooth]) or, under
 * reduced motion, jumps.
 */
internal class WidgetStackSwipe(
    private val scope: CoroutineScope,
    initialShown: Int,
) {
    val position = Animatable(initialShown.toFloat())
    val dotsAlpha = Animatable(1f)

    /** The stack's length along the swipe, in px, as last laid out. */
    var extentPx: Float = 1f

    var count: Int = 1
    var shown: Int = initialShown
    var reducedMotion: Boolean = false

    /** Called with the index a swipe settled on, once it has. */
    var onShown: (Int) -> Unit = {}
    private var fading: Job? = null

    /** A drag of [deltaPx] along the axis: swiping toward the start moves on to the next widget. */
    fun dragBy(deltaPx: Float) {
        val low = (shown - 1).coerceAtLeast(0).toFloat()
        val high = (shown + 1).coerceAtMost(count - 1).toFloat()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            position.snapTo((position.value - deltaPx / extentPx.coerceAtLeast(1f)).coerceIn(low, high))
        }
    }

    /** The finger let go at [velocityPxPerSecond] along the axis: settle on the nearest widget, or the next one. */
    fun release(velocityPxPerSecond: Float) {
        val flung = -velocityPxPerSecond / extentPx.coerceAtLeast(1f)
        val at = position.value
        val target =
            when {
                flung > FLING_WIDGETS_PER_SECOND -> ceil(at)
                flung < -FLING_WIDGETS_PER_SECOND -> floor(at)
                else -> at.roundToInt().toFloat()
            }
        settleOn(target.toInt())
    }

    /** The accessibility and menu route: one widget on. False when there is none that way. */
    fun step(delta: Int): Boolean {
        val target = shown + delta
        if (target !in 0 until count) return false
        settleOn(target)
        return true
    }

    private fun settleOn(index: Int) {
        val target = index.coerceIn((shown - 1).coerceAtLeast(0), (shown + 1).coerceAtMost(count - 1))
        scope.launch {
            if (reducedMotion) {
                position.snapTo(target.toFloat())
            } else {
                position.animateTo(target.toFloat(), RiffleMotion.smooth<Float>())
            }
            if (target != shown) onShown(target)
        }
        wakeDots()
    }

    /** Dots show while the stack is used and for a moment after, then fade to the idle level. */
    fun wakeDots() {
        fading?.cancel()
        fading =
            scope.launch {
                dotsAlpha.snapTo(1f)
                delay(DOTS_VISIBLE_MILLIS)
                if (reducedMotion) {
                    dotsAlpha.snapTo(DOTS_IDLE_ALPHA)
                } else {
                    dotsAlpha.animateTo(DOTS_IDLE_ALPHA, RiffleMotion.smooth<Float>())
                }
            }
    }

    /** The widget shown changed outside a swipe (reorder, a menu): follow it. */
    suspend fun follow(index: Int) {
        if (abs(position.value - index) > FOLLOW_EPSILON && !position.isRunning) position.snapTo(index.toFloat())
    }

    private companion object {
        const val FLING_WIDGETS_PER_SECOND = 2f
        const val FOLLOW_EPSILON = 0.001f
    }
}

/** How long the dots stay fully visible after the stack is used, and the level they fade to (they never vanish). */
internal const val DOTS_VISIBLE_MILLIS = 1_500L
internal const val DOTS_IDLE_ALPHA = 0.5f

@Composable
internal fun rememberWidgetStackSwipe(
    count: Int,
    shown: Int,
    reducedMotion: Boolean,
    onShown: (Int) -> Unit,
): WidgetStackSwipe {
    val scope = rememberCoroutineScope()
    val swipe = remember(scope) { WidgetStackSwipe(scope, shown) }
    val latestOnShown = rememberUpdatedState(onShown)
    swipe.count = count
    swipe.shown = shown
    swipe.reducedMotion = reducedMotion
    swipe.onShown = { index -> latestOnShown.value(index) }
    LaunchedEffect(shown, count) { swipe.follow(shown) }
    LaunchedEffect(swipe) { swipe.wakeDots() }
    return swipe
}

/** A swipe along [vertical]'s axis that moves [swipe]; nothing else (a tap or a long press is not touched). */
@Composable
internal fun Modifier.widgetStackSwipe(
    swipe: WidgetStackSwipe,
    vertical: Boolean,
    reverse: Boolean = false,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this
    val state = rememberDraggableState { delta -> swipe.dragBy(delta) }
    return draggable(
        state = state,
        orientation = if (vertical) Orientation.Vertical else Orientation.Horizontal,
        reverseDirection = reverse,
        onDragStarted = { swipe.wakeDots() },
        onDragStopped = { velocity -> swipe.release(velocity) },
    )
}

/** The recomposition probe key of a widget stack: swiping it must not recompose it. */
internal fun homeWidgetStackProbeKey(itemId: String): String = "home-widget-stack:$itemId"
