package com.riffle.core.domain.launcher.workspace.dock

import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.workspace.DockMenuReachability
import com.riffle.core.domain.launcher.workspace.DockPresentation
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.pool.PoolDock
import com.riffle.core.domain.launcher.workspace.pool.PoolDockSettings

/**
 * The override a workspace's dock actually applies, after the stranding rule: [presentation] is null when it follows
 * the shared dock. [strandedFallback] is true when the stored override hid the dock but no gesture reaches the
 * workspace menu, so the dock is drawn anyway (the UI may say why); every other part of the override still applies.
 */
data class DockPresentationResolution(
    val presentation: DockPresentation?,
    val strandedFallback: Boolean,
)

/**
 * Design 11.4, resolution order: the layout's shared dock (`PoolDock.settings`, or a `DockModel` while the dock is
 * still read from the home layout), then the shown workspace's `WorkspaceDock.presentation`. Pure. Pins, capacity,
 * appearance and the panel are never overridden; only "is it drawn", its home edge and its icon size are.
 *
 * Library's edge (`PoolDockSettings.libraryPosition`) is not overridden: workspaces replace the Home/Library pair.
 */
object EffectiveDock {
    /** [workspace]'s override after the stranding rule ([DockMenuReachability]). */
    fun presentationFor(
        workspace: Workspace?,
        globalGestureActions: Collection<LauncherGestureAction>,
    ): DockPresentationResolution {
        val stored = workspace?.dock?.presentation
        val stranded = workspace != null && DockMenuReachability.issue(workspace, globalGestureActions) != null
        val applied = if (stranded) stored?.copy(hidden = false) else stored
        return DockPresentationResolution(applied?.takeUnless { it.followsShared }, stranded)
    }

    /** The shared settings with [presentation] layered on top. */
    fun apply(
        settings: PoolDockSettings,
        presentation: DockPresentation?,
    ): PoolDockSettings =
        if (presentation == null) {
            settings
        } else {
            settings.copy(
                isEnabled = settings.isEnabled && !presentation.hidden,
                position = presentation.edge ?: settings.position,
                iconSizeDp = presentation.iconSizeDp ?: settings.iconSizeDp,
            )
        }

    /** The same layering over a `DockModel` (its items are untouched). */
    fun apply(
        dock: DockModel,
        presentation: DockPresentation?,
    ): DockModel =
        if (presentation == null) {
            dock
        } else {
            dock.copy(
                isEnabled = dock.isEnabled && !presentation.hidden,
                position = presentation.edge ?: dock.position,
                iconSizeDp = presentation.iconSizeDp ?: dock.iconSizeDp,
            )
        }
}

/**
 * The dock drawn while [workspace] is shown: [poolDock] with the workspace's override (after the stranding rule)
 * applied to its settings. Pins and panel are the shared ones. Null while the layout has no dock in its pool.
 */
fun resolveDock(
    poolDock: PoolDock?,
    workspace: Workspace?,
    globalGestureActions: Collection<LauncherGestureAction> = emptyList(),
): PoolDock? =
    poolDock?.let { dock ->
        val presentation = EffectiveDock.presentationFor(workspace, globalGestureActions).presentation
        dock.copy(settings = EffectiveDock.apply(dock.settings, presentation))
    }
