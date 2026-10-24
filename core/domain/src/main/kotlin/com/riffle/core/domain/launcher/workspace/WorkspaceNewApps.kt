package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.workspace.pool.NewAppPlacement

/**
 * What a workspace does with a newly installed app (N9, design 9.8): [placement] says whether it also gets a home
 * icon, [autoPages] whether a page is added when every placed page is full ("pages appear as you fill them").
 */
data class WorkspaceNewApps(
    val placement: NewAppPlacement = NewAppPlacement.FINDER_ONLY,
    val autoPages: Boolean = false,
)

/** The new-app setting a workspace actually uses: its own choice, else its preset's default, else today's behaviour. */
object NewAppDefaults {
    /** Today's behaviour, and what a migrated workspace (no preset recorded) uses: the Finder only, no new pages. */
    val MIGRATED = WorkspaceNewApps()

    /** iOS-style: every new app gets a home icon, and pages appear as they fill. */
    val HOME_AND_PAGES = WorkspaceNewApps(NewAppPlacement.HOME_AND_FINDER, autoPages = true)

    private const val IOS_PRESET_ID = "ios"

    /**
     * The default for the preset [presetId] names. Only iOS-style opts in: Nova-style keeps the drawer-first Android
     * habit, Niagara's pinned list is hand-picked, and TimeScape and Kvaesitso-style have no icon grid to place into.
     */
    fun forPreset(presetId: String?): WorkspaceNewApps = if (presetId == IOS_PRESET_ID) HOME_AND_PAGES else MIGRATED

    /** [workspace]'s own setting when it has one, otherwise its recorded preset's default. */
    fun resolve(workspace: Workspace): WorkspaceNewApps = workspace.newApps ?: forPreset(workspace.presetId)
}
