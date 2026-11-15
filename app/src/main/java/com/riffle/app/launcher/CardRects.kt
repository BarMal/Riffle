package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Where the Stream's cards (a stack's front card and strips, an open card) and the row of quick actions stand on
 * screen,
 * for Home's grid to keep its apps out from under them ("Around the cards"). Each card reports its coordinates when it
 * is
 * laid out; the rectangles are worked out when they are read ([rectsPx], in the draw phase of Home's cells), so a card
 * moved by a graphics layer (an open stack) is where it is now, and a moving stack redraws the cells and recomposes
 * nothing. Transient.
 */
@Stable
internal class CardRects {
    private val entries = mutableStateMapOf<Any, LayoutCoordinates>()
    private val version = mutableIntStateOf(0)

    fun report(
        key: Any,
        coordinates: LayoutCoordinates,
    ) {
        if (entries[key] !== coordinates) entries[key] = coordinates
        // A card that moved is laid out again and reports again: that is what the cells redraw on.
        version.intValue += 1
    }

    fun remove(key: Any) {
        entries.remove(key)
        covers.remove(key)
    }

    fun clear() {
        entries.clear()
        covers.clear()
    }

    /** A rectangle that hides the grid under it without being a card (the Dock's body, the open card's room). */
    class Cover(val coordinates: LayoutCoordinates, val withOpenCard: Boolean)

    private val covers = mutableStateMapOf<Any, Cover>()

    fun reportCover(
        key: Any,
        coordinates: LayoutCoordinates,
        withOpenCard: Boolean,
    ) {
        val known = covers[key]
        if (known == null || known.coordinates !== coordinates || known.withOpenCard != withOpenCard) {
            covers[key] = Cover(coordinates, withOpenCard)
        }
        version.intValue += 1
    }

    /** Every attached cover's rectangle in the root's coordinates (px), and whether it comes in with the open card. */
    fun coversPx(): List<Pair<Rect, Boolean>> {
        version.intValue
        return covers.values.filter { it.coordinates.isAttached }.map { cover ->
            val c = cover.coordinates
            val a = c.localToRoot(Offset.Zero)
            val b = c.localToRoot(Offset(c.size.width.toFloat(), c.size.height.toFloat()))
            Rect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y)) to cover.withOpenCard
        }
    }

    /** Every attached card's rectangle in the root's coordinates (px). */
    fun rectsPx(): List<Rect> {
        version.intValue
        return entries.values.filter { it.isAttached }.map { coordinates ->
            val a = coordinates.localToRoot(Offset.Zero)
            val b = coordinates.localToRoot(Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
            Rect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y))
        }
    }
}

/** The cards Home's grid keeps clear of, or null (no Stream). */
internal val LocalCardRects = compositionLocalOf<CardRects?> { null }

/** Reports this node as a card (or the quick actions row) Home's apps are hidden under; a no-op without a Stream. */
@Composable
internal fun Modifier.reportsCardRect(rects: CardRects? = LocalCardRects.current): Modifier {
    if (rects == null) return this
    val key = remember { Any() }
    DisposableEffect(rects, key) { onDispose { rects.remove(key) } }
    return onGloballyPositioned { coordinates -> rects.report(key, coordinates) }
}

/**
 * Reports this node as a cover: the grid under it fades out whatever the "apps behind the cards" setting is (to the
 * peek opacity with apps behind the cards, nothing with them around), with the Dock's extended body or the open card's
 * room. [withOpenCard]: the cover comes in as the open card grows (its room), not at once (the Dock's body).
 */
@Composable
internal fun Modifier.reportsGridCover(
    withOpenCard: Boolean,
    active: Boolean = true,
    rects: CardRects? = LocalCardRects.current,
): Modifier {
    if (rects == null) return this
    val key = remember { Any() }
    DisposableEffect(rects, key) { onDispose { rects.remove(key) } }
    DisposableEffect(rects, key, active) {
        if (!active) rects.remove(key)
        onDispose {}
    }
    return if (active) {
        onGloballyPositioned { coordinates -> rects.reportCover(key, coordinates, withOpenCard) }
    } else {
        this
    }
}
