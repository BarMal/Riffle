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
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.riffle.app.launcher.LauncherHapticEvent
import com.riffle.app.launcher.LocalDockMotionTuning
import com.riffle.app.launcher.LocalLauncherHaptics
import com.riffle.core.domain.launcher.designsystem.RiffleMotionTokens
import com.riffle.core.domain.launcher.dock.DockMotionTuning
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.SwipeDismiss
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A card that follows a sideways finger (ADR 0003, Amendment 1; device fixes, round 5, item 2; batch 7). A horizontal
 * drag that starts on a card is always the card's ([SwipeDismiss.ownerOf]): the front card of a dismissible
 * notification ([dismissible]) is dismissed; any other card resists, a few dp with no fade, and settles back.
 * What follows is the dismissing card. The platform's `draggable` (ADR 0002) gives the drag, the slop and the release velocity; the rules (how far
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
    /** The Dock's motion tuning: the opening speed also sets how fast a card leaves and settles back. */
    private val tuning: () -> DockMotionTuning,
    private val density: Float,
    /** Whether this card is the front card of a notification that can be dismissed now (else it resists). */
    private val dismissible: () -> Boolean = { true },
    private val onDismiss: () -> Unit,
) {
    /** How far the card has been taken sideways, in px (positive: right). */
    var offsetPx by mutableFloatStateOf(0f)
        private set

    private var outFade by mutableFloatStateOf(1f)
    private var job: Job? = null

    /** The card's width, in px, set when it is measured. */
    var widthPx: Float = 0f

    /** The finger's travel, for a card that resists (its offset is the resisted travel). */
    private var rawPx = 0f

    /** Whether the card is taken to be dismissed (else it resists). */
    val dismissing: Boolean get() = dismissible()

    /** The card's opacity: the slight fade of the way it has gone and the fade-out of a reduced-motion dismissal. */
    fun alpha(): Float = if (dismissing) SwipeDismiss.alpha(offsetPx, widthPx) * outFade else 1f

    /** The card's scale: a dismissing card lifts a little as it is taken; one that resists does not. */
    fun scale(): Float = if (dismissing) SwipeDismiss.scale(offsetPx, density) else 1f

    /** Whether releasing now would dismiss the card by distance (the cue shows). */
    fun armed(): Boolean = dismissing && SwipeDismiss.isArmed(offsetPx, widthPx)

    /** The opacity of the "Dismiss" cue drawn in the space the card leaves. */
    fun cueAlpha(): Float = if (dismissing) SwipeDismiss.cueAlpha(offsetPx, widthPx) else 0f

    val draggable: DraggableState =
        DraggableState { delta ->
            if (dismissible()) {
                offsetPx += delta
            } else {
                rawPx += delta
                offsetPx = SwipeDismiss.resist(rawPx, density)
            }
        }

    /** The finger landed on the card (or took it again): whatever was animating stops where it is. */
    fun grab() {
        job?.cancel()
        job = null
        rawPx = if (dismissible()) 0f else SwipeDismiss.unresist(offsetPx, density)
    }

    /** The finger lifted at [velocityPxPerSecond] (positive: right). */
    fun release(velocityPxPerSecond: Float) {
        val outcome =
            if (dismissing) {
                SwipeDismiss.decide(offsetPx, widthPx, velocityPxPerSecond, density)
            } else {
                SwipeDismiss.Outcome.SETTLE
            }
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
            val millis = tuning().millisFor(SwipeDismiss.FLY_OFF_MILLIS)
            animate(offsetPx, target, animationSpec = tween(millis)) { value, _ -> offsetPx = value }
        }
    }

    private suspend fun settleBack() {
        if (reducedMotion()) {
            offsetPx = 0f
        } else {
            val spec = spring<Float>(Spring.DampingRatioNoBouncy, tuning().stiffnessFor(Spring.StiffnessMedium))
            animate(offsetPx, 0f, animationSpec = spec) { value, _ -> offsetPx = value }
        }
        outFade = 1f
        rawPx = 0f
    }
}

/**
 * The swipe state of [content]'s card (null when it cannot be swiped: [SwipeDismiss.offers]): its dismissal asks the
 * Stream to dismiss the notification and plays one light tick (the tick wrapper honours the "light ticks" setting).
 * With [resists] a card that cannot be dismissed (or is not the front card, [isFront]) still gets a state: it resists a
 * horizontal drag and settles back, so the drag never reaches the pager.
 */
@Composable
internal fun rememberSwipeDismissState(
    content: StreamContent?,
    callbacks: StreamCallbacks,
    reducedMotion: Boolean,
    resists: Boolean = false,
    isFront: Boolean = true,
): SwipeDismissState? {
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val haptics by rememberUpdatedState(LocalLauncherHaptics.current)
    val latestCallbacks by rememberUpdatedState(callbacks)
    val latestReduced by rememberUpdatedState(reducedMotion)
    val latestContent by rememberUpdatedState(content)
    val latestTuning by rememberUpdatedState(LocalDockMotionTuning.current)
    val latestFront by rememberUpdatedState(isFront)
    val offered = content != null && SwipeDismiss.offers(content)
    return remember(offered, resists, density) {
        if (!offered && !resists) {
            null
        } else {
            SwipeDismissState(
                scope,
                { latestReduced },
                { latestTuning },
                density,
                dismissible = { offered && latestFront },
            ) {
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
            val lift = state.scale()
            scaleX = lift
            scaleY = lift
        }.onSizeChanged { state.widthPx = it.width.toFloat() }
    }

/** The semantics property that says a card taken sideways is past the threshold (a test hook: releasing dismisses). */
internal val DismissArmedKey = SemanticsPropertyKey<Boolean>("DismissArmed")

/** The semantics property of the "Dismiss" cue's opacity, 0 until the card is past the threshold (a test hook). */
internal val DismissCueAlphaKey = SemanticsPropertyKey<Float>("DismissCueAlpha")

/** The semantics property of how far (px, positive: right) a card has been taken sideways (a test hook). */
internal val SwipeOffsetKey = SemanticsPropertyKey<Float>("SwipeOffset")

/** Publishes the swipe's armed state and cue opacity on a node (read when the tree is read, not per frame). */
internal fun SemanticsPropertyReceiver.swipeSemantics(state: SwipeDismissState?) {
    if (state != null) {
        this[DismissArmedKey] = state.armed()
        this[DismissCueAlphaKey] = state.cueAlpha()
        this[SwipeOffsetKey] = state.offsetPx
    }
}

/**
 * The cue of a card about to be dismissed: the word "Dismiss" in the room the card leaves, on the side it came from,
 * drawn behind the card in the draw phase (this modifier goes before [swipeLayer], so it does not move with the card).
 * Nothing is drawn until the card is past the threshold, and never for a card that resists.
 */
@Composable
internal fun Modifier.swipeCue(state: SwipeDismissState?): Modifier {
    if (state == null) return this
    val label = stringResource(R.string.stream_notification_dismiss)
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val color = MaterialTheme.colorScheme.onSurface
    val layout = remember(label, style) { measurer.measure(label, style, maxLines = 1) }
    val inset = with(LocalDensity.current) { SwipeCueInset.toPx() }
    return drawBehind {
        val alpha = state.cueAlpha()
        if (alpha > 0f) {
            val x = if (state.offsetPx >= 0f) inset else size.width - inset - layout.size.width
            drawText(
                layout,
                color = color,
                topLeft = Offset(x, (size.height - layout.size.height) / 2f),
                alpha = alpha,
            )
        }
    }
}

private val SwipeCueInset = 20.dp

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
