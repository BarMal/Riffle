package com.riffle.app.launcher

import androidx.compose.ui.graphics.vector.ImageVector
import com.riffle.core.domain.launcher.apps.AppIdentity

/**
 * One entry on the dock's dynamic side: what it shows, and what a tap on it does. A live app or now playing entry also
 * says what showing it in the Stream means ([showInStreamLabel]): a press that starts on it, a long-press item, an
 * accessibility action and Shift+Enter all do that.
 *
 * The dynamic side lists live apps and now playing, and in the contextual mode a tile for each Stream page that is not
 * an app (ADR 0003, Amendment 3), so the entry carries its own intent rather than the section inferring one from a
 * shared entry type. That keeps the section itself free of any notion of the Stream or launching: it lays out entries,
 * marks the selected one, and reports taps (Decision 2 in docs/product/modes-dock-handle-and-cards-plan.md).
 */
internal data class DockDynamicEntry(
    val key: String,
    val label: String,
    val identity: AppIdentity?,
    val badgeCount: Int,
    val isSelected: Boolean,
    val contentDescription: String,
    val intent: DockDynamicEntryIntent?,
    /** What to draw over (or instead of) the app icon: a contextual tile's glyph, now playing's play mark. */
    val symbol: DockDynamicSymbol? = null,
    /**
     * What a tap says to a screen reader ("Open Gmail", or "Show Gmail" in the contextual mode) and, in the Home
     * mode, the
     * custom action that shows the entry's page in the Stream ("Show Gmail in Stream"), the non-gesture way of the
     * press
     * that starts on an icon.
     */
    val openLabel: String? = null,
    val showInStreamLabel: String? = null,
    /**
     * The long-press menu of an app entry: its shortcuts, App info and Hide, under "Open in Stream" (which the tile
     * adds). Empty means no menu beyond that.
     */
    val appMenu: List<ShortcutContextMenuItem> = emptyList(),
    /** The long-press menu's "Open in Stream", localised by whoever built the entry. */
    val openInStreamMenuLabel: String? = null,
)

/** A mark the dock draws itself on a dynamic entry. */
internal sealed interface DockDynamicSymbol {
    /** Now playing: a small play mark in the corner of the playing app's icon. */
    data object NowPlaying : DockDynamicSymbol

    /** A contextual tile (Now, Agenda, Recents, ...): the section's [glyph], drawn in place of an app icon. */
    data class Glyph(val glyph: ImageVector) : DockDynamicSymbol
}

/**
 * What activating an entry means, as the dock sees it.
 *
 * Either a shell action the dock can send as-is, or [Delegate]: the dock does not know what the
 * entry means and hands its [DockDynamicEntry.key] back to whoever supplied it -- the active mode's
 * interpreter. Values rather than a callback per entry keep entries comparable: the section is
 * redrawn whenever a notification lands, and entries that differ only by lambda identity would never
 * skip.
 */
internal sealed interface DockDynamicEntryIntent {
    data class Dispatch(val action: LauncherShellAction) : DockDynamicEntryIntent

    /** Hand the entry's key back to the mode that supplied it; the dock itself attaches no meaning. */
    data object Delegate : DockDynamicEntryIntent
}

/** Whether this is a contextual tile (a Stream page that is not an app), which only the contextual mode has. */
internal val DockDynamicEntry.isContextTile: Boolean get() = symbol is DockDynamicSymbol.Glyph
