package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.MAX_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import com.riffle.core.domain.launcher.settings.LauncherGestureAction

/**
 * A workspace's override of how the layout's shared dock is drawn while it is the shown workspace (Q17, design 11.2).
 * Override-only: pins, capacity, appearance and the panel always come from the layout's dock and are never per
 * workspace. Every field left at its default follows the shared dock, so `DockPresentation()` means "follow".
 */
data class DockPresentation(
    /** True draws no dock on this workspace (valid only while the menu stays reachable, [DockMenuReachability]). */
    val hidden: Boolean = false,
    /** Null follows the shared dock's home edge. */
    val edge: DockPosition? = null,
    /** Null follows the shared dock's icon size; otherwise within MIN..MAX_DOCK_ICON_SIZE_DP. */
    val iconSizeDp: Int? = null,
) {
    init {
        require(iconSizeDp == null || iconSizeDp in MIN_DOCK_ICON_SIZE_DP..MAX_DOCK_ICON_SIZE_DP) {
            "Dock icon size override must be within $MIN_DOCK_ICON_SIZE_DP..$MAX_DOCK_ICON_SIZE_DP dp."
        }
    }

    /** Whether this overrides nothing (stored as no override). */
    val followsShared: Boolean get() = !hidden && edge == null && iconSizeDp == null

    companion object {
        /** [iconSizeDp] clamped into the valid range, for decoding and UI input; null stays null. */
        fun clampedIconSize(iconSizeDp: Int?): Int? = iconSizeDp?.coerceIn(MIN_DOCK_ICON_SIZE_DP, MAX_DOCK_ICON_SIZE_DP)
    }
}

/**
 * N6 / design 11.4, the stranding rule: today the dock is the handle of the workspace menu (and of the app list), so a
 * workspace may hide its dock only while some other way to reach the menu remains. That way is a home gesture bound to
 * the workspace-menu action, either in the global home gestures or in the workspace's own `gestureBindings`.
 *
 * The TalkBack "Workspace menu" action and the keyboard shortcut are always present on the home surface, but they do
 * not count: a touch user must not be stranded.
 */
object DockMenuReachability {
    /** The gesture action that opens the workspace menu (11.4). */
    val WORKSPACE_MENU_ACTION: LauncherGestureAction = LauncherGestureAction.OPEN_WORKSPACE_MENU

    /** What every global home gesture does (defaults included), for the checks below. */
    fun globalActions(settings: HomeGestureSettings): List<LauncherGestureAction> =
        HomeGesture.entries.map { gesture -> settings.actionFor(gesture) }

    /**
     * Whether a gesture opens the workspace menu on [workspace]: one of the global home gestures'
     * [globalGestureActions], or one of its own `gestureBindings` (action ids stored by enum name).
     */
    fun hasMenuGesture(
        workspace: Workspace,
        globalGestureActions: Collection<LauncherGestureAction>,
    ): Boolean =
        WORKSPACE_MENU_ACTION in globalGestureActions ||
            workspace.gestureBindings.values.any { LauncherGestureAction.fromStoredName(it) == WORKSPACE_MENU_ACTION }

    /** Whether [workspace] may hide its dock (it may always show it). */
    fun canHideDock(
        workspace: Workspace,
        globalGestureActions: Collection<LauncherGestureAction>,
    ): Boolean = hasMenuGesture(workspace, globalGestureActions)

    /** The stranding issue for [workspace], or null when its dock is shown or the menu stays reachable. */
    fun issue(
        workspace: Workspace,
        globalGestureActions: Collection<LauncherGestureAction>,
    ): WorkspaceIssue? =
        WorkspaceIssue.DockHiddenWithoutMenuEntry.takeIf {
            workspace.dock.presentation?.hidden == true && !canHideDock(workspace, globalGestureActions)
        }
}
