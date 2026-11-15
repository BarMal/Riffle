package com.riffle.app.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.riffle.app.launcher.designsystem.RiffleMotion
import com.riffle.core.domain.launcher.designsystem.RiffleMotionTokens
import kotlin.math.roundToInt

/**
 * The one motion of the Dock body's length (batch 7, item 3): whatever makes the Dock longer or shorter along its
 * edge (the contextual tiles appended with the Stream, a live app arriving or leaving) eases the glass body from the
 * length it has to the one it is to have, on the Dock's own spring ([RiffleMotion.smooth] at the opening speed,
 * critically damped: no overshoot) and nothing else resizes the Dock then. The value is read where the body is laid
 * out ([dockBodyLength]), so a frame of it relays out the Dock and recomposes nothing; the content is laid out at its
 * final size and the body clips it ([DockBodyFrame]), so nothing is measured per frame.
 */
@Stable
internal class DockBodyLength(
    initialDp: Float,
) {
    private val animatable = Animatable(initialDp)

    /** Where the length is now, in dp: a snapshot read, for layout and draw. */
    val dp: Float get() = animatable.value

    /** Whether it is on its way. */
    val isRunning: Boolean get() = animatable.isRunning

    suspend fun animateTo(
        targetDp: Float,
        spec: FiniteAnimationSpec<Float>,
    ) {
        animatable.animateTo(targetDp, spec)
    }

    suspend fun snapTo(targetDp: Float) {
        animatable.snapTo(targetDp)
    }
}

/**
 * The animated length of a Dock body whose length is to be [targetDp]. It eases only when [ease] says the Dock has
 * settled (not on the first frames, not when it changes edge: see [rememberDockSizeEasing]) and the motion is not
 * reduced; else it is the target at once, in the same frame, so a body is never a frame behind its content.
 */
@Composable
internal fun rememberDockBodyLength(
    targetDp: Float,
    ease: Boolean,
    reducedMotion: Boolean,
): DockBodyLength {
    val state = remember { DockBodyLength(targetDp) }
    val tuning = LocalDockMotionTuning.current
    val easing = ease && !reducedMotion
    LaunchedEffect(targetDp, easing, tuning) {
        if (easing) {
            state.animateTo(targetDp, RiffleMotion.smooth(tuning, reducedMotion = false, visibilityThreshold = 0.05f))
        } else {
            state.snapTo(targetDp)
        }
    }
    return state
}

/**
 * Sizes the body: [length] dp (read in layout) along the Dock's edge and [crossDp] across it, whatever the constraints
 * ask (held inside them), so the glass drawn by the modifiers after this one is the animated body.
 */
internal fun Modifier.dockBodyLength(
    runsHorizontally: Boolean,
    crossDp: Dp,
    length: () -> Float,
    targetDp: Float,
    easing: Boolean,
    /** Told how long the room along the edge is (px): the body is never longer, whatever length it wants. */
    onRoom: (Int) -> Unit = {},
): Modifier =
    layout { measurable, constraints ->
        onRoom(if (runsHorizontally) constraints.maxWidth else constraints.maxHeight)
        // While the Dock is not easing (first frames, a change of edge, reduced motion) the body is the target in the
        // very frame it changes, before the spring state has been told.
        val main = (if (easing) length() else targetDp).coerceAtLeast(0f)
        val mainPx = main.dp.roundToPx()
        val crossPx = crossDp.roundToPx()
        val width = (if (runsHorizontally) mainPx else crossPx).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (if (runsHorizontally) crossPx else mainPx).coerceIn(constraints.minHeight, constraints.maxHeight)
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(width, height) { placeable.place(0, 0) }
    }

/**
 * The body's content frame: what the strip holds, laid out at the size it has once the body has arrived
 * ([targetBodyMainDp] less the padding, along the run) and placed as it will stand then, clipped to the body as it is
 * now. So a body that is still growing shows the part of the row it has reached, with nothing measured again and
 * nothing squeezed; and the frame is the viewport the icons fade in ([LocalDockRunViewport]: by how much of each is
 * in view), so a tile is never drawn half cut off, and one the body has not reached is not drawn at all.
 */
