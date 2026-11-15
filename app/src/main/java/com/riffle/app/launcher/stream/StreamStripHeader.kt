package com.riffle.app.launcher.stream

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.riffle.app.launcher.designsystem.RiffleSpacing

/**
 * Strips show whole lines only (device fixes, round 7, item 1): a card behind the front one shows its header (the app,
 * the title, the age) and nothing under it, so no first line of text is cut by the card in front. The header ends at
 * [StripHeaderBottom]; the card's body starts below that (the header's row plus the air under it).
 */
internal val StripHeaderBottom: Dp = StreamHeaderTopPadding + StreamMinTarget + RiffleSpacing.xs

/** Where a card of a stack stands, so its content can be clipped to its header while it is a strip. */
internal class StripPlace(
    val index: Int,
    val position: () -> Float,
) {
    /** The card's height as last laid out (px), kept for tests: a plain field, never read to draw. */
    var fullPx: Float = 0f
}

internal val LocalStripPlace = staticCompositionLocalOf<StripPlace?> { null }

/**
 * How much of a card of height [fullPx] shows while the stack is at [position]: all of it as the front card, the header
 * ([headerPx]) once it is a whole card behind, easing between so a moving stack never jumps. Pure.
 */
internal fun stripVisiblePx(
    index: Int,
    position: Float,
    fullPx: Float,
    headerPx: Float,
): Float {
    val behind = (index - position).coerceIn(0f, 1f)
    return if (headerPx >= fullPx) fullPx else fullPx - (fullPx - headerPx) * behind
}

/** Test hook: the height (px) of the card's content that shows now (the whole card in front). */
internal val StripVisibleHeightKey = SemanticsPropertyKey<Float>("StripVisibleHeight")

/** Clips the card's content to its header while it is a strip: read in draw, nothing recomposes. */
@Composable
internal fun Modifier.headerOnlyAsStrip(): Modifier {
    val place = LocalStripPlace.current ?: return this
    val headerPx = StripHeaderBottom.value * LocalDensity.current.density
    return this
        .onSizeChanged { place.fullPx = it.height.toFloat() }
        .drawWithContent {
            val visible = stripVisiblePx(place.index, place.position(), size.height, headerPx)
            if (visible >= size.height) {
                drawContent()
            } else {
                clipRect(bottom = visible) { this@drawWithContent.drawContent() }
            }
        }
        .semantics {
            this[StripVisibleHeightKey] = stripVisiblePx(place.index, place.position(), place.fullPx, headerPx)
        }
}
