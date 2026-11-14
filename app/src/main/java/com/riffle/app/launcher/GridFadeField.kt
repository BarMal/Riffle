package com.riffle.app.launcher

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
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

    companion object {
        /** A cell drawn below this opacity is not there to be pressed. */
        const val INPUT_ALPHA_FLOOR = 0.05f
    }

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
 * One grid cell's place in the fade: where its middle stands (read after layout) and the opacity the fade gives it
 * there. Read in draw and semantics blocks, so a page scrolling past the column changes it without recomposing.
 */
@Stable
internal class GridFadeCell(
    private val field: GridFadeField,
) {
    private val x = mutableFloatStateOf(Float.NaN)
    private val y = mutableFloatStateOf(Float.NaN)

    fun place(
        xPx: Float,
        yPx: Float,
    ) {
        x.floatValue = xPx
        y.floatValue = yPx
    }

    /** What this cell is drawn at; 1 until it has been laid out. */
    fun alpha(): Float {
        val cx = x.floatValue
        val cy = y.floatValue
        return if (cx.isNaN() || cy.isNaN()) 1f else field.alphaAtPx(cx, cy)
    }

    /** Whether the cell is faded out so far that it takes no input (see [GridFadeField.INPUT_ALPHA_FLOOR]). */
    fun blocksInput(): Boolean = alpha() < GridFadeField.INPUT_ALPHA_FLOOR
}

/** The cell's place in the fade Home draws, or null (folded with nothing behind the cards, or no Stream). */
@Composable
internal fun rememberGridFadeCell(): GridFadeCell? {
    val field = LocalGridFade.current ?: return null
    return remember(field) { GridFadeCell(field) }
}

/**
 * Fades this cell by where it stands, so the grid flows into the column's fade instead of ending in an edge. Reads
 * its position after layout and the field in the draw layer, so a page scrolling past the column fades smoothly with
 * no recomposition; nothing is offscreen and nothing is blurred.
 */
internal fun Modifier.gridFade(cell: GridFadeCell?): Modifier =
    if (cell == null) {
        this
    } else {
        this
            .onGloballyPositioned { coordinates ->
                val origin = coordinates.positionInRoot()
                cell.place(origin.x + coordinates.size.width / 2f, origin.y + coordinates.size.height / 2f)
            }
            .graphicsLayer { alpha = cell.alpha() }
            // What the cell is drawn at, for a test to read: a layer's alpha is not in the semantics tree.
            .semantics { gridFadeAlpha = cell.alpha() }
    }

/**
 * A cell that is faded out (under the column, or behind an open card) takes no input: a press on it neither taps nor
 * long-presses the app that is drawn at nothing. Drawn last in the cell, as a sibling over its content; present only
 * while the cell is below [GridFadeField.INPUT_ALPHA_FLOOR], so a visible cell is untouched and a page's drag still
 * reaches the pager (this swallows taps and presses, and consumes no movement).
 */
@Composable
internal fun GridFadeInputBlock(
    cell: GridFadeCell?,
    modifier: Modifier = Modifier,
) {
    if (cell == null) return
    val blocked by remember(cell) { derivedStateOf { cell.blocksInput() } }
    if (blocked) {
        Box(
            modifier.pointerInput(
                Unit,
            ) { detectTapGestures(onTap = {}, onLongPress = {}) }.testTag(GRID_FADE_BLOCK_TEST_TAG),
        )
    }
}

internal const val GRID_FADE_BLOCK_TEST_TAG = "grid-fade-input-block"

/** The opacity the grid fade gives a cell; defined only on the cells of an unfolded Home. */
internal val GridFadeAlphaKey = SemanticsPropertyKey<Float>("GridFadeAlpha")
private var SemanticsPropertyReceiver.gridFadeAlpha by GridFadeAlphaKey