@Composable
@Suppress("LongParameterList")
internal fun DockBodyFrame(
    runsHorizontally: Boolean,
    /** The length the body is heading for along the run, and the padding it keeps at each end of it (dp). */
    targetBodyMainDp: Int,
    paddingDp: Int,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    /** The opacity of the content (read where it is drawn): the cross-fade of a change under reduced motion. */
    alpha: () -> Float = { 1f },
    /**
     * Whether the icons fade where the frame cuts them even at rest: the contextual row's do (it scrolls, and an icon
     * at its end is cut by design), the Home mode's static run and section do not (they are whole, and whatever their
     * own scroll cuts is left as it was); they fade only while the body is moving.
     */
    fadeAtRest: Boolean = false,
    moving: () -> Boolean = { false },
) {
    val viewport = remember(runsHorizontally) { DockRunViewport(runsHorizontally) }
    val latestFadeAtRest by rememberUpdatedState(fadeAtRest)
    val latestMoving by rememberUpdatedState(moving)
    viewport.enabled = { latestFadeAtRest || latestMoving() }
    // Slack only where the frame is not itself the row's viewport: the Home mode's rounding (an icon's size and gap).
    val slackPx = if (fadeAtRest) 0f else FRAME_SLACK_PX
    Box(
        modifier =
            modifier
                .clipToBounds()
                .onGloballyPositioned { coordinates ->
                    // A little slack, so the pixel an icon's rounded size and gap may stand past the frame is no cut.
                    val bounds = Rect(coordinates.positionInRoot(), coordinates.size.toSize()).inflate(slackPx)
                    if (viewport.bounds != bounds) viewport.bounds = bounds
                }
                .layout { measurable, constraints ->
                    // Rounded as the body rounds its own length and padding, so at rest the frame is exactly the
                    // room the content has always had.
                    val targetPx = (targetBodyMainDp.dp.roundToPx() - 2 * paddingDp.dp.roundToPx()).coerceAtLeast(0)
                    val childConstraints =
                        if (runsHorizontally) {
                            Constraints(0, targetPx, 0, constraints.maxHeight)
                        } else {
                            Constraints(0, constraints.maxWidth, 0, targetPx)
                        }
                    val placeable = measurable.measure(childConstraints)
                    val childMain = if (runsHorizontally) placeable.width else placeable.height
                    val childCross = if (runsHorizontally) placeable.height else placeable.width
                    // The frame is as long as the body gives it and as thick as its content.
                    val width = if (runsHorizontally) constraints.maxWidth else childCross
                    val height = if (runsHorizontally) childCross else constraints.maxHeight
                    val offset = ((targetPx - childMain) / 2f).roundToInt()
                    layout(width, height) {
                        if (runsHorizontally) placeable.place(offset, 0) else placeable.place(0, offset)
                    }
                }
                .graphicsLayer { this.alpha = alpha() },
    ) {
        CompositionLocalProvider(LocalDockRunViewport provides viewport, content = content)
    }
}

/** The semantics property of the opacity the Dock's content is drawn at across a change of mode (a test hook). */
internal val DockContentAlphaKey = SemanticsPropertyKey<Float>("DockContentAlpha")

/**
 * The content's opacity across a change of what the strip holds, under reduced motion (the size snaps, the content
 * cross-fades): a function read where the content is drawn, 0 the moment [mode] changes and 1 again over the design
 * language's short fade; always 1 otherwise.
 */
