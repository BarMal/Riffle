package com.riffle.app.launcher

import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.home.AppShortcutItem

/**
 * What a tap on a pinned dock app does, which differs by view mode.
 *
 * Grid mode has nowhere on the launcher for an app's content, so a pinned icon opens the app. Cards
 * mode keeps that content on the launcher, so a pinned icon brings the app's stage forward instead
 * -- but only when the app has a stage. A pinned app with nothing waiting has none to show, so it
 * falls back to opening, and the badge on its icon is exactly the signal for which it will be:
 * badged means there is a stage to bring forward, unbadged means a tap opens the app. Opening stays
 * reachable either way through the icon's long-press menu.
 *
 * A value rather than a callback so the dock can compare it across recompositions: the set of
 * stage-backed apps changes as notifications arrive, and that change is what should redraw the dock,
 * not a fresh lambda identity every frame.
 */
internal sealed interface DockStaticTapBehaviour {
    data object Launch : DockStaticTapBehaviour

    data class SelectStageIfBacked(val stageBackedAppIds: Set<AppStageId>) : DockStaticTapBehaviour

    fun actionFor(shortcut: AppShortcutItem): LauncherShellAction {
        val stageId =
            AppStageId(
                packageName = shortcut.appIdentity.packageName,
                profileId = shortcut.appIdentity.profile.id,
            )
        return when (this) {
            Launch -> shortcut.launchAction()
            is SelectStageIfBacked ->
                if (stageId in stageBackedAppIds) {
                    LauncherShellAction.SelectAppStage(stageId)
                } else {
                    shortcut.launchAction()
                }
        }
    }
}
