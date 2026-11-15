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
import com.riffle.core.domain.launcher.stream.StreamCardSizes
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

    /** Where the front card's lower edge lies now; the strips of the cards in front of it start under it. */
    var bottomPx by mutableFloatStateOf(0f)

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
    val presence: OpenCardPresence = OpenCardPresence(),
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
    /** The stack's layout: each card's browse height and where its slot stands at a position. */
    val stackGeometry: StackGeometry,
    val strips: Dp,
    /** The distance between the strips of the cards behind: the sizes' pitch. */
    val pitchDp: Float,
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
    /** The room the page keeps under the collapsed stack (the quick actions' strip): its bottom stands above it. */
    val restFooter: Dp = 0.dp,
    /** How many cards show as strips on each side of the open card at most. */
    val peeks: Int = StreamCardStackSpec.VISIBLE_PEEKS,
    /** The cards in front of the open card (newer, lower index), drawn as header strips under it. */
    val belowStrips: List<OpenLayerStrip> = emptyList(),
    /** How far the finger has dragged past the open card's body edge toward a neighbour (the cue's fill). */
    val cue: HandoffCueState = HandoffCueState(),
)

/** A card in front of the open one, shown as its header strip under the open card (tap: open it). */
@Stable
internal class OpenLayerStrip(
    val index: Int,
    val id: String,
    val content: @Composable (Modifier) -> Unit,
)

/** The layout identity of a strip, so it is told from the open card of the same index. */
private data class StripSlotId(val index: Int)

/**
 * How present an open card is, 0 (a strip) to 1 (whole): written by the layer's layout, read where the card draws, so
 * the body fades and the header scales with the motion and nothing recomposes.
 */
@Stable
internal class OpenCardPresence {
    var openness by mutableFloatStateOf(1f)
}

/**
 * The drag past the open card's scrolling body toward a neighbour, as the cue draws it: [fraction] is how far the
 * finger has gone toward the hand-off threshold, 0 to 1 (negative: toward the card in front, below). Written by the
 * hand-off, read in draw.
 */
@Stable
internal class HandoffCueState {
    var fraction by mutableFloatStateOf(0f)
}

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
                            val pitch = layer.pitchDp * density
                            val rank = if (tap.y < top) kotlin.math.ceil((top - tap.y) / pitch).toInt() else 0
                            val strips = (layer.strips.toPx() / pitch + STRIP_COUNT_EPSILON).toInt()
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

/** Float slack when counting how many strips the strips' room holds (three pitches may divide to 2.9999). */
private const val STRIP_COUNT_EPSILON = 0.01f

/** The least height an open card is given before the cards behind it stop peeking out above it. */
private val MinOpenCardHeight = StreamCardSizes.OPEN_CARD_MIN_DP.dp

/** The content height of an open card, kept between frames so an animation measures the content once. */
private class OpenCardNaturalHeight {
    var key = 0
    var px = 0
}

