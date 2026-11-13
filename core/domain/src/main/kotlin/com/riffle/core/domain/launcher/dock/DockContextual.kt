package com.riffle.core.domain.launcher.dock

import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamPage
import com.riffle.core.domain.launcher.stream.StreamPageRef
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.ref

/**
 * The Dock's two modes (ADR 0003, Amendment 3). The Home mode holds pinned apps, a divider and live apps and does not
 * scroll beyond what it does today. The contextual mode, which is on while the Stream shows, holds the same icons in
 * the
 * same order and then a divider and one tile per Stream page that is not an app; its row scrolls.
 */
enum class DockMode {
    HOME,
    CONTEXTUAL,
    ;

    companion object {
        fun of(streamShown: Boolean): DockMode = if (streamShown) CONTEXTUAL else HOME
    }
}

/**
 * The Stream's pages in the Dock's order (ADR 0003, Amendment 3: stacks are ordered as the Dock is).
 *
 * The Dock's order is: the live apps (newest first, the first `liveAppSlots` of them), now playing, then the contextual
 * tiles in the Stream layout's order. The pages that have an icon follow it, so swiping the pager walks the Dock
 * left to
 * right. Pages that have no icon of their own (a live app past the Dock's slots, the pages of a feed after the first)
 * stay next to the ones that do.
 */
object DockPageOrder {
    /**
     * [pages] (the planner's, in the layout's section order) in the Dock's order. [liveGroupKeys] are the notification
     * groups of the live apps the Dock shows, in the Dock's order; [nowPlaying] says the now playing entry is shown,
     * which stands for the Now page and puts it right after the live apps instead of at its place in the layout.
     */
    fun order(
        pages: List<StreamPage>,
        liveGroupKeys: List<String>,
        nowPlaying: Boolean,
    ): List<StreamPage> {
        val appPages = pages.filter { page -> page.ref().isAppPage }
        val live = liveGroupKeys.mapNotNull { key -> appPages.firstOrNull { page -> page.groupKey == key } }
        val otherApps = appPages - live.toSet()
        val now = if (nowPlaying) pages.filter { page -> page.section == StreamSection.NOW } else emptyList()
        val rest = pages - appPages.toSet() - now.toSet()
        return live + otherApps + now + rest
    }

    /** The keys of the live apps the Dock shows, in its order, from the entries of [DockDynamicSide.entries]. */
    fun liveGroupKeys(entries: List<DockDynamicSideEntry>): List<String> =
        entries.filterIsInstance<DockDynamicSideEntry.LiveApp>().map(DockDynamicSideEntry.LiveApp::groupKey)
}

/** Which Dock icon is ringed: the one of the page that is open. */
object DockRing {
    /**
     * The key of the entry to ring while [current] is open, or null when no entry stands for it (a live app past the
     * Dock's slots; nothing open). [entryKeys] are the keys of the entries the Dock draws. Now is the now playing entry
     * while that is shown, else its tile. A pinned app with nothing live has no page, so is never ringed.
     */
    fun entryKeyFor(
        current: StreamPageRef?,
        entryKeys: Collection<String>,
    ): String? {
        if (current == null) return null
        val candidates =
            when {
                current.isAppPage ->
                    listOf(DockDynamicSideEntry.liveAppKey(requireNotNull(current.groupKey)))
                current.section == StreamSection.NOW ->
                    listOf(DockDynamicSideEntry.NOW_PLAYING_KEY, DockDynamicSideEntry.contextTileKey(current.section))
                else -> listOf(DockDynamicSideEntry.contextTileKey(current.section))
            }
        return candidates.firstOrNull { key -> key in entryKeys }
    }
}

