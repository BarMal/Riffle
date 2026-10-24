package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.workspace.dock.EffectiveDock
import com.riffle.core.domain.launcher.workspace.pool.PoolHomeView

/**
 * Which workspace's arrangement one `HomeLayoutSet` key shows and edits on the standard home (S9): [workspaceId] is the
 * workspace an edit lands in; while it has no arrangement of its own it shows the first of [candidates] that has pages
 * (it borrows), and the first edit forks that one ([com.riffle.core.domain.launcher.workspace.pool.PoolArrangements]).
 * Empty [candidates] means it borrows nothing.
 */
data class HomeShownWorkspace(
    val workspaceId: WorkspaceId,
    val candidates: List<WorkspaceId> = emptyList(),
)

/** Resolves [HomeShownWorkspace] per key; [activeMode] is the mode of the set's active key. Pure. */
fun interface HomeShownWorkspaces {
    fun shownFor(
        key: HomeLayoutKey,
        activeMode: LauncherViewMode,
    ): HomeShownWorkspace

    companion object {
        /** The S4 rule: every key shows its own migrated workspace `ws:<layout class>:<mode>`. */
        val Migrated: HomeShownWorkspaces =
            HomeShownWorkspaces {
                    key,
                    _,
                ->
                HomeShownWorkspace(WorkspaceHome.migratedId(key))
            }
    }
}

/**
 * S9, workspaces are the default: the launcher home is the active workspace of the current layout, drawn by the
 * standard home surface (full editing parity) over the pool's projection of that workspace's arrangement. Everything
 * here is pure and never throws.
 *
 * Rules (`docs/product/workspaces-default.md`):
 * - A **migrated** workspace (`ws:<layout class>:<mode>`) is the home the user had in that mode: the standard home
 *   shows that mode ([homeModeFor]) and that arrangement, exactly as before the flip.
 * - Any other workspace (a preset such as the Nova default, a copy) keeps the mode the layout set already shows (the
 *   stored `LauncherViewMode`, now only migration input), shows its own arrangement, or until its first edit the
 *   arrangement it borrows ([PoolHomeView.candidates]), so the default workspace renders the migrated home
 *   identically. A workspace without any home-grid page borrows nothing.
 * - Only the active key's mode follows the workspace; the other stored modes keep their migrated arrangements.
 * - Pages of a non-migrated workspace that do not read placed items (lens pages, page-sets) are drawn by the
 *   container hosts after the placed pages ([extraPages]). A migrated workspace's lens pages are the layout's own
 *   generated pages.
 */
@Suppress("TooManyFunctions") // Small pure rules the shell reads one by one.
object WorkspaceHome {
    /** The migrated workspace of [key] (`ws:<layout class>:<mode>`, N10 fold included). */
    fun migratedId(key: HomeLayoutKey): WorkspaceId =
        HomeLayoutWorkspaceMapper.workspaceId(key.deviceClass.workspaceLayoutClass(), key.viewMode)

    /** The mode [id] was migrated from on [layoutClass], or null when it is not a migrated workspace there. */
    fun migratedMode(
        id: WorkspaceId,
        layoutClass: HomeLayoutDeviceClass,
    ): LauncherViewMode? =
        LauncherViewMode.entries.firstOrNull { mode ->
            HomeLayoutWorkspaceMapper.workspaceId(layoutClass.workspaceLayoutClass(), mode) == id
        }

    /** The workspace [layoutClass] draws (the active one, or its fallback), or null while nothing is stored for it. */
    fun activeWorkspace(
        set: WorkspaceSet?,
        layoutClass: HomeLayoutDeviceClass,
    ): Workspace? {
        val workspaceClass = layoutClass.workspaceLayoutClass()
        return set?.takeIf { it.hasLayout(workspaceClass) }?.resolveActive(workspaceClass)?.workspace
    }

    /** The [HomeShownWorkspaces] for [set]; [HomeShownWorkspaces.Migrated] for every layout [set] does not hold. */
    fun shownWorkspaces(set: WorkspaceSet?): HomeShownWorkspaces =
        if (set == null) HomeShownWorkspaces.Migrated else HomeShownWorkspaces { key, mode -> shown(set, key, mode) }

    /** See [HomeShownWorkspace] and the rules above. */
    fun shown(
        set: WorkspaceSet?,
        key: HomeLayoutKey,
        activeMode: LauncherViewMode,
    ): HomeShownWorkspace {
        val migrated = HomeShownWorkspace(migratedId(key))
        val layoutClass = key.deviceClass.workspaceLayoutClass()
        val workspace = activeWorkspace(set, layoutClass)
        return when {
            key.viewMode != activeMode || workspace == null -> migrated
            // A migrated workspace of another mode shows once the shell switched to its mode ([homeModeFor]).
            migratedMode(workspace.id, layoutClass) != null -> migrated
            !hasHomeGrid(workspace) -> HomeShownWorkspace(workspace.id)
            else -> HomeShownWorkspace(workspace.id, PoolHomeView.candidates(workspace.id, layoutClass, key.viewMode))
        }
    }

    /**
     * The mode the standard home shows while [workspace] is active on [layoutClass]: a migrated workspace's own mode,
     * otherwise [current] (the stored mode stays as it is, so nothing re-lays out the borrowed home).
     */
    fun homeModeFor(
        workspace: Workspace?,
        layoutClass: HomeLayoutDeviceClass,
        current: LauncherViewMode,
    ): LauncherViewMode = workspace?.let { migratedMode(it.id, layoutClass) } ?: current

