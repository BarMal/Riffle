package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.cards.AppStage
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
 * Cards mode's entries: the stages, all of them.
 *
 * Every stage, including those of apps pinned to the dock's static side. The de-duplication the
 * dock's composition applies elsewhere exists so one app is not shown twice meaning the same thing,
 * and here the two sides do not mean the same thing: the static one opens the app, this one brings
 * its cards forward. Leaving a pinned app out would make its stage the only one with no way to
 * reach it.
 *
 * Fed from the reconciled stage snapshot rather than from notification groups, because a stage is
 * not always a notification -- a pinned stage with nothing waiting, or one carrying media, is still
 * somewhere the user can go.
 */
internal fun List<AppStage>.stageDockDynamicEntries(
    state: LauncherShellState,
    selectedStageId: AppStageId?,
    badgeCounts: Map<AppStageId, Int>,
    allNotificationsSelected: Boolean = false,
): List<DockDynamicEntry> {
    // No stages means nothing to merge, so the page that merges them is not offered either.
    if (isEmpty()) return emptyList()
    return map { stage ->
        val label = stageLabel(stage.id, state)
        val count = badgeCounts[stage.id] ?: 0
        DockDynamicEntry(
            key = "stage:${stage.id.profileId.value}:${stage.id.packageName.value}",
            label = label,
            identity = stageAppIdentity(stage.id, state),
            badgeCount = count,
            isSelected = stage.id == selectedStageId,
            contentDescription = dockStageEntryContentDescription(label, count, stage.id == selectedStageId),
            intent = DockDynamicEntryIntent.Dispatch(LauncherShellAction.SelectAppStage(stage.id)),
        )
    } +
        allNotificationsDockDynamicEntry(
            isSelected = allNotificationsSelected,
            badgeCount = badgeCounts.values.sum(),
        )
}

/**
 * The merged view of every stage's notifications, kept last as the rail kept it.
 *
 * It has no app behind it, so it draws the initial fallback the section already uses for an app the
 * launcher cannot resolve rather than borrowing some stage's icon.
 */
private fun allNotificationsDockDynamicEntry(
    isSelected: Boolean,
    badgeCount: Int,
): DockDynamicEntry =
    DockDynamicEntry(
        key = "all-notifications",
        label = ALL_NOTIFICATIONS_LABEL,
        identity = null,
        badgeCount = badgeCount,
        isSelected = isSelected,
        contentDescription =
            dockStageEntryContentDescription(ALL_NOTIFICATIONS_LABEL, badgeCount, isSelected),
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
