package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.cards.AppStageId

/**
 * One entry on the dock's dynamic side: what it shows, and what a tap on it does.
 *
 * The two view modes put genuinely different things here -- grid mode lists the apps with something
 * waiting, Cards mode lists the stages -- so the entry carries its own action rather than the
 * section inferring one from a shared entry type. That also keeps the section itself free of any
 * notion of stages or launching: it lays out entries.
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
 * What activating an entry means.
 *
 * Most entries resolve to a shell action, but the merged All-notifications page is not a stage and
 * has no action to send -- it is a choice about what the Cards surface is showing. Naming both as
 * values keeps the entry comparable, which a callback per entry would not be: the section is redrawn
 * whenever a notification lands, and entries that differ only by lambda identity would never skip.
 */
internal sealed interface DockDynamicEntryIntent {
    data class Dispatch(val action: LauncherShellAction) : DockDynamicEntryIntent

    data object ShowAllNotifications : DockDynamicEntryIntent
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

/**
 * Cards mode's entries: the apps a notification arrived for that the dock is not already showing.
 *
 * The same de-duplicated notification list grid mode draws -- a pinned app is on the static side,
 * so it is not repeated here -- but a tap brings the app's stage forward rather than leaving for the
 * app, because in Cards the content is already on the launcher. A notification-backed app always has
 * a stage, so the selection always lands.
 *
 * "Dynamic" means "this has something waiting", not "here is every stage": a pinned app's stage is
 * reached from its static icon, and an app with nothing waiting is not shown at all.
 */
internal fun List<DockNotificationCardState>.stageSelectingDockDynamicEntries(selectedStageId: AppStageId?) =
    map { card ->
        val stageId = AppStageId(card.group.packageName, card.group.profileId)
        val label = dockNotificationCardLabel(card)
        val isSelected = stageId == selectedStageId
        DockDynamicEntry(
            key = "cards:${card.group.packageName.value}:${card.group.profileId.value}",
            label = label,
            identity = card.app?.identity,
            badgeCount = card.group.count,
            isSelected = isSelected,
            contentDescription = dockStageEntryContentDescription(label, card.group.count, isSelected),
            intent = DockDynamicEntryIntent.Dispatch(LauncherShellAction.SelectAppStage(stageId)),
        )
    }

/**
 * The merged "All notifications" entry -- every stage's notifications at once, one destination.
 *
 * Offered only where opted in (per posture), and unlike a per-app entry it stands for no single app,
 * so it draws the initial-letter fallback rather than borrowing an icon. Activating it is a choice
 * about what the surface shows, not a stage selection, so it carries [ShowAllNotifications].
 */
internal fun allNotificationsDockDynamicEntry(
    isSelected: Boolean,
    badgeCount: Int,
): DockDynamicEntry =
    DockDynamicEntry(
        key = "all-notifications",
        label = ALL_NOTIFICATIONS_LABEL,
        identity = null,
        badgeCount = badgeCount,
        isSelected = isSelected,
        contentDescription = dockStageEntryContentDescription(ALL_NOTIFICATIONS_LABEL, badgeCount, isSelected),
        intent = DockDynamicEntryIntent.ShowAllNotifications,
    )

private const val ALL_NOTIFICATIONS_LABEL = "All notifications"

private fun dockStageEntryContentDescription(
    label: String,
    badgeCount: Int,
    isSelected: Boolean,
): String =
    buildList {
        add(label)
        if (badgeCount > 0) {
            add("$badgeCount ${if (badgeCount == 1) "card" else "cards"}")
        }
        add(if (isSelected) "Showing. Open stage" else "Open stage")
    }.joinToString(separator = ", ")
