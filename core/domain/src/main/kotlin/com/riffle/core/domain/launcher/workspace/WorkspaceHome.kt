package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool
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
 * One arrangement per posture (ADR 0003, decision 3): the launcher home of a layout (device class) is the pool
 * arrangement of that layout's **active** workspace as stored, drawn by the standard home surface over the pool's
 * projection of it. There is no switching: nothing in the app changes which workspace is active, so the stored choice
 * of the build before the cut-over stands. The other workspaces and their arrangements stay in storage, decodable and
 * untouched (the owner may want switching back later), and nothing shows or edits them. Everything here is pure and
 * never throws.
 *
 * Rules:
 * - A layout with nothing stored shows its **migrated** workspace (`ws:<layout class>:<mode>`): the home the user had
 *   in that mode, exactly as before workspaces existed.
 * - A **migrated** active workspace shows that mode ([homeModeFor]) and that arrangement.
 * - Any other active workspace (the seeded default, a copy, a preset) keeps the mode the layout set already shows (the
 *   stored `LauncherViewMode`, now only migration input), shows its own arrangement, or until its first edit the
 *   arrangement it borrows ([PoolHomeView.candidates]), so the default renders the migrated home identically. The
 *   first edit forks the borrowed arrangement into the active workspace; the borrowed one is left as it was.
 * - Only the active key's mode follows the workspace; the other stored modes keep their migrated arrangements.
 * - Pages of the active workspace that do not read placed items (lens pages, page-sets, the Finder) are stored and
 *   never drawn: the Library and the Stream replaced them.
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

    /**
     * The workspace whose arrangement [layoutClass] shows: the stored active one (never replaced by a fallback: what
     * can be drawn is decided by its placed pages alone), or null while nothing is stored for the layout.
     */
    fun activeWorkspace(
        set: WorkspaceSet?,
        layoutClass: HomeLayoutDeviceClass,
    ): Workspace? {
        val workspaceClass = layoutClass.workspaceLayoutClass()
        return set?.takeIf { it.hasLayout(workspaceClass) }?.workspacesFor(workspaceClass)?.active
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
            else -> HomeShownWorkspace(workspace.id, borrowCandidates(workspace.id, layoutClass))
        }
    }

    /**
     * What a non-migrated workspace borrows until its first edit, best first: itself, then the migrated arrangements
     * in a fixed order (Library, Standard, Cards). Fixed, never the currently shown mode, so a detour through a
     * migrated workspace of another mode cannot change what a preset borrows (or later forks). Library first because
     * the alpha showed every layout as Library.
     */
    fun borrowCandidates(
        id: WorkspaceId,
        layoutClass: HomeLayoutDeviceClass,
    ): List<WorkspaceId> {
        val migrated =
            BORROW_ORDER.map {
                HomeLayoutWorkspaceMapper.workspaceId(
                    layoutClass.workspaceLayoutClass(),
                    it,
                )
            }
        return (listOf(id) + migrated).distinct()
    }

    /**
     * The mode the standard home shows while [workspace] is active on [layoutClass], derived from [pool] (the
     * layout's placed items) so it never depends on what a migrated workspace selected before:
     * - a migrated workspace: its own mode;
     * - one that forked: the mode of the migrated arrangement it was forked from ([Arrangement.forkedFrom]);
     * - one that borrows: the mode of the arrangement it borrows ([borrowCandidates]);
     * - one with an arrangement of no known origin (an older fork, an adopted page): the first migrated arrangement
     *   the pool holds, in [borrowCandidates] order.
     * Otherwise (no pool, or no migrated arrangement at all) [current].
     */
    fun homeModeFor(
        workspace: Workspace?,
        layoutClass: HomeLayoutDeviceClass,
        pool: PlacedItemPool?,
        current: LauncherViewMode,
    ): LauncherViewMode {
        val id = workspace?.id ?: return current
        val migrated = migratedMode(id, layoutClass)
        val others = borrowCandidates(id, layoutClass).drop(1)
        val origin =
            when {
                migrated != null || pool == null -> null
                id in pool.arrangements ->
                    pool.arrangements.getValue(id).forkedFrom?.takeIf { migratedMode(it, layoutClass) != null }
                        ?: others.firstOrNull { it in pool.arrangements }
                else -> PoolHomeView.arrangementOf(pool, others) ?: others.firstOrNull { it in pool.arrangements }
            }
        return migrated ?: origin?.let { migratedMode(it, layoutClass) } ?: current
    }

    /** Whether [workspace] has a pager page that draws placed items. */
    fun hasHomeGrid(workspace: Workspace): Boolean = workspace.pagerPages.any(PoolHomeView::isPlacedHomePage)

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

    private fun nthHomePage(
        workspace: Workspace,
        page: PageHost,
        layout: HomeLayout,
    ): LauncherPageId? {
        val index = workspace.pagerPages.filter(PoolHomeView::isPlacedHomePage).indexOf(page)
        return layout.pages.filter { it.type == LauncherPageType.Home }.getOrNull(index)?.id
    }

    private val BORROW_ORDER =
        listOf(
            LauncherViewMode.HOME_SCREEN_LIBRARY,
            LauncherViewMode.STANDARD_APP_DRAWER,
            LauncherViewMode.CARD_INTERFACE,
        )
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
