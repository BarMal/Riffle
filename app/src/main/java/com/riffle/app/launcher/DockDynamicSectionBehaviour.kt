package com.riffle.app.launcher

import com.riffle.core.domain.launcher.cards.AppStageId

/**
 * What a tap on a dynamic dock entry means, which is not the same question in every view mode.
 *
 * A sealed pair rather than a lambda so the answer is a value the dock can compare, remember and
 * be tested against, instead of a function identity that changes on every recomposition.
 */
internal sealed interface DockDynamicSectionBehaviour {
    /** No stage to bring forward, so a tap leaves the launcher for the app. */
    data object LaunchApp : DockDynamicSectionBehaviour

    /**
     * The app's cards are already on the launcher, so a tap brings that stage forward rather than
     * leaving for the app -- opening the app is what the user was trying to avoid by having its
     * content on the home screen at all.
     *
     * Carries the selected stage as its key rather than its [AppStageId] because the key is what
     * the hoisted interaction context already holds, and reading it there saves the dock having to
     * reconcile the whole stage snapshot a second time just to know which entry is showing.
     */
    data class SelectStage(val selectedStageKey: String? = null) : DockDynamicSectionBehaviour
}

/** The stage an entry stands for. Every dynamic entry is one app on one profile, so this is total. */
internal fun DockNotificationCardState.dockDynamicEntryStageId(): AppStageId =
    AppStageId(packageName = group.packageName, profileId = group.profileId)

/** What to dispatch when this entry is tapped, or `null` when there is nothing to dispatch. */
internal fun DockDynamicSectionBehaviour.actionFor(entry: DockNotificationCardState): LauncherShellAction? =
    when (this) {
        // An app the launcher cannot resolve has nothing to launch -- the entry stays, badged and
        // readable, because the notification is real even when the app behind it has gone.
        DockDynamicSectionBehaviour.LaunchApp -> entry.app?.identity?.let(LauncherShellAction::LaunchApp)
        // Deliberately not gated on the app resolving: a stage is built from notifications, so one
        // exists to bring forward whether or not the launcher found an installed app for it.
        is DockDynamicSectionBehaviour.SelectStage ->
            LauncherShellAction.SelectAppStage(entry.dockDynamicEntryStageId())
    }

/** Whether this entry is the one currently showing, which only stage selection has an answer to. */
internal fun DockDynamicSectionBehaviour.isSelected(entry: DockNotificationCardState): Boolean =
    when (this) {
        DockDynamicSectionBehaviour.LaunchApp -> false
        is DockDynamicSectionBehaviour.SelectStage ->
            selectedStageKey != null && selectedStageKey == adaptiveStageStageKey(entry.dockDynamicEntryStageId())
    }