@Composable
internal fun rememberDockContentFade(
    mode: Any?,
    reducedMotion: Boolean,
): () -> Float {
    val state = remember { DockContentFade(mode) }
    state.wanted = mode
    LaunchedEffect(mode, reducedMotion) {
        if (reducedMotion && state.shown != mode) {
            state.shown = mode
            state.anim.snapTo(0f)
            state.anim.animateTo(1f, tween(RiffleMotionTokens.DURATION_REDUCED_MOTION_MILLIS))
        } else {
            state.shown = mode
            state.anim.snapTo(1f)
        }
    }
    // Only a reduced-motion change dips to nothing and fades back in. With the motion on the content is drawn on every
    // frame of the change (a frame of an empty body, the effect not yet having run, was the blink the owner saw).
    return {
        if (!reducedMotion) {
            1f
        } else if (state.shown != state.wanted) {
            0f
        } else {
            state.anim.value
        }
    }
}

@Stable
private class DockContentFade(
    initial: Any?,
) {
    val anim = Animatable(1f)
    var wanted: Any? by mutableStateOf(initial)
    var shown: Any? by mutableStateOf(initial)
}

/** What the frame's viewport is widened by, in px, so rounding between an icon's and the frame's edges is not a cut. */
private const val FRAME_SLACK_PX = 3f

/** Where the Dock's body reports how long it is in the contextual mode, into [hostState]. */
@Composable
internal fun rememberDockContextualLengthSink(hostState: HomeDockHostState): (Float) -> Unit =
    remember(hostState) {
        {
                length ->
            if (hostState.contextualLengthDp.value != length) hostState.contextualLengthDp.value = length
        }
    }

/**
 * Where the Dock's body stands along its edge while it is extended for the contextual mode (ADR 0003, Amendment 3,
 * "the pinned run stays where it is"): the body's start edge stays where the Home mode's body has it ([homePx] long,
 * placed by [fraction] of the room: 0 start, a half centred, 1 end), so the pinned icons do not move, and the body
 * grows away from it toward the end; only when the end reaches the edge's room ([runPx]) does it grow back the other
 * way. Returns how far the body is from where its parent's alignment would put a body [lengthPx] long (px along the
 * edge).
 */
internal fun dockAnchoredShiftPx(
    runPx: Float,
    homePx: Float,
    lengthPx: Float,
    fraction: Float,
): Float {
    val aligned = (runPx - lengthPx) * fraction
    val atHome = (runPx - homePx) * fraction
    val start = minOf(atHome, runPx - lengthPx).coerceAtLeast(0f)
    return start - aligned
}

/** What the body's anchoring needs (see [dockAnchoredShiftPx]): the room along the edge, the Home length and the
 * alignment. */
internal class DockBodyAnchor(
    val runDp: Float,
    val homeDp: Int,
    val fraction: Float,
) {
    fun shiftPx(
        lengthDp: Float,
        density: Float,
    ): Float = dockAnchoredShiftPx(runDp * density, homeDp * density, lengthDp * density, fraction)
}

/**
 * This body rectangle (now [nowLengthDp] long) as it will stand once it has arrived at [targetLengthDp], when the body
 * is
 * anchored: the start edge moves only as far as the anchor says ([DockBodyAnchor]), the length is the target.
 */
internal fun Rect.anchoredArrival(
    anchor: DockBodyAnchor,
    nowLengthDp: Float,
    targetLengthDp: Float,
    density: Float,
    runsHorizontally: Boolean,
): Rect {
    val targetPx = targetLengthDp * density
    val nowShift = anchor.shiftPx(nowLengthDp, density)
    val targetShift = anchor.shiftPx(targetLengthDp, density)
    // The rectangle stands where its parent's alignment put a body of its current length, shifted by [nowShift].
    val nowPx = nowLengthDp * density
    val aligned = (if (runsHorizontally) left else top) - nowShift
    val alignedTarget = aligned + (nowPx - targetPx) * anchor.fraction
    val start = alignedTarget + targetShift
    return if (runsHorizontally) {
        Rect(
            start,
            top,
            start + targetPx,
            bottom,
        )
    } else {
        Rect(left, start, right, start + targetPx)
    }
}
