package com.riffle.app.launcher.stream

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.dock.DockMotionConstants
import com.riffle.core.domain.launcher.dock.DockMotionTuning
import com.riffle.core.domain.launcher.settings.OpenCardPlacement
import com.riffle.core.domain.launcher.stream.OpenCardBlend
import com.riffle.core.domain.launcher.stream.OpenCardLayout
import com.riffle.core.domain.launcher.stream.OpenCardSpring
import com.riffle.core.domain.launcher.stream.OpenCardSpringState
import com.riffle.core.domain.launcher.stream.StackFling
import com.riffle.core.domain.launcher.stream.StackGeometry
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

    /** False while the room is too short for the cards behind to peek out above the open card: they are not drawn. */
    var stripsVisible by mutableStateOf(true)
}

/** One open card the layer draws: the card at stack position [index] (its id [id]) and how to compose it. */
@Stable
internal class OpenLayerCard(
    val index: Int,
    val id: String,
    /** The card itself; [Modifier] carries the layout identity the layer needs and must be the root's modifier. */
    val content: @Composable (Modifier) -> Unit,
)

/**
 * The open card as the Stream's one overlay: what the stack that has a card open hands to the surface to draw over the
 * whole of its area (see [OpenCardHolder.layer]). It is drawn outside the pager, so no page, column or host clips it:
 * the screen is the only edge it can meet. [cards] are the open cards of the cards the stack's position lies between
 * (one at rest, two while it moves from one to the next); each is drawn between its slot in the stack and its open
 * frame by how near the front it is.
 */
@Stable
@Suppress("LongParameterList")
internal class OpenCardLayer(
    val geometry: OpenCardFrameGeometry,
    val placement: OpenCardPlacement,
    val browseHeight: Dp,
    val strips: Dp,
    val bottomInset: Dp,
    /** The stack's position and fade, read in layout and draw so a drag moves the cards with no recomposition. */
    val stack: StreamStackState,
    val count: Int,
    val reducedMotion: Boolean,
    /** What moves the stack from the layer: the platform scrollable and the body's hand-off, on the layer's root. */
    val dragModifier: Modifier,
    val onClose: () -> Unit,
    /** A tap on the strip of the card [rank] places behind the open one (1 is the nearest): open that card. */
    val onStripTap: (rank: Int) -> Unit,
    val cards: List<OpenLayerCard>,
)

/**
 * Draws [OpenCardHolder.layer], when a stack has published one: a scrim over the stack (a tap beside the card closes it
 * and the cards behind it are not tapped under it) and the open cards ([OpenCardFrames]). A vertical drag anywhere on
 * it but in the card's scrolling body moves the stack ([OpenCardLayer.dragModifier]).
 */
@Composable
internal fun OpenCardLayerHost(
    cards: OpenCardHolder,
    restColumn: (() -> StreamColumnRect?)?,
    modifier: Modifier = Modifier,
) {
    val layer = cards.layer ?: return
    Box(modifier = modifier.fillMaxSize().padding(top = RiffleSpacing.s).then(layer.dragModifier)) {
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
        OpenCardFrames(
            motion = cards.motion,
            layer = layer,
            restColumn = restColumn,
            modifier = Modifier.matchParentSize(),
        )
    }
}

/** The least height an open card is given before the cards behind it stop peeking out above it. */
private val MinOpenCardHeight = 240.dp

/** The content height of an open card, kept between frames so an animation measures the content once. */
private class OpenCardNaturalHeight {
    var key = 0
    var px = 0
}

