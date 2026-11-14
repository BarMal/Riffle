package com.riffle.app.launcher.stream

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.riffle.core.domain.launcher.stream.StackGeometry
import kotlin.math.roundToInt

/**
 * Lays out a stack's cards from its position (`StackGeometry`): each child tagged with [stackSlot] is the whole card's
 * slot, sized to the part of the card that shows (its top strip, or all of it) and placed with its top edge where the
 * geometry says, bottom-anchored, so the front card sits on the bottom of the area (just above the Dock) and older
 * cards peek above it. The position is read while measuring, so a drag or a coast re-lays out these children and
 * nothing else (no recomposition per frame). The card is drawn scaled from the centre of its top edge, with the card's
 * own opacity and the stack's fade.
 *
 * The slot clips its content to the size it is measured at, so the card is the real card cut off, not a copy of its
 * strip: the strip and the whole card are the same pixels at every position.
 */
@Composable
internal fun StackLayout(
    state: StreamStackState,
    geometry: StackGeometry,
    cardWidth: Dp,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val position = state.position
        val fade = state.fade
        val slotWidth = cardWidth.roundToPx().coerceAtMost(width)
        val left = (width - slotWidth) / 2
        val bottom = height - bottomInset.roundToPx()
        val slots =
            measurables.mapNotNull { measurable ->
                val index = measurable.layoutId as? Int ?: return@mapNotNull null
                val r = index - position
                val frame = geometry.frame(r)
                if (!frame.isVisible) return@mapNotNull null
                val clip = (frame.clipHeightDp * density).roundToInt().coerceAtLeast(1)
                Triple(r, frame, measurable.measure(Constraints.fixed(slotWidth, clip)))
            }
        layout(width, height) {
            // The card furthest back is drawn first, so the front card is on top.
            slots.sortedByDescending { it.first }.forEach { (_, frame, placeable) ->
                placeable.placeWithLayer(left, bottom - (frame.topDp * density).roundToInt()) {
                    alpha = frame.alpha * fade
                    scaleX = frame.scale
                    scaleY = frame.scale
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
            }
        }
    }
}

/** Tags a child of [StackLayout] as the slot of card [index]. */
internal fun Modifier.stackSlot(index: Int): Modifier = layoutId(index)
