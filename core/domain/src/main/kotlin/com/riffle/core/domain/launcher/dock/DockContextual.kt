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
 * The Dock's order is: the pinned apps (each one's shortcuts page, unless it has a live page), the live apps (newest
 * first, the first `liveAppSlots` of them), now playing, then the contextual tiles in the Stream layout's order. The
 * pages that have an icon follow it, so swiping the pager walks the Dock left to right. Pages that have no icon of
 * their own (a live app past the Dock's slots, the pages of a feed after the first) stay next to the ones that do.
 */
object DockPageOrder {
    /**
     * [pages] (the planner's, in the layout's section order) in the Dock's order. [liveGroupKeys] are the notification
     * groups of the live apps the Dock shows, in the Dock's order; [nowPlaying] says the now playing entry is shown,
     * which stands for the Now page and puts it right after the live apps instead of at its place in the layout.
     * [pinnedGroupKeys] are the pinned apps' keys in the Dock's order: each one's page (live, else shortcuts) sits at
     * its icon's place in the run, so a pinned app that is live is not also in the live apps.
     */
    fun order(
        pages: List<StreamPage>,
        liveGroupKeys: List<String>,
        nowPlaying: Boolean,
        pinnedGroupKeys: List<String> = emptyList(),
    ): List<StreamPage> {
        val shortcutPages = pages.filter { page -> page.ref().isShortcutsPage }
        val appPages = pages.filter { page -> page.ref().isLiveAppPage }
        // A pinned app has one icon: its live page when it has one, else its shortcuts page, at the icon's place.
        val pinned =
            pinnedGroupKeys.mapNotNull { key ->
                appPages.firstOrNull { page -> page.groupKey == key }
                    ?: shortcutPages.firstOrNull { page -> page.groupKey == key }
            }
        val live =
            liveGroupKeys.mapNotNull { key -> appPages.firstOrNull { page -> page.groupKey == key } } - pinned.toSet()
        val otherApps = appPages - live.toSet() - pinned.toSet()
        val now = if (nowPlaying) pages.filter { page -> page.section == StreamSection.NOW } else emptyList()
        val rest = pages - appPages.toSet() - shortcutPages.toSet() - now.toSet()
        return pinned + (shortcutPages - pinned.toSet()) + live + otherApps + now + rest
    }

    /** The keys of the live apps the Dock shows, in its order, from the entries of [DockDynamicSide.entries]. */
    fun liveGroupKeys(entries: List<DockDynamicSideEntry>): List<String> =
        entries.filterIsInstance<DockDynamicSideEntry.LiveApp>().map(DockDynamicSideEntry.LiveApp::groupKey)
}

/** Which Dock icon is ringed: the one of the page that is open. */
object DockRing {
    /**
     * The key of the entry to ring while [current] is open, or null when no entry stands for it (a live app past the
     * Dock's slots; nothing open). [entryKeys] are the keys of the entries the Dock draws, the pinned apps' included
     * ([DockDynamicSideEntry.pinnedAppKey]). Now is the now playing entry while that is shown, else its tile. A pinned
     * app is ringed on its shortcuts page, and on its live page when no live entry stands for it.
     */
    fun entryKeyFor(
        current: StreamPageRef?,
        entryKeys: Collection<String>,
    ): String? {
        if (current == null) return null
        val candidates =
            when {
                current.isShortcutsPage ->
                    listOf(DockDynamicSideEntry.pinnedAppKey(requireNotNull(current.groupKey)))
                current.isLiveAppPage ->
                    listOf(
                        DockDynamicSideEntry.liveAppKey(requireNotNull(current.groupKey)),
                        DockDynamicSideEntry.pinnedAppKey(requireNotNull(current.groupKey)),
                    )
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
    /** How much of the neighbour the row leaves showing past an item it had to scroll to, in dp. */
    const val PEEK_MARGIN_DP = 24

    /**
     * The scroll offset that brings the item spanning [itemStartPx]..[itemEndPx] (in the row's content) into the
     * [viewportPx]-long viewport. An item that is already wholly in view leaves the row where it is ([scrollPx], never
     * a nudge for the margin). Otherwise the row moves by the least that shows the item whole, plus [marginPx] on the
     * side it came from (the side the move goes toward) so the neighbour peeks, where there is more to see there; the
     * result is never outside 0..[maxScrollPx]. An item longer than the viewport is aligned at its start.
     */
    fun toReveal(
        scrollPx: Int,
        viewportPx: Int,
        maxScrollPx: Int,
        itemStartPx: Int,
        itemEndPx: Int,
        marginPx: Int = 0,
    ): Int {
        if (itemStartPx >= scrollPx && itemEndPx <= scrollPx + viewportPx) return scrollPx
        val margin = marginPx.coerceIn(0, ((viewportPx - (itemEndPx - itemStartPx)) / 2).coerceAtLeast(0))
        val target =
            when {
                itemEndPx - itemStartPx >= viewportPx -> itemStartPx
                itemStartPx < scrollPx -> itemStartPx - margin
                else -> itemEndPx + margin - viewportPx
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
    /**
     * The contextual mode's one item size along the run, pinned icons, live apps and tiles alike: the 48 dp touch
     * target. The pinned icons keep the Dock's own (Home) size in the Home mode and only draw at this one in the row.
     */
    const val CONTEXTUAL_ITEM_DP = 48

    /** The fewest whole items the contextual row shows across its viewport when the Dock's run can hold them. */
    const val MIN_VISIBLE_ITEMS = 6

    /** The least gap the row closes up to for [MIN_VISIBLE_ITEMS]; the Dock's own spacing is its ceiling. */
    const val MIN_CONTEXTUAL_SPACING_DP = 4

    /** One item's extent: [startDp] up to (not including) [endDp]. */
    data class Extent(val startDp: Int, val endDp: Int)

    /**
     * The row: [entries] are the extents of the dynamic entries in order (live apps and now playing first, then the
     * tiles), [contentDp] the row's whole length including [endPaddingDp], [pinned] the extents of the pinned slots.
     */
    data class Run(
        val entries: List<Extent>,
        val contentDp: Int,
        val pinned: List<Extent> = emptyList(),
        val endPaddingDp: Int = 0,
    ) {
        /** Every item of the row in order: the pins, then the entries. */
        val items: List<Extent> get() = pinned + entries

        /**
         * The offsets (dp) at which an item stands at the leading edge: each item's start, and 0. These are the
         * places a programmatic scroll may stop.
         */
        val stopsDp: List<Int> get() = (listOf(0) + items.map(Extent::startDp)).distinct()
    }

    /**
     * How the contextual row spaces its items for a viewport of [viewportDp] along the run: items are
     * [CONTEXTUAL_ITEM_DP] and the gap is the Dock's own [preferredSpacingDp], closed up (to no less than
     * [MIN_CONTEXTUAL_SPACING_DP]) until [MIN_VISIBLE_ITEMS] whole items fit. A viewport too short for that at the
     * minimum gap shows as many as fit.
     */
    fun contextualSpacingDp(
        viewportDp: Int,
        preferredSpacingDp: Int,
    ): Int {
        val preferred = preferredSpacingDp.coerceAtLeast(0)
        val floor = minOf(MIN_CONTEXTUAL_SPACING_DP, preferred)
        val room = viewportDp - MIN_VISIBLE_ITEMS * CONTEXTUAL_ITEM_DP
        val fitting = if (room <= 0) 0 else room / (MIN_VISIBLE_ITEMS - 1)
        return fitting.coerceIn(floor, preferred.coerceAtLeast(floor))
    }

    /**
     * The contextual row. [staticDp] is the pinned block's length ([staticCount] pins in it), [liveCount] and
     * [tileCount] how many live entries and tiles follow it, [entryDp] one entry's length and [spacingDp] the gap the
     * row leaves between entries, which also sits either side of the divider between the live entries and the tiles
     * ([dividerDp] wide). The divider between the pinned block and the entries is [dividerDp] wide with no gap, as in
     * the Home mode.
     *
     * With [viewportDp] given, the row ends so that scrolling to its end leaves a whole item at the leading edge:
     * trailing padding is added up to the next item start (so the last stop is an item's, never a sliver of one).
     */
    @Suppress("LongParameterList")
    fun contextual(
        staticDp: Int,
        liveCount: Int,
        tileCount: Int,
        entryDp: Int,
        spacingDp: Int,
        dividerDp: Int,
        staticCount: Int = 0,
        viewportDp: Int? = null,
    ): Run {
        val count = liveCount + tileCount
        val pinned = pinnedExtents(staticCount, staticDp, entryDp)
        if (count <= 0) return Run(emptyList(), staticDp.coerceAtLeast(0), pinned)
        val first = staticDp.coerceAtLeast(0) + dividerDp
        val entries = ArrayList<Extent>(count)
        var cursor = first
        repeat(count) { index ->
            if (index > 0) cursor += spacingDp
            if (index == liveCount && liveCount > 0) cursor += dividerDp + spacingDp
            entries += Extent(cursor, cursor + entryDp)
            cursor += entryDp
        }
        val padding = viewportDp?.let { endPaddingFor(pinned + entries, cursor, it) } ?: 0
        return Run(entries, cursor + padding, pinned, padding)
    }

    private fun pinnedExtents(
        count: Int,
        staticDp: Int,
        itemDp: Int,
    ): List<Extent> {
        if (count <= 0 || itemDp <= 0) return emptyList()
        val gap = if (count > 1) ((staticDp - count * itemDp) / (count - 1)).coerceAtLeast(0) else 0
        return List(count) { index -> Extent(index * (itemDp + gap), index * (itemDp + gap) + itemDp) }
    }

    /** Padding that makes the last scroll offset (content - viewport) the start of an item. */
    private fun endPaddingFor(
        items: List<Extent>,
        contentDp: Int,
        viewportDp: Int,
    ): Int {
        val maxScroll = contentDp - viewportDp
        val stop = items.map(Extent::startDp).firstOrNull { start -> start >= maxScroll }
        return if (maxScroll <= 0 || stop == null) 0 else stop - maxScroll
    }
}