/**
 * Lays out the layer's open cards in the Stream's area. Each is a whole card at every frame, positioned and sized from
 * the stack's position and the open card's progress: where its slot in the stack stands when it is a card away from the
 * front (a strip, or sinking into the Dock), where [OpenCardLayout] puts it when it is the front card (its browse place
 * at progress 0, its open place at 1, the height in between), and blended by how near the front it is
 * ([OpenCardBlend.openness]). So moving the stack from one card to the next is one motion: the card that arrives grows
 * into its open card as the one that leaves shrinks back to its strip. The glass, edge and rounded corners are drawn at
 * the size a card has now, so none is cut off by a clip. Only the cards are measured per frame, nothing around them.
 * The cards stand in the column [restColumn] widened toward the whole area by the progress (the whole area when null),
 * centred in it with the page's side padding. The stack beneath is told where the front card's top lies
 * ([OpenCardFrameGeometry.topPx]) so the cards behind move out of its way.
 */
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
internal fun OpenCardFrames(
    motion: OpenCardMotionState,
    layer: OpenCardLayer,
    restColumn: (() -> StreamColumnRect?)?,
    modifier: Modifier = Modifier,
) {
    val naturals = remember(layer) { mutableMapOf<String, OpenCardNaturalHeight>() }
    val stackGeometry = remember(layer.browseHeight) { StackGeometry(layer.browseHeight.value) }
    val geometry = layer.geometry
    Layout(
        content = {
            layer.cards.forEach { card ->
                key(card.id) { card.content(Modifier.layoutId(card.index)) }
            }
        },
        modifier = modifier.graphicsLayer { alpha = motion.fade },
    ) { measurables, constraints ->
        val areaWidth = constraints.maxWidth
        val areaHeight = constraints.maxHeight
        val progress = motion.progress.coerceIn(0f, 1f)
        val position = layer.stack.position
        val pad = RiffleSpacing.l.roundToPx()
        val readable = StreamMaxReadableWidth.roundToPx()
        val rest = restColumn?.invoke()
        val restLeft = rest?.let { it.left.dp.roundToPx() } ?: 0
        val restWidth = rest?.let { (it.right.dp.roundToPx() - it.left.dp.roundToPx()).coerceAtLeast(0) } ?: areaWidth
        val columnLeft = restLeft + ((0 - restLeft) * progress).roundToInt()
        val columnWidth = restWidth + ((areaWidth - restWidth) * progress).roundToInt()
        val cardWidth = (columnWidth - 2 * pad).coerceIn(0, readable)
        val finalWidth = (areaWidth - 2 * pad).coerceIn(0, readable)
        val inset = layer.bottomInset.roundToPx()
        // The cards behind peek out above the open card only while that leaves it room for its header, a little of its
        // thread and its actions (the keyboard is up, the window is short): else they are not drawn, never overlapped.
        val wantedStrips = layer.strips.roundToPx()
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
        val browsePx = layer.browseHeight.roundToPx().toFloat()
        val bottom = areaHeight - inset
        val browseTop = bottom - browsePx
        val key = finalWidth * 31 + room
        val nearest = StackFling.nearest(position, layer.count)

        // Each open card: its place as the open card it is at this progress, and how near the front it is.
        class Entry(
            val measurable: androidx.compose.ui.layout.Measurable,
            val index: Int,
            val id: String,
            val r: Float,
            val openness: Float,
        )
        val entries =
            measurables.mapNotNull { measurable ->
                val index = measurable.layoutId as? Int ?: return@mapNotNull null
                val r = index - position
                // Reduced motion: no growth and no blend; the card nearest the front is the open one, and it fades.
                val openness =
                    if (layer.reducedMotion) {
                        if (index == nearest) {
                            1f
                        } else {
                            0f
                        }
                    } else {
                        OpenCardBlend.openness(r)
                    }
                Entry(measurable, index, "$index", r, openness)
            }
        // A card that stands whole in its open place is measured at the height its content has (once per layout pass: a
        // child is measured only once), so content that changes is followed; the others are measured at the height the
        // blend gives them, from the height their content would have.
        val whole = HashMap<Int, androidx.compose.ui.layout.Placeable>()
        entries.filter { progress >= 1f && it.openness >= 1f }.forEach { entry ->
            val fully = entry.measurable.measure(Constraints(finalWidth, finalWidth, 0, room))
            naturals.getOrPut(entry.id) { OpenCardNaturalHeight() }.also {
                it.key = key
                it.px = fully.height
            }
            whole[entry.index] = fully
        }

        fun openFrameOf(entry: Entry): OpenCardLayout.Frame {
            val natural = naturals.getOrPut(entry.id) { OpenCardNaturalHeight() }
            if (natural.key != key) {
                natural.key = key
                natural.px = entry.measurable.maxIntrinsicHeight(finalWidth).coerceIn(0, room)
            }
            return OpenCardLayout.frame(
                progress = progress,
                areaHeightPx = areaHeight.toFloat(),
                bottomInsetPx = inset.toFloat(),
                browseHeightPx = browsePx,
                fullHeightPx = natural.px.toFloat(),
                stripsPx = stripsPx.toFloat(),
                placement = layer.placement,
            )
        }
        // Where the front card's top lies, so the stack beneath can move its slots to it: between the two cards the
        // position lies between, each at its open top, or at its browse top when it does not open.
        val frames = entries.associate { it.index to openFrameOf(it) }
        val frontTop =
            if (layer.reducedMotion) {
                frames[nearest]?.topPx ?: browseTop
            } else {
                OpenCardBlend.followTop(position) { index -> frames[index]?.topPx ?: browseTop }
            }
        geometry.topPx = frontTop
        val shift = frontTop - browseTop
        val placed =
            entries.filter { it.openness > 0f }.sortedBy { it.openness }.map { entry ->
                val open = frames.getValue(entry.index)
                val slot = stackGeometry.frame(entry.r)
                val w = if (layer.reducedMotion) 1f else entry.openness
                val top = OpenCardBlend.lerp(bottom - slot.topDp * density + shift, open.topPx, w)
                val height = OpenCardBlend.lerp(slot.visibleHeightDp * density, open.heightPx, w)
                val alpha = if (layer.reducedMotion) 1f else slot.alpha
                val placeable =
                    whole[entry.index]
                        ?: entry.measurable.measure(Constraints.fixed(cardWidth, height.roundToInt().coerceAtLeast(0)))
                Triple(placeable, top, alpha)
            }
        layout(areaWidth, areaHeight) {
            placed.forEach { (placeable, top, alpha) ->
                val x = columnLeft + (columnWidth - placeable.width) / 2
                placeable.placeWithLayer(x, top.roundToInt()) { this.alpha = alpha * layer.stack.fade }
            }
        }
    }
}