/** Moving a contextual tile along the row, which moves its section in the Stream layout. */
object DockTileMoves {
    /**
     * The layout move ([com.riffle.core.domain.launcher.stream.StreamLayoutAction.Move]'s delta) that takes [section]'s
     * tile one place [direction] (-1 earlier, +1 later) along the row, or null when it is already at that end. Sections
     * that draw no tile (Notifications while it has live apps, hidden ones) are stepped over, so the move is always
     * visible. [tileSections] are the sections that have a tile, in the Dock's order.
     */
    fun deltaFor(
        layout: StreamLayout,
        tileSections: List<StreamSection>,
        section: StreamSection,
        direction: Int,
    ): Int? {
        val tileIndex = tileSections.indexOf(section)
        val neighbour = tileSections.getOrNull(tileIndex + direction)
        if (tileIndex < 0 || neighbour == null || direction == 0) return null
        val order = layout.sections.map { it.section }
        return order.indexOf(neighbour) - order.indexOf(section)
    }
}

/** Where a Dock row's scroll goes to keep one item in view. Plain pixels along the row's axis. */
object DockRunScroll {
    /**
     * The scroll offset that brings the item spanning [itemStartPx]..[itemEndPx] (in the row's content) into the
     * [viewportPx]-long viewport, leaving [marginPx] around it where there is room: [scrollPx] itself when it is
     * already
     * in view, the least move otherwise, never outside 0..[maxScrollPx]. An item longer than the viewport is aligned at
     * its start.
     */
    fun toReveal(
        scrollPx: Int,
        viewportPx: Int,
        maxScrollPx: Int,
        itemStartPx: Int,
        itemEndPx: Int,
        marginPx: Int = 0,
    ): Int {
        val margin = marginPx.coerceIn(0, ((viewportPx - (itemEndPx - itemStartPx)) / 2).coerceAtLeast(0))
        val wantedStart = itemStartPx - margin
        val wantedEnd = itemEndPx + margin
        val target =
            when {
                wantedEnd - wantedStart >= viewportPx -> wantedStart
                wantedStart < scrollPx -> wantedStart
                wantedEnd > scrollPx + viewportPx -> wantedEnd - viewportPx
                else -> scrollPx
            }
        return target.coerceIn(0, maxScrollPx.coerceAtLeast(0))
    }
}

/**
 * Where the items of the contextual mode's one scrolling row lie along its run, in dp, from the start of the row's
 * content (inside the Dock's own padding): the pinned block, a divider, the live entries, a divider, then the tiles.
 * Pure arithmetic over the sizes the Dock draws with, so the scroll that keeps the open icon in view needs no
 * measuring, and the test says where each icon is.
 */
object DockRunGeometry {
    /** One item's extent: [startDp] up to (not including) [endDp]. */
    data class Extent(val startDp: Int, val endDp: Int)

    /**
     * The row: [entries] are the extents of the dynamic entries in order (live apps and now playing first, then the
     * tiles), [contentDp] the row's whole length.
     */
    data class Run(val entries: List<Extent>, val contentDp: Int)

    /**
     * The contextual row. [staticDp] is the pinned block's length, [liveCount] and [tileCount] how many live entries
     * and
     * tiles follow it, [entryDp] one entry's length and [spacingDp] the gap the row leaves between entries, which also
     * sits either side of the divider between the live entries and the tiles ([dividerDp] wide). The divider between
     * the
     * pinned block and the entries is [dividerDp] wide with no gap, as in the Home mode.
     */
    fun contextual(
        staticDp: Int,
        liveCount: Int,
        tileCount: Int,
        entryDp: Int,
        spacingDp: Int,
        dividerDp: Int,
    ): Run {
        val count = liveCount + tileCount
        if (count <= 0) return Run(emptyList(), staticDp.coerceAtLeast(0))
        val first = staticDp.coerceAtLeast(0) + dividerDp
        val entries = ArrayList<Extent>(count)
        var cursor = first
        repeat(count) { index ->
            if (index > 0) cursor += spacingDp
            if (index == liveCount && liveCount > 0) cursor += dividerDp + spacingDp
            entries += Extent(cursor, cursor + entryDp)
            cursor += entryDp
        }
        return Run(entries, cursor)
    }
}
