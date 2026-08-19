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
    val action: LauncherShellAction?,
)

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
            action = card.app?.identity?.let(LauncherShellAction::LaunchApp),
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
): List<DockDynamicEntry> =
    map { stage ->
        val label = stageLabel(stage.id, state)
        val count = badgeCounts[stage.id] ?: 0
        DockDynamicEntry(
            key = "stage:${stage.id.profileId.value}:${stage.id.packageName.value}",
            label = label,
            identity = stageAppIdentity(stage.id, state),
            badgeCount = count,
            isSelected = stage.id == selectedStageId,
            contentDescription = dockStageEntryContentDescription(label, count, stage.id == selectedStageId),
            action = LauncherShellAction.SelectAppStage(stage.id),
        )
    }

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
