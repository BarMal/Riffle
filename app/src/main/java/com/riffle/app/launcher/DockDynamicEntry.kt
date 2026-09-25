package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppIdentity

/**
 * One entry on the dock's dynamic side: what it shows, and what a tap on it does.
 *
 * The modes put genuinely different things here -- grid mode lists the apps with something waiting,
 * Cards lists its stage selector -- so the entry carries its own intent rather than the section
 * inferring one from a shared entry type. That keeps the section itself free of any notion of stages
 * or launching: it lays out entries, marks the selected one, and reports taps (Decision 2 in
 * docs/product/modes-dock-handle-and-cards-plan.md).
 */
internal data class DockDynamicEntry(
    val key: String,
    val label: String,
    val identity: AppIdentity?,
    val badgeCount: Int,
    val isSelected: Boolean,
    val contentDescription: String,
    val intent: DockDynamicEntryIntent?,
)

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

/**
 * Grid mode's entries: the apps the dock is not already showing that have notifications waiting.
 *
 * Nothing to select, because there is nowhere for the app's content to be shown on the launcher --
 * a tap leaves for the app, and one whose app the launcher cannot resolve has nothing to leave for.
 */
internal fun List<DockNotificationCardState>.launchableDockDynamicEntries(): List<DockDynamicEntry> =
    map { card ->
        val label = dockNotificationCardLabel(card)
        DockDynamicEntry(
            key = "notifications:${card.group.packageName.value}:${card.group.profileId.value}",
            label = label,
            identity = card.app?.identity,
            badgeCount = card.group.count,
            isSelected = false,
            contentDescription = dockNotificationCardContentDescription(card = card, label = label),
            intent =
                card.app?.identity
                    ?.let(LauncherShellAction::LaunchApp)
                    ?.let(DockDynamicEntryIntent::Dispatch),
        )
    }
