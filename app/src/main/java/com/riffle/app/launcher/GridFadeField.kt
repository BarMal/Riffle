package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import com.riffle.core.domain.launcher.stream.StreamColumnRect
import com.riffle.core.domain.launcher.stream.StreamGridFade

/**
 * The fade of Home's grid beneath the Stream's column (ADR 0003, Amendment 2): where the column stands, whether apps
 * peek through it, and how far the fade has come in. Home's cells read it when they draw, so the column moving or
 * widening redraws them without recomposing anything. The rule itself is [StreamGridFade] (pure, tested).
 */
@Stable
internal class GridFadeField {
    /**
     * The column in dp, in the coordinates of the Home destination's root; null when no column stands. A function, so
     * that what the cells read while drawing (the column widening as a card opens) is read there, not in composition.
     */
    var column: () -> StreamColumnRect? by mutableStateOf({ null })

    /** Apps show faintly through the column (the setting), or nothing does. */
    var peekThrough: Boolean by mutableStateOf(true)

    /** 0: no fade, 1: the column in place; a function for the same reason as [column]. */
    var presence: () -> Float by mutableStateOf({ 0f })

    /** 0 at rest, 1 with a card fully open: the apps peeking through go out behind it. */
    var open: () -> Float by mutableStateOf({ 0f })

    /** Pixels per dp. */
    var density: Float = 1f

    /** The opacity of an item centred at ([xPx], [yPx]) in the root's coordinates. */
    fun alphaAtPx(
        xPx: Float,
        yPx: Float,
    ): Float {
        val presence = presence()
        if (presence <= 0f) return 1f
        return StreamGridFade.alpha(xPx / density, yPx / density, column(), peekThrough, presence, open())
    }
}

/** The fade Home's cells read; null (folded, or no Stream) leaves the grid as it is. */
internal val LocalGridFade = staticCompositionLocalOf<GridFadeField?> { null }

/**
 * Fades this cell by where it stands, so the grid flows into the column's fade instead of ending in an edge. Reads
 * its position after layout and the field in the draw layer, so a page scrolling past the column fades smoothly with
 * no recomposition; nothing is offscreen and nothing is blurred.
 */
@Composable
internal fun Modifier.gridFade(): Modifier {
    val field = LocalGridFade.current ?: return this
    val centre = remember { mutableFloatStateOf(Float.NaN) to mutableFloatStateOf(Float.NaN) }
    return this
        .onGloballyPositioned { coordinates ->
            val origin = coordinates.positionInRoot()
            centre.first.floatValue = origin.x + coordinates.size.width / 2f
            centre.second.floatValue = origin.y + coordinates.size.height / 2f
        }
        .graphicsLayer { alpha = field.alphaOf(centre) }
        // What the cell is drawn at, for a test to read: a layer's alpha is not in the semantics tree.
        .semantics { gridFadeAlpha = field.alphaOf(centre) }
}

private fun GridFadeField.alphaOf(centre: Pair<MutableFloatState, MutableFloatState>): Float {
    val x = centre.first.floatValue
    val y = centre.second.floatValue
    return if (x.isNaN() || y.isNaN()) 1f else alphaAtPx(x, y)
}

/** The opacity the grid fade gives a cell; defined only on the cells of an unfolded Home. */
internal val GridFadeAlphaKey = SemanticsPropertyKey<Float>("GridFadeAlpha")
private var SemanticsPropertyReceiver.gridFadeAlpha by GridFadeAlphaKey
