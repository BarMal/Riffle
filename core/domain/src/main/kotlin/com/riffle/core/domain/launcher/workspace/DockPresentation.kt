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
 * N6 / design 11.4, the stranding rule, as P5 restates it: the dock is the handle of the Stream and the Library (ADR
 * 0003), so a workspace may hide its dock only while some other way to reach them remains. That way is a home gesture
 * bound to the Stream or Library action, either in the global home gestures or in the workspace's own
 * `gestureBindings`.
 *
 * The TalkBack "Open Stream" and "Open Library" actions and the keyboard shortcuts are always present on the home
 * surface, but they do not count: a touch user must not be stranded. (The workspace menu has no gesture any more; it
 * stays reachable through its accessibility action and Settings until P8 removes it.)
 */
object DockMenuReachability {
    /** The gesture actions that reach what the dock is the handle of: the Stream and the Library. */
    val HANDLE_ACTIONS: Set<LauncherGestureAction> =
        setOf(LauncherGestureAction.OPEN_STREAM, LauncherGestureAction.OPEN_LIBRARY)

    /** The action the Niagara preset binds to its swipe up in place of its hidden dock. */
    val NIAGARA_ACTION: LauncherGestureAction = LauncherGestureAction.OPEN_LIBRARY

    /** What every global home gesture does (defaults included), for the checks below. */
    fun globalActions(settings: HomeGestureSettings): List<LauncherGestureAction> =
        HomeGesture.entries.map { gesture -> settings.actionFor(gesture) }

    /**
     * Whether a gesture opens the Stream or the Library on [workspace]: one of the global home gestures'
     * [globalGestureActions], or one of its own `gestureBindings` (action ids stored by enum name).
     */
    fun hasMenuGesture(
        workspace: Workspace,
        globalGestureActions: Collection<LauncherGestureAction>,
    ): Boolean =
        globalGestureActions.any { it in HANDLE_ACTIONS } ||
            workspace.gestureBindings.values.any { LauncherGestureAction.fromStoredName(it) in HANDLE_ACTIONS }

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
