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

    /** "Around the cards": an app under a card, or the quick actions row, is fully hidden (the stack's cards below). */
    var around: Boolean by mutableStateOf(false)

    /** Where the cards stand; read while drawing. */
    var cards: CardRects? = null

    /** Pixels per dp. */
    var density: Float = 1f

    companion object {
        /** A cell drawn below this opacity is not there to be pressed. */
        const val INPUT_ALPHA_FLOOR = 0.05f

        /** From this much of an open card's growth the cells behind it take no input. */
        const val OPEN_BLOCKS_INPUT_FROM = 0.5f
    }

    /** The opacity of an item centred at ([xPx], [yPx]) in the root's coordinates, [widthPx] by [heightPx] big. */
    fun alphaAtPx(
        xPx: Float,
        yPx: Float,
        widthPx: Float = 0f,
        heightPx: Float = 0f,
    ): Float {
        val presence = presence()
        val faded =
            if (presence <= 0f) {
                1f
            } else {
                fadeAt(xPx / density, yPx / density, presence)
            }
        val rects = if (around && presence > 0f) cards?.rectsPx().orEmpty() else emptyList()
        if (rects.isEmpty()) return coveredAlpha(faded, xPx, yPx, widthPx, heightPx, presence)
        val cell =
            StreamColumnRect(
                (xPx - widthPx / 2f) / density,
                (yPx - heightPx / 2f) / density,
                (xPx + widthPx / 2f) / density,
                (yPx + heightPx / 2f) / density,
            )
        val under =
            rects.map {
                StreamColumnRect(
                    it.left / density,
                    it.top / density,
                    it.right / density,
                    it.bottom / density,
                )
            }
        // The cards' hiding comes in with the Stream as the fade does.
        val keep = 1f - presence.coerceIn(0f, 1f) * (1f - StreamGridFade.aroundFactor(cell, under))
        return coveredAlpha(faded * keep, xPx, yPx, widthPx, heightPx, presence)
    }

    /**
     * The column's fade, or with a card open the fade of what is drawn: the column's rectangle and feather ease into
     * the open card's rectangle (and its tabs) and the short feather as the card opens, so the grid is hidden under the
     * card and not around a column taller than it (round 7: "expanding a card obscures apps it doesn't cover").
     */
    private fun fadeAt(
        xDp: Float,
        yDp: Float,
        presence: Float,
    ): Float {
        val openNow = open().coerceIn(0f, 1f)
        val column = column()
        val region = if (openNow > 0f) openRegionDp() else null
        if (region == null || column == null) return StreamGridFade.alpha(xDp, yDp, column, peekThrough, presence)
        val shown =
            StreamColumnRect(
                lerp(column.left, region.left, openNow),
                lerp(column.top, region.top, openNow),
                lerp(column.right, region.right, openNow),
                lerp(column.bottom, region.bottom, openNow),
            )
        val feather = lerp(StreamGridFade.FEATHER_DP, StreamGridFade.CARD_FEATHER_DP, openNow)
        return StreamGridFade.alpha(xDp, yDp, shown, peekThrough, presence, feather)
    }

    private fun lerp(
        from: Float,
        to: Float,
        t: Float,
    ): Float = from + (to - from) * t

    /**
     * Whether a cell centred at ([xPx], [yPx]) is behind an open card (the card and its room, else the column): it
     * is drawn at the peek opacity but is not there to be pressed, the card is over it.
     */
    fun behindOpenCard(
        xPx: Float,
        yPx: Float,
    ): Boolean {
        if (presence() <= 0f || open() < OPEN_BLOCKS_INPUT_FROM) return false
        val region = openRegionDp() ?: column() ?: return false
        val x = xPx / density
        val y = yPx / density
        return x >= region.left && x <= region.right && y >= region.top && y <= region.bottom
    }

    /** The rectangle of the open card, its tabs and the room under them down to the Dock (dp); null: none reported. */
    private fun openRegionDp(): StreamColumnRect? {
        val rects = cards?.coversPx().orEmpty().filter { it.second }.map { it.first }
        if (rects.isEmpty()) return null
        return StreamColumnRect(
            rects.minOf { it.left } / density,
            rects.minOf { it.top } / density,
            rects.maxOf { it.right } / density,
            rects.maxOf { it.bottom } / density,
        )
    }

    /**
     * [alpha] held under the covers (the Dock's extended body, the open card's room): nothing shows there with the apps
     * around the cards, and no more than the peek opacity with apps behind them.
     */
    private fun coveredAlpha(
        alpha: Float,
        xPx: Float,
        yPx: Float,
        widthPx: Float,
        heightPx: Float,
        presence: Float,
    ): Float {
        val covers = cards?.coversPx().orEmpty()
        if (covers.isEmpty() || presence <= 0f) return alpha
        val cell =
            StreamColumnRect(
                (xPx - widthPx / 2f) / density,
                (yPx - heightPx / 2f) / density,
                (xPx + widthPx / 2f) / density,
                (yPx + heightPx / 2f) / density,
            )
        val openNow = open().coerceIn(0f, 1f)
        var result = alpha
        covers.forEach { (rect, withOpenCard) ->
            val under =
                StreamColumnRect(rect.left / density, rect.top / density, rect.right / density, rect.bottom / density)
            val factor = StreamGridFade.aroundFactor(cell, listOf(under))
            val strength = presence.coerceIn(0f, 1f) * (if (withOpenCard) openNow else 1f)
            val floor = if (around) 0f else StreamGridFade.floorAlpha(true)
            val held = floor + factor * (1f - floor)
            result = minOf(result, 1f - strength * (1f - held))
        }
        return result
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
    private val width = mutableFloatStateOf(0f)
    private val height = mutableFloatStateOf(0f)

    fun place(
        xPx: Float,
        yPx: Float,
        widthPx: Float = 0f,
        heightPx: Float = 0f,
    ) {
        x.floatValue = xPx
        y.floatValue = yPx
        width.floatValue = widthPx
        height.floatValue = heightPx
    }

    /** What this cell is drawn at; 1 until it has been laid out. */
    fun alpha(): Float {
        val cx = x.floatValue
        val cy = y.floatValue
        return if (cx.isNaN() || cy.isNaN()) 1f else field.alphaAtPx(cx, cy, width.floatValue, height.floatValue)
    }

    /** Whether the cell is faded out so far that it takes no input (see [GridFadeField.INPUT_ALPHA_FLOOR]). */
    fun blocksInput(): Boolean =
        alpha() < GridFadeField.INPUT_ALPHA_FLOOR ||
            (!x.floatValue.isNaN() && field.behindOpenCard(x.floatValue, y.floatValue))
}

/** The cell's place in the fade Home draws, or null (folded with nothing behind the cards, or no Stream). */
@Composable
internal fun rememberGridFadeCell(): GridFadeCell? {
    CountRecomposition(GRID_CELL_PROBE_KEY)
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
                cell.place(
                    origin.x + coordinates.size.width / 2f,
                    origin.y + coordinates.size.height / 2f,
                    coordinates.size.width.toFloat(),
                    coordinates.size.height.toFloat(),
                )
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

/** What the recomposition probe counts a grid cell under. */
internal const val GRID_CELL_PROBE_KEY = "grid-cell"

internal const val GRID_FADE_BLOCK_TEST_TAG = "grid-fade-input-block"

/** The opacity the grid fade gives a cell; defined only on the cells of an unfolded Home. */
internal val GridFadeAlphaKey = SemanticsPropertyKey<Float>("GridFadeAlpha")
private var SemanticsPropertyReceiver.gridFadeAlpha by GridFadeAlphaKey
