package com.riffle.app.launcher.stream

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.dock.DockMotionConstants
import com.riffle.core.domain.launcher.dock.DockMotionTuning
import com.riffle.core.domain.launcher.settings.OpenCardPlacement
import com.riffle.core.domain.launcher.stream.OpenCardLayout
import com.riffle.core.domain.launcher.stream.OpenCardSpring
import com.riffle.core.domain.launcher.stream.OpenCardSpringState
import com.riffle.core.domain.launcher.stream.StreamCardStackSpec
import com.riffle.core.domain.launcher.stream.StreamColumnRect
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

    /** False while the room is too short for the cards behind to peek out above the open card: they are not drawn. */
    var stripsVisible by mutableStateOf(true)
}

/**
 * The open card as the Stream's one overlay: what the stack that has a card open hands to the surface to draw over the
 * whole of its area (see [OpenCardHolder.layer]). It is drawn outside the pager, so no page, column or host clips it:
 * the screen is the only edge it can meet.
 */
@Stable
internal class OpenCardLayer(
    val geometry: OpenCardFrameGeometry,
    val placement: OpenCardPlacement,
    val browseHeight: Dp,
    val strips: Dp,
    val bottomInset: Dp,
    val onClose: () -> Unit,
    /** A tap on the strip of the card [rank] places behind the open one (1 is the nearest): open that card. */
    val onStripTap: (rank: Int) -> Unit,
    val content: @Composable () -> Unit,
)

/**
 * Draws [OpenCardHolder.layer], when a stack has published one: a scrim over the stack (a tap beside the card closes it
 * and the cards behind it are not tapped under it) and the card itself ([OpenCardFrame]).
 */
@Composable
internal fun OpenCardLayerHost(
    cards: OpenCardHolder,
    restColumn: (() -> StreamColumnRect?)?,
    modifier: Modifier = Modifier,
) {
    val layer = cards.layer ?: return
    Box(modifier = modifier.fillMaxSize().padding(top = RiffleSpacing.s)) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .clearAndSetSemantics {}
                    .pointerInput(layer) {
                        detectTapGestures { tap ->
                            // A tap on a strip behind the open card opens that card; anywhere else closes this one.
                            val top = layer.geometry.topPx
                            val pitch = StreamCardStackSpec.PITCH_DP * density
                            val rank = if (tap.y < top) kotlin.math.ceil((top - tap.y) / pitch).toInt() else 0
                            val strips = (layer.strips.toPx() / pitch).toInt()
                            if (layer.geometry.stripsVisible && rank in 1..strips) {
                                layer.onStripTap(
                                    rank,
                                )
                            } else {
                                layer.onClose()
                            }
                        }
                    },
        )
        OpenCardFrame(
            motion = cards.motion,
            geometry = layer.geometry,
            placement = layer.placement,
            restColumn = restColumn,
            browseHeight = layer.browseHeight,
            bottomInset = layer.bottomInset,
            strips = layer.strips,
            modifier = Modifier.matchParentSize(),
            content = layer.content,
        )
    }
}

/** The least height an open card is given before the cards behind it stop peeking out above it. */
private val MinOpenCardHeight = 240.dp

/** The content height of the open card, kept between frames so an animation measures the content once. */
private class OpenCardNaturalHeight {
    var key = 0
    var px = 0
}

/**
 * Lays out the one child, an open card, in the Stream's area at the frame [OpenCardLayout] gives for the motion's
 * progress: where its browse card stands at 0, where [placement] puts it at 1, and the height in between. The card is
 * a whole card at every frame: its glass, edge and rounded corners are drawn at the size it has now, and its content
 * keeps its top inside it (it scrolls, as it does once open), so the card is never cut off by a clip, its own or its
 * parent's. Only the card is measured per frame; nothing around it is. It stands in the column [restColumn] widened
 * toward the whole area by the progress (the whole area when null), centred in it with the page's side padding.
 * [geometry] is told where it lies so the cards behind move out of its way.
 */
@Composable
@Suppress("LongParameterList")
internal fun OpenCardFrame(
    motion: OpenCardMotionState,
    geometry: OpenCardFrameGeometry,
    placement: OpenCardPlacement,
    restColumn: (() -> StreamColumnRect?)?,
    browseHeight: Dp,
    bottomInset: Dp,
    strips: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val natural = remember { OpenCardNaturalHeight() }
    Layout(
        content = content,
        modifier = modifier.graphicsLayer { alpha = motion.fade },
    ) { measurables, constraints ->
        val areaWidth = constraints.maxWidth
        val areaHeight = constraints.maxHeight
        val progress = motion.progress.coerceIn(0f, 1f)
        val pad = RiffleSpacing.l.roundToPx()
        val readable = StreamMaxReadableWidth.roundToPx()
        val rest = restColumn?.invoke()
        val restLeft = rest?.let { it.left.dp.roundToPx() } ?: 0
        val restWidth = rest?.let { (it.right.dp.roundToPx() - it.left.dp.roundToPx()).coerceAtLeast(0) } ?: areaWidth
        val columnLeft = restLeft + ((0 - restLeft) * progress).roundToInt()
        val columnWidth = restWidth + ((areaWidth - restWidth) * progress).roundToInt()
        val cardWidth = (columnWidth - 2 * pad).coerceIn(0, readable)
        val finalWidth = (areaWidth - 2 * pad).coerceIn(0, readable)
        val inset = bottomInset.roundToPx()
        // The cards behind peek out above the open card only while that leaves it room for its header, a little of its
        // thread and its actions (the keyboard is up, the window is short): else they are not drawn, never overlapped.
        val wantedStrips = strips.roundToPx()
        val stripsFit =
            OpenCardLayout.stripsFit(
                areaHeight.toFloat(),
                inset.toFloat(),
                wantedStrips.toFloat(),
                MinOpenCardHeight.roundToPx().toFloat(),
            )
        val stripsPx = if (stripsFit) wantedStrips else 0
        geometry.stripsVisible = stripsFit
        val room = (areaHeight - inset - stripsPx).coerceAtLeast(0)
        val measurable = measurables.firstOrNull()
        // The height the card would have fully open: asked of its intrinsic height at its final width (once per
        // animation: a child is measured only once per pass), and read off the card itself at every layout while it is
        // settled, so content that changes is followed.
        var settled: androidx.compose.ui.layout.Placeable? = null
        val key = finalWidth * 31 + room
        if (measurable != null) {
            if (progress >= 1f) {
                val fully = measurable.measure(Constraints(finalWidth, finalWidth, 0, room))
                natural.key = key
                natural.px = fully.height
                settled = fully
            } else if (natural.key != key) {
                natural.key = key
                natural.px = measurable.maxIntrinsicHeight(finalWidth).coerceIn(0, room)
            }
        }
        val frame =
            OpenCardLayout.frame(
                progress = progress,
                areaHeightPx = areaHeight.toFloat(),
                bottomInsetPx = inset.toFloat(),
                browseHeightPx = browseHeight.roundToPx().toFloat(),
                fullHeightPx = natural.px.toFloat(),
                stripsPx = stripsPx.toFloat(),
                placement = placement,
            )
        geometry.topPx = frame.topPx
        geometry.heightPx = frame.heightPx
        val placeable =
            settled ?: measurable?.measure(
                Constraints.fixed(cardWidth, frame.heightPx.roundToInt().coerceAtLeast(0)),
            )
        val x = columnLeft + (columnWidth - (placeable?.width ?: cardWidth)) / 2
        layout(areaWidth, areaHeight) { placeable?.place(x, frame.topPx.roundToInt()) }
    }
}
