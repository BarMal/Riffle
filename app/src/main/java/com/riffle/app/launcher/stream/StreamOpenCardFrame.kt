package com.riffle.app.launcher.stream

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.riffle.core.domain.launcher.dock.DockMotionConstants
import com.riffle.core.domain.launcher.dock.DockMotionTuning
import com.riffle.core.domain.launcher.settings.OpenCardPlacement
import com.riffle.core.domain.launcher.stream.OpenCardLayout
import com.riffle.core.domain.launcher.stream.OpenCardSpring
import com.riffle.core.domain.launcher.stream.OpenCardSpringState
import kotlin.math.roundToInt

/**
 * How far an open card has grown (0: its browse size, 1: its full content) and how opaque it is. The spring is the pure
 * [OpenCardSpring] (critically damped, constants in `DockMotionConstants`) run against the frame clock; under reduced
 * motion there is no growth, only a short fade.
 */
@Stable
internal class OpenCardMotionState {
    var progress by mutableFloatStateOf(0f)
        private set

    var fade by mutableFloatStateOf(1f)
        private set

    /** Carries the card to open ([open]) or back to its browse size. Returns when it has settled. */
    suspend fun animateTo(
        open: Boolean,
        reduced: Boolean,
        tuning: DockMotionTuning = StackDefaultTuning,
    ) {
        // Already there (a stack that has never had a card open starts here): nothing to run.
        if (progress == if (open) 1f else 0f) return
        if (reduced) {
            fadeTo(open)
            return
        }
        val target = if (open) 1f else 0f
        var state = OpenCardSpringState(progress)
        var last = withFrameNanos { it }
        while (!OpenCardSpring.isSettled(state, target)) {
            val now = withFrameNanos { it }
            state = OpenCardSpring.advance(state, target, (now - last) / NANOS_PER_SECOND, tuning)
            last = now
            progress = state.progress
        }
        progress = target
    }

    /** Reduced motion: the card is open at once and fades in; closing fades it out and then it is gone. */
    private suspend fun fadeTo(open: Boolean) {
        try {
            val start = withFrameNanos { it }
            if (open) {
                progress = 1f
                fade = OpenCardSpring.reducedFadeAlpha(0f)
            }
            var elapsed = 0f
            while (elapsed < DockMotionConstants.REDUCED_FADE_SECONDS) {
                fade =
                    if (open) {
                        OpenCardSpring.reducedFadeAlpha(elapsed)
                    } else {
                        1f - elapsed / DockMotionConstants.REDUCED_FADE_SECONDS
                    }
                elapsed = (withFrameNanos { it } - start) / NANOS_PER_SECOND
            }
        } finally {
            fade = 1f
            progress = if (open) 1f else 0f
        }
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000f
    }
}

/** What the open card's frame last laid out, read by the stack under it to move aside. Written in layout. */
internal class OpenCardFrameGeometry {
    // State, so the stack's graphics layer (which reads the top) is redrawn when the open card's layout moves it.
    var topPx by mutableFloatStateOf(0f)
    var heightPx by mutableFloatStateOf(0f)
}

/**
 * Lays out the one child, an open card, in the Stream's area at the frame [OpenCardLayout] gives for the motion's
 * progress: where its browse card stands at 0, where [placement] puts it at 1, and the height in between. The child is
 * measured at its natural height (held to the room there is), placed, and drawn clipped to the part that has grown, so
 * the card is revealed rather than stretched. [geometry] is told where it lies so the cards behind move out of its
 * way.
 */
@Composable
@Suppress("LongParameterList")
internal fun OpenCardFrame(
    motion: OpenCardMotionState,
    geometry: OpenCardFrameGeometry,
    placement: OpenCardPlacement,
    cardWidth: Dp,
    browseHeight: Dp,
    bottomInset: Dp,
    strips: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier =
            modifier
                .graphicsLayer { alpha = motion.fade }
                .drawWithContent {
                    val top = geometry.topPx
                    clipRect(top = top, bottom = top + geometry.heightPx) { this@drawWithContent.drawContent() }
                },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val areaHeight = constraints.maxHeight
        val cardPx = cardWidth.roundToPx().coerceAtMost(width)
        val inset = bottomInset.roundToPx()
        val stripsPx = strips.roundToPx()
        val room = (areaHeight - inset - stripsPx).coerceAtLeast(0)
        val placeable = measurables.firstOrNull()?.measure(Constraints(cardPx, cardPx, 0, room))
        val frame =
            OpenCardLayout.frame(
                progress = motion.progress,
                areaHeightPx = areaHeight.toFloat(),
                bottomInsetPx = inset.toFloat(),
                browseHeightPx = browseHeight.roundToPx().toFloat(),
                fullHeightPx = (placeable?.height ?: 0).toFloat(),
                stripsPx = stripsPx.toFloat(),
                placement = placement,
            )
        geometry.topPx = frame.topPx
        geometry.heightPx = frame.heightPx
        layout(width, areaHeight) { placeable?.place((width - cardPx) / 2, frame.topPx.roundToInt()) }
    }
}
