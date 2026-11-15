package com.riffle.app.launcher.stream

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.riffle.app.launcher.LocalLauncherCardShape
import com.riffle.core.domain.launcher.stream.StackGeometry

/**
 * Where the card in front of a card stands, in that card's own (unscaled) coordinates: what is cut out of it so that
 * it tucks under the card in front. Pure geometry from the stack's frames, so it is tested without a screen.
 */
internal object StripCut {
    /** The card in front as a rectangle in the coordinates of the card behind it, and how much smaller or larger. */
    data class InFront(
        val left: Float,
        val top: Float,
        /** The card in front's size relative to the card behind's own scale: its outline is drawn this much larger. */
        val scale: Float,
        /** The card in front's own height, unscaled, in px. */
        val heightPx: Float,
    )

    /**
     * The card in front of the card at [index] with the stack at [position], or null when no card stands in front of
     * it (it is the front card at rest, or the card in front has sunk away): then nothing is cut.
     */
    fun inFrontOf(
        index: Int,
        position: Float,
        geometry: StackGeometry,
        cardWidthPx: Float,
        density: Float,
        stripPitchDp: Float = geometry.pitchDp,
    ): InFront? {
        val own = geometry.frame(index, position, stripPitchDp)
        val front = geometry.frame(index - 1, position, stripPitchDp)
        val cut = index > 0 && own.isVisible && front.isVisible && own.scale > 0f
        return if (cut) {
            val scale = front.scale / own.scale
            InFront(
                // Both cards are scaled about the centre of their top edge, which lies on the stack's centre line.
                left = cardWidthPx / 2f - cardWidthPx * scale / 2f,
                top = (own.topDp - front.topDp) * density / own.scale,
                scale = scale,
                heightPx = geometry.heightOf(index - 1) * density,
            )
        } else {
            null
        }
    }
}

/**
 * Draws a stack's card so that it disappears behind the card in front of it: the card is clipped to its own outline
 * less the outline of the card in front (a path difference), so its lower boundary follows the rounded top of that
 * card, with no gap and no straight cut, and the two translucent fills still never overlap (they would add up into a
 * darker band). The front card at rest has no card in front and is drawn unclipped, so its edge stays crisp. The
 * stack's position is read here, in the draw phase: a drag or a coast redraws the cut and recomposes nothing. No
 * offscreen layer, blur or blend mode: one clip.
 */
@Composable
internal fun Modifier.stripCut(
    index: Int,
    state: StreamStackState,
    geometry: StackGeometry,
    cardWidth: Dp,
    /** The distance between the strips now, read in draw (a card that is open slims it); null: the geometry's own. */
    stripPitchDp: (() -> Float)? = null,
): Modifier {
    val shape = LocalLauncherCardShape.current
    val paths = remember { StripCutPaths() }
    return drawWithContent {
        val widthPx = cardWidth.toPx()
        val inFront =
            StripCut.inFrontOf(
                index,
                state.position,
                geometry,
                widthPx,
                density,
                stripPitchDp?.invoke() ?: geometry.pitchDp,
            )
        if (inFront == null) {
            drawContent()
        } else {
            val ownSize = Size(widthPx, geometry.heightOf(index) * density)
            val cut = paths.cut(shape, ownSize, inFront, Size(widthPx, inFront.heightPx), layoutDirection, this)
            clipPath(cut) { this@drawWithContent.drawContent() }
        }
    }
}

/** The paths of one slot's cut, kept between frames so a moving stack allocates none. */
internal class StripCutPaths {
    private val own = Path()
    private val front = Path()
    private val result = Path()

    /** [shape] at [ownSize] less [shape] at [frontSize] placed and scaled as [inFront] says. */
    @Suppress("LongParameterList")
    fun cut(
        shape: Shape,
        ownSize: Size,
        inFront: StripCut.InFront,
        frontSize: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path {
        own.rewind()
        own.addOutlineOf(shape.createOutline(ownSize, layoutDirection, density), 0f, 0f, 1f)
        front.rewind()
        front.addOutlineOf(
            shape.createOutline(frontSize, layoutDirection, density),
            inFront.left,
            inFront.top,
            inFront.scale,
        )
        result.rewind()
        result.op(own, front, PathOperation.Difference)
        return result
    }
}

/** Adds [outline] scaled by [scale] about its origin and then moved to ([left], [top]). */
private fun Path.addOutlineOf(
    outline: Outline,
    left: Float,
    top: Float,
    scale: Float,
) {
    when (outline) {
        is Outline.Rectangle ->
            addRect(
                Rect(
                    left + outline.rect.left * scale,
                    top + outline.rect.top * scale,
                    left + outline.rect.right * scale,
                    top + outline.rect.bottom * scale,
                ),
            )
        is Outline.Rounded -> {
            val r = outline.roundRect

            fun CornerRadius.scaled() = CornerRadius(x * scale, y * scale)
            addRoundRect(
                RoundRect(
                    left = left + r.left * scale,
                    top = top + r.top * scale,
                    right = left + r.right * scale,
                    bottom = top + r.bottom * scale,
                    topLeftCornerRadius = r.topLeftCornerRadius.scaled(),
                    topRightCornerRadius = r.topRightCornerRadius.scaled(),
                    bottomRightCornerRadius = r.bottomRightCornerRadius.scaled(),
                    bottomLeftCornerRadius = r.bottomLeftCornerRadius.scaled(),
                ),
            )
        }
        is Outline.Generic -> {
            val moved = Path().apply { addPath(outline.path) }
            // Column-major: scale on the diagonal, the translation in the last column.
            moved.transform(
                Matrix(
                    floatArrayOf(scale, 0f, 0f, 0f, 0f, scale, 0f, 0f, 0f, 0f, 1f, 0f, left, top, 0f, 1f),
                ),
            )
            addPath(moved)
        }
    }
}