/**
 * Lays out the layer's open cards in the Stream's area. Each is a whole card at every frame, positioned and sized from
 * the stack's position and the open card's progress: where its slot in the stack stands when it is a card away from the
 * front (a strip above it, or a strip below it), where [OpenCardLayout] puts it when it is the front card (its browse
 * rectangle at progress 0, its open place at 1, the rectangle in between), and blended by how near the front it is
 * ([OpenCardBlend.openness]). So moving the stack from one card to the next is one motion: the card that arrives grows
 * into its open card as the one that leaves shrinks back to its strip below. The glass, edge and rounded corners are
 * drawn at the size a card has now, so none is cut off by a clip, and each card's body fades with how open it is
 * ([OpenCardPresence]) so two bodies are never drawn through each other. The cards behind it show as strips above it
 * (the stack beneath moves to the open card's top) and the cards in front of it as strips below it ([OpenLayerStrip]),
 * from the moment it opens. Only the cards are measured per frame, nothing around them.
 *
 * The open card grows from the collapsed front card's own rectangle (its bottom is [OpenCardLayer.restFooter] above the
 * area's, its height its browse height) and shrinks back to exactly it. The cards stand in the column [restColumn]
 * widened toward the whole area by the progress (the whole area when null), centred in it with the page's side
 * padding. The stack beneath is told where the front card's top lies ([OpenCardFrameGeometry.topPx]) so the cards
 * behind move out of its way.
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
    val stackGeometry = layer.stackGeometry
    val geometry = layer.geometry
    Layout(
        content = {
            layer.cards.forEach { card ->
                key(card.id) { card.content(Modifier.layoutId(card.index)) }
            }
            layer.belowStrips.forEach { strip ->
                key("strip:${strip.id}") { strip.content(Modifier.layoutId(StripSlotId(strip.index))) }
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
        val pitch = layer.pitchDp * density
        val belowGap = BelowStripGap.roundToPx().toFloat()
        // Where the collapsed front card's bottom edge stands: the page keeps its footer under it.
        val restBottom = (areaHeight - inset - layer.restFooter.roundToPx()).toFloat()
        val bottom = areaHeight - inset
        val minCard = MinOpenCardHeight.roundToPx().toFloat()

        // What stands above and below the open card at an index: the strips of the cards behind and in front of it.
        fun aboveOf(index: Int): Float = (layer.count - 1 - index).coerceIn(0, layer.peeks) * pitch

        // The strips behind it stand only while they leave an open card its room (the keyboard is up, the window is
        // short); the strips of the cards in front of it are as many as then fit under it.
        fun stripsFitAt(index: Int): Boolean =
            OpenCardLayout.stripsFit(areaHeight.toFloat(), inset.toFloat(), aboveOf(index), minCard)

        fun aboveShown(index: Int): Float = if (stripsFitAt(index)) aboveOf(index) else 0f

        fun belowCountAt(index: Int): Int {
            if (!stripsFitAt(index)) return 0
            val room = areaHeight - inset - aboveShown(index) - minCard - belowGap
            return minOf(index.coerceAtLeast(0), layer.peeks, (room / pitch).toInt().coerceAtLeast(0))
        }

        fun belowShown(index: Int): Float {
            val n = belowCountAt(index)
            return if (n == 0) 0f else n * pitch + belowGap
        }

        fun roomOf(index: Int): Int = (bottom - belowShown(index) - aboveShown(index)).roundToInt().coerceAtLeast(0)

        // A card's browse height is its own: where it stands as the front card of the resting stack.
        fun browsePxOf(index: Int): Float = stackGeometry.heightOf(index) * density

        fun browseTopOf(index: Int): Float = restBottom - browsePxOf(index)
        val nearest = StackFling.nearest(position, layer.count)
        geometry.stripsVisible = stripsFitAt(nearest)

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
        // blend gives them, from the height their content would have. A card is never shorter than its browse card.
        val whole = HashMap<Int, androidx.compose.ui.layout.Placeable>()
        entries.filter { progress >= 1f && it.openness >= 1f }.forEach { entry ->
            val room = roomOf(entry.index)
            val least = browsePxOf(entry.index).roundToInt().coerceAtMost(room)
            val fully = entry.measurable.measure(Constraints(finalWidth, finalWidth, least, room))
            naturals.getOrPut(entry.id) { OpenCardNaturalHeight() }.also {
                it.key = finalWidth * 31 + room
                it.px = fully.height
            }
            whole[entry.index] = fully
        }

        fun openFrameOf(entry: Entry): OpenCardLayout.Frame {
            val natural = naturals.getOrPut(entry.id) { OpenCardNaturalHeight() }
            val room = roomOf(entry.index)
            val key = finalWidth * 31 + room
            if (natural.key != key) {
                natural.key = key
                natural.px = entry.measurable.maxIntrinsicHeight(finalWidth).coerceIn(0, room)
            }
            return OpenCardLayout.frame(
                progress = progress,
                areaHeightPx = areaHeight.toFloat(),
                bottomInsetPx = inset.toFloat(),
                browseHeightPx = browsePxOf(entry.index),
                fullHeightPx = natural.px.toFloat(),
                stripsPx = aboveShown(entry.index),
                placement = layer.placement,
                restBottomPx = restBottom,
                belowPx = belowShown(entry.index),
            )
        }
        // Where the front card's top lies, so the stack beneath can move its slots to it: between the two cards the
        // position lies between, each at its open top, or at its browse top when it does not open.
        val frames = entries.associate { it.index to openFrameOf(it) }
        val frontTop =
            if (layer.reducedMotion) {
                frames[nearest]?.topPx ?: browseTopOf(nearest)
            } else {
                OpenCardBlend.followTop(position) { index -> frames[index]?.topPx ?: browseTopOf(index) }
            }
        geometry.topPx = frontTop
        // The open frame's lower edge at this position, where the strips below it start.
        val frontBottom =
            if (layer.reducedMotion) {
                frames[nearest]?.let { it.topPx + it.heightPx } ?: restBottom
            } else {
                OpenCardBlend.followTop(position) { index ->
                    frames[index]?.let { it.topPx + it.heightPx }
                        ?: (restBottom + (bottom - belowShown(index) - restBottom) * progress)
                }
            }
        geometry.bottomPx = frontBottom
        // How far the resting stack is moved so that its front slot's top (the front height at this position above
        // the bottom) lies at the open frame's top.
        val shift = frontTop - (restBottom - stackGeometry.frontHeightAt(position) * density)
        val placed =
            entries.filter { it.openness > 0f }.sortedBy { it.openness }.map { entry ->
                val open = frames.getValue(entry.index)
                val w = if (layer.reducedMotion) 1f else entry.openness
                // Where the card stands as a strip: above the open card (the stack's own slot, moved with it), or, when
                // it is in front of the position, below it, on the slot the strips below keep.
                val slotTop: Float
                val slotHeight: Float
                if (entry.r < 0f && !layer.reducedMotion) {
                    slotTop = frontBottom + belowGap + (-1f - entry.r) * pitch
                    slotHeight = pitch
                } else {
                    val slot = stackGeometry.frame(entry.index, position)
                    slotTop = restBottom - slot.topDp * density + shift
                    slotHeight = slot.visibleHeightDp * density
                }
                val top = OpenCardBlend.lerp(slotTop, open.topPx, w)
                val height = OpenCardBlend.lerp(slotHeight, open.heightPx, w)
                val alpha =
                    if (layer.reducedMotion || entry.r < 0f) {
                        1f
                    } else {
                        stackGeometry.frame(
                            entry.index,
                            position,
                        ).alpha
                    }
                layer.cards.firstOrNull { it.index == entry.index }?.presence?.openness = w
                // Edges are rounded, not the height: top and bottom each move one way only, never a pixel back.
                val topPx = top.roundToInt()
                val placeable =
                    whole[entry.index]
                        ?: entry.measurable.measure(
                            Constraints.fixed(cardWidth, ((top + height).roundToInt() - topPx).coerceAtLeast(0)),
                        )
                Triple(placeable, topPx.toFloat(), alpha)
            }
        // The cards in front of the open one, as strips below it, each on a slot of its own that does not move while
        // the open card changes (the one that arrives or leaves takes the slot of the one that leaves or arrives).
        val stripsBelow =
            if (layer.reducedMotion || belowCountAt(nearest) == 0) {
                emptyList()
            } else {
                measurables.mapNotNull { measurable ->
                    val index = (measurable.layoutId as? StripSlotId)?.index ?: return@mapNotNull null
                    val j = position - 1f - index
                    if (j < 0f) return@mapNotNull null
                    val fade = (belowCountAt(nearest) - j).coerceIn(0f, 1f) * progress
                    if (fade <= 0f) return@mapNotNull null
                    val top = frontBottom + belowGap + j * pitch
                    Triple(measurable.measure(Constraints.fixed(cardWidth, pitch.roundToInt())), top, fade)
                }
            }
        layout(areaWidth, areaHeight) {
            stripsBelow.forEach { (placeable, top, alpha) ->
                val x = columnLeft + (columnWidth - placeable.width) / 2
                placeable.placeWithLayer(x, top.roundToInt()) { this.alpha = alpha * layer.stack.fade }
            }
            placed.forEach { (placeable, top, alpha) ->
                val x = columnLeft + (columnWidth - placeable.width) / 2
                placeable.placeWithLayer(x, top.roundToInt()) { this.alpha = alpha * layer.stack.fade }
            }
        }
    }
}

/** The gap between the open card and the strips of the cards in front of it. */
private val BelowStripGap = 4.dp
