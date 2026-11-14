package com.riffle.app.launcher

import com.riffle.app.R
import com.riffle.app.launcher.stream.StreamPageOption
import com.riffle.app.launcher.stream.glyph
import com.riffle.app.launcher.stream.titleRes
import com.riffle.core.domain.launcher.dock.DockDynamicSideEntry
import com.riffle.core.domain.launcher.dock.DockTileMoves
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamView

// The Dock's contextual mode, entry by entry (ADR 0003, Amendment 3): what the mode changes on a live app's entry, a
// contextual tile, and the page options a long-press offers.

/**
 * What the contextual mode changes on an app entry: it is ringed when its page is open, a tap switches the page instead
 * of launching the app, and its long-press menu leads with the page options instead of "Open in Stream" (the page is
 * what the Stream shows, and the Dock's tap already shows it).
 */
internal fun DockDynamicEntry.inMode(
    streamShown: Boolean,
    selected: Boolean,
    pageMenu: List<ShortcutContextMenuItem>,
    resources: android.content.res.Resources,
): DockDynamicEntry {
    if (!streamShown) return this
    // The ringed app's icon opens the app (a second tap); the other icons show their page.
    val showLabel =
        if (selected && identity != null && symbol == null) {
            openAppLabelFor(
                resources,
            )
        } else {
            openLabelFor(resources)
        }
    return copy(
        isSelected = selected,
        intent = DockDynamicEntryIntent.Delegate,
        openLabel = showLabel,
        showInStreamLabel = null,
        openInStreamMenuLabel = null,
        appMenu = pageMenu + appMenu,
    )
}

private fun DockDynamicEntry.openAppLabelFor(resources: android.content.res.Resources): String? =
    resources.getString(R.string.dock_live_app_open_action, label)

private fun DockDynamicEntry.openLabelFor(resources: android.content.res.Resources): String? =
    when (symbol) {
        is DockDynamicSymbol.Glyph -> resources.getString(R.string.dock_tile_show_action, label)
        else -> resources.getString(R.string.dock_live_app_show_action, label)
    }

/** A contextual tile: the section's glyph and name; a tap shows its page. */
internal fun DockDynamicSideEntry.ContextTile.contextTileEntry(
    resources: android.content.res.Resources,
): DockDynamicEntry {
    val name = resources.getString(section.titleRes())
    return DockDynamicEntry(
        key = key,
        label = name,
        identity = null,
        badgeCount = 0,
        isSelected = false,
        contentDescription = name,
        intent = DockDynamicEntryIntent.Delegate,
        symbol = DockDynamicSymbol.Glyph(section.glyph()),
    )
}

/**
 * The page options of an entry's page as long-press items (ADR 0003, Amendment 3: page options moved off the page's
 * header): the other way to show the section ("Show as list"), Move earlier and later where the tile can move, Hide,
 * and a
 * "Show" for each hidden section. Live apps and now playing offer Show as and Hide only: their order is the Dock's.
 */
internal fun pageOptionMenu(
    entry: DockDynamicSideEntry,
    layout: StreamLayout,
    tileSections: List<StreamSection>,
    resources: android.content.res.Resources,
    request: (StreamPageOption) -> Unit,
): List<ShortcutContextMenuItem> {
    val section =
        when (entry) {
            is DockDynamicSideEntry.ContextTile -> entry.section
            is DockDynamicSideEntry.NowPlaying -> StreamSection.NOW
            is DockDynamicSideEntry.LiveApp -> StreamSection.NOTIFICATIONS
        }
    val otherView = StreamView.entries.first { view -> view != layout.viewOf(section) }

    fun move(direction: Int): Int? =
        DockTileMoves.deltaFor(layout, tileSections, section, direction)
            .takeIf { entry is DockDynamicSideEntry.ContextTile }
    val items =
        listOfNotNull(
            ShortcutContextMenuItem(
                label = resources.getString(R.string.dock_tile_show_as, resources.getString(otherView.titleRes())),
                onSelect = { request(StreamPageOption.ShowAs(section, otherView)) },
            ),
            move(-1)?.let { delta ->
                ShortcutContextMenuItem(
                    label = resources.getString(R.string.dock_tile_move_earlier),
                    onSelect = { request(StreamPageOption.Move(section, delta)) },
                )
            },
            move(1)?.let { delta ->
                ShortcutContextMenuItem(
                    label = resources.getString(R.string.dock_tile_move_later),
                    onSelect = { request(StreamPageOption.Move(section, delta)) },
                )
            },
            ShortcutContextMenuItem(
                label = resources.getString(R.string.stream_hide_section),
                onSelect = { request(StreamPageOption.Hide(section)) },
            ),
        )
    val hidden =
        layout.sections.filter { sectionLayout -> !sectionLayout.enabled }.map { sectionLayout ->
            ShortcutContextMenuItem(
                label =
                    resources.getString(
                        R.string.stream_show_hidden_section,
                        resources.getString(sectionLayout.section.titleRes()),
                    ),
                onSelect = { request(StreamPageOption.Show(sectionLayout.section)) },
            )
        }
    return items + hidden
}