    /** Whether [workspace] has a pager page that draws placed items. */
    fun hasHomeGrid(workspace: Workspace): Boolean = workspace.pagerPages.any(PoolHomeView::isPlacedHomePage)

    /**
     * The pages of [workspace] the standard home draws with the container hosts, after its placed pages, in workspace
     * order: every pager page that reads no placed items. None for a migrated workspace (its lens pages are the
     * layout's generated pages, already drawn) and none while nothing is active.
     */
    fun extraPages(
        workspace: Workspace?,
        layoutClass: HomeLayoutDeviceClass,
    ): List<PageHost> =
        if (workspace == null || migratedMode(workspace.id, layoutClass) != null) {
            emptyList()
        } else {
            workspace.pagerPages.filterNot { it.readsPlacedItems() }
        }

    /**
     * The standard home page [containerId] of [workspace] is drawn as in [layout], or null when it is not a placed
     * page there (a lens page, the Finder, an unknown id). A migrated page (`page:<id>`) names its layout page; a
     * home-grid page names its arrangement page through its lens, else it is the k-th placed page of the workspace
     * drawn as the k-th `Home` page.
     */
    fun layoutPageOf(
        workspace: Workspace,
        containerId: ContainerId,
        layout: HomeLayout,
    ): LauncherPageId? {
        val page = workspace.pages.firstOrNull { it.id == containerId } ?: return null
        val ids = layout.pages.map { it.id }
        val migratedPage = containerId.value.removePrefix(MIGRATED_PAGE_PREFIX).trimEnd('#')
        return when {
            containerId.value.startsWith(MIGRATED_PAGE_PREFIX) && LauncherPageId(migratedPage) in ids ->
                LauncherPageId(migratedPage)
            !PoolHomeView.isPlacedHomePage(page) -> null
            else -> {
                val named = PoolHomeView.pageKey(page)?.let(::LauncherPageId)?.takeIf { it in ids }
                named ?: nthHomePage(workspace, page, layout)
            }
        }
    }

    /** The layout page of the workspace's start page ([Workspace.effectiveStartPageId]); null when it is not placed. */
    fun startPageOf(
        workspace: Workspace?,
        layout: HomeLayout,
    ): LauncherPageId? = workspace?.effectiveStartPageId?.let { id -> layoutPageOf(workspace, id, layout) }

    /**
     * [global] with [workspace]'s own gesture bindings on top (gesture name -> action name, both stored by enum name).
     * Unknown names are ignored, never an error; actions that need a launch target (an app or a shortcut) are ignored
     * because a binding stores none. Null or no bindings return [global] itself.
     */
    fun gestures(
        global: HomeGestureSettings,
        workspace: Workspace?,
    ): HomeGestureSettings =
        workspace?.gestureBindings.orEmpty().entries.fold(global) { settings, (gestureName, actionName) ->
            val gesture = HomeGesture.entries.firstOrNull { it.name == gestureName }
            val action = LauncherGestureAction.fromStoredName(actionName)?.takeUnless { it in TARGETED_ACTIONS }
            if (gesture == null || action == null) settings else settings.withAction(gesture, action)
        }

    private fun nthHomePage(
        workspace: Workspace,
        page: PageHost,
        layout: HomeLayout,
    ): LauncherPageId? {
        val index = workspace.pagerPages.filter(PoolHomeView::isPlacedHomePage).indexOf(page)
        return layout.pages.filter { it.type == LauncherPageType.Home }.getOrNull(index)?.id
    }

    private val TARGETED_ACTIONS = setOf(LauncherGestureAction.LAUNCH_APP, LauncherGestureAction.LAUNCH_APP_SHORTCUT)
    private const val MIGRATED_PAGE_PREFIX = "page:"
}

/** Whether this page reads placed items (`home.grid`) anywhere: a page, a page-set or a widget grid made of them. */
fun PageHost.readsPlacedItems(): Boolean =
    when (this) {
        is PageSetContainer -> WorkspaceSourceIds.HOME_GRID in binding.lens.sources
        is PageContainer ->
            when (val body = content) {
                is PageContent.Bound -> WorkspaceSourceIds.HOME_GRID in body.binding.lens.sources
                is PageContent.WidgetGrid ->
                    body.placements.any { WorkspaceSourceIds.HOME_GRID in it.widget.binding.lens.sources }
            }
    }

/**
 * The shell state the home surface draws while [workspace] is active (S9, render only, never stored): the global home
 * gestures with the workspace's own bindings on top ([WorkspaceHome.gestures]) and the shared dock with the
 * workspace's override after the stranding rule ([EffectiveDock.presentationFor], design 11.4). Settings, the drawer
 * and the stored state keep the global values. Null (nothing loaded) and a workspace that overrides nothing return
 * this state itself.
 */
fun LauncherShellState.withWorkspaceHome(workspace: Workspace?): LauncherShellState {
    if (workspace == null) return this
    val global = launcherSettings.gestures.homeGestures
    val gestures = WorkspaceHome.gestures(global, workspace)
    val presentation = EffectiveDock.presentationFor(workspace, DockMenuReachability.globalActions(global)).presentation
    val dock = EffectiveDock.apply(homeLayout.dock, presentation)
    return if (gestures === global && dock == homeLayout.dock) {
        this
    } else {
        copy(
            homeLayout = homeLayout.copy(dock = dock),
            launcherSettings =
                launcherSettings.copy(gestures = launcherSettings.gestures.copy(homeGestures = gestures)),
        )
    }
}
