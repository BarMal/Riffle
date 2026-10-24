package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.NewAppDefaults
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory

/** The outcome of [PoolNewApps.placeNewApp]. */
data class NewAppEdit(
    val edit: PoolEdit,
    /** Workspaces that now show the app (all through one shared [PoolApp]). */
    val placedIn: List<WorkspaceId>,
    /** Opted-in workspaces that were full, had no page, or already showed the app. */
    val skipped: List<WorkspaceId>,
    /** Workspaces that borrowed an arrangement and got their own (fork on first edit) to take the icon. */
    val forked: List<WorkspaceId> = emptyList(),
    /** Workspaces that got a new page for the icon because every page was full and `autoPages` is on. */
    val pagesAdded: List<WorkspaceId> = emptyList(),
)

/** Why an app became visible to the launcher. Only [INSTALLED] is a new app (N9). */
enum class AppArrival {
    /** A package was installed (not replaced) for a profile that was already available. */
    INSTALLED,

    /** A work or private profile was unpaused or unlocked: its apps reappear, nothing is new. */
    PROFILE_AVAILABLE,

    /** An existing package was updated or replaced. */
    UPDATED,

    /** Apps came back with a restore. */
    RESTORED,
}

object PoolNewApps {
    /**
     * N9/N12, the entry point the install event calls: when [arrival] is [AppArrival.INSTALLED], creates ONE [PoolApp]
     * and references it from every workspace of [layout] whose new-app setting ([NewAppDefaults.resolve]: its own,
     * else its preset's) is `HOME_AND_FINDER`, in the first free cell of its first page with room. When every page is
     * full and the workspace's `autoPages` is on, a page is added (the last page's grid) and the icon goes there;
     * otherwise that workspace is skipped. Any other [arrival] (a work profile unpausing, an update, a restore)
     * changes nothing.
     *
     * A workspace without an arrangement of its own shows the first of [candidatesOf] that has pages (pass
     * `PoolHomeView.candidates(...)`); it gets its own arrangement first ([PoolArrangements.fork], an edit forks),
     * unless the arrangement it shows already has the app. With the default [candidatesOf] such a workspace is
     * skipped.
     *
     * Idempotent: a workspace that already shows the app (same identity, not a shortcut) is skipped, so a repeated
     * event places nothing twice. When nothing is placed the pool is unchanged. Workspaces of other layouts are never
     * touched; arrangements of workspaces not in [layout] are ignored.
     */
    fun placeNewApp(
        pool: PlacedItemPool,
        identity: AppIdentity,
        label: String,
        ids: WorkspaceIdFactory,
        layout: LayoutWorkspaces,
        arrival: AppArrival,
        candidatesOf: (WorkspaceId) -> List<WorkspaceId> = { listOf(it) },
    ): NewAppEdit {
        if (arrival != AppArrival.INSTALLED) return NewAppEdit(PoolEdit(pool), emptyList(), emptyList())
        val app = PoolApp(PoolIds.fresh(pool.items.keys, ids), identity, label)
        val run = Run(pool.copy(items = pool.items + (app.id to app)))
        layout.workspaces
            .filter { NewAppDefaults.resolve(it).placement == NewAppPlacement.HOME_AND_FINDER }
            .forEach { workspace -> run.placeInto(workspace, app, ids, candidatesOf) }
        val result = if (run.placed.isEmpty()) pool else run.pool
        return NewAppEdit(PoolGc.finish(pool, result), run.placed, run.skipped, run.forked, run.pagesAdded)
    }

    private class Run(var pool: PlacedItemPool) {
        val placed = mutableListOf<WorkspaceId>()
        val skipped = mutableListOf<WorkspaceId>()
        val forked = mutableListOf<WorkspaceId>()
        val pagesAdded = mutableListOf<WorkspaceId>()

        fun placeInto(
            workspace: Workspace,
            app: PoolApp,
            ids: WorkspaceIdFactory,
            candidatesOf: (WorkspaceId) -> List<WorkspaceId>,
        ) {
            val id = workspace.id
            val before = pool
            val owned = ownArrangement(id, app, ids, candidatesOf)
            val fit =
                owned?.takeUnless { alreadyShows(pool, it, app.appIdentity) }
                    ?.let { firstFit(pool, id, it, app.id) ?: grow(id, app, ids, workspace) }
            if (fit == null) {
                // Nothing placed here: a fork made only for this icon is dropped again.
                pool = before
                forked -= id
                skipped += id
            } else {
                pool = fit
                placed += id
            }
        }

        /** [id]'s own arrangement, forking the one it shows when it has none; null when it should be skipped. */
        private fun ownArrangement(
            id: WorkspaceId,
            app: PoolApp,
            ids: WorkspaceIdFactory,
            candidatesOf: (WorkspaceId) -> List<WorkspaceId>,
        ): Arrangement? =
            pool.arrangements[id] ?: run {
                val candidates = candidatesOf(id)
                val shown =
                    PoolHomeView.arrangementOf(pool, candidates.filter { it != id })?.let(pool.arrangements::get)
                val fork =
                    shown?.takeUnless { alreadyShows(pool, it, app.appIdentity) }
                        ?.let { PoolArrangements.fork(pool, id, candidates, ids) as? PoolResult.Done }
                fork?.let {
                    // The fork collects unreferenced items, which includes the new app until it is placed: keep it.
                    val forkedPool = it.edit.pool
                    pool = forkedPool.copy(items = forkedPool.items + (app.id to app))
                    forked += id
                    pool.arrangements[id]
                }
            }

        private fun grow(
            id: WorkspaceId,
            app: PoolApp,
            ids: WorkspaceIdFactory,
            workspace: Workspace,
        ): PlacedItemPool? {
            val grown =
                if (NewAppDefaults.resolve(
                        workspace,
                    ).autoPages
                ) {
                    PoolAutoPages.appendPage(pool, id, ids)
                } else {
                    null
                }
            val done =
                grown?.let { (next, pageId) -> PoolPages.place(next, id, pageId, app.id, null, GridSpan()) }
                    as? PoolResult.Done
            if (done != null) pagesAdded += id
            return done?.edit?.pool
        }
    }

    /**
     * The earlier form, kept for its callers: opts in by the arrangement's own stored [Arrangement.newAppPlacement]
     * (superseded by the workspace's setting, see the overload above) and never adds pages or forks.
     *
     * N9/N12: creates ONE [PoolApp] and references it from the first free cell of the first page with room in every
     * workspace whose [NewAppPlacement] is `HOME_AND_FINDER`. Idempotent per package-added event: a workspace that
     * already places this app (same identity, not a shortcut) is skipped. When nothing is placed the pool is unchanged.
     * Creating pages when "pages appear as you fill them" is arrangement-only and not done here.
     */
    fun placeNewApp(
        pool: PlacedItemPool,
        identity: AppIdentity,
        label: String,
        ids: WorkspaceIdFactory,
    ): NewAppEdit {
        val app = PoolApp(PoolIds.fresh(pool.items.keys, ids), identity, label)
        var working = pool.copy(items = pool.items + (app.id to app))
        val placed = mutableListOf<WorkspaceId>()
        val skipped = mutableListOf<WorkspaceId>()
        pool.arrangements.filterValues { it.newAppPlacement == NewAppPlacement.HOME_AND_FINDER }.forEach {
                (workspaceId, arrangement) ->
            val next =
                if (alreadyShows(
                        pool,
                        arrangement,
                        identity,
                    )
                ) {
                    null
                } else {
                    firstFit(working, workspaceId, arrangement, app.id)
                }
            if (next == null) {
                skipped += workspaceId
            } else {
                working = next
                placed += workspaceId
            }
        }
        val result = if (placed.isEmpty()) pool else working
        return NewAppEdit(PoolGc.finish(pool, result), placed, skipped)
    }

    private fun alreadyShows(
        pool: PlacedItemPool,
        arrangement: Arrangement,
        identity: AppIdentity,
    ): Boolean =
        PoolReferences.referencedIn(arrangement).any { id ->
            (pool.items[id] as? PoolApp)?.let { it.appIdentity == identity && it.appShortcutId == null } == true
        }

    private fun firstFit(
        pool: PlacedItemPool,
        workspaceId: WorkspaceId,
        arrangement: Arrangement,
        appId: PoolItemId,
    ): PlacedItemPool? =
        arrangement.pages.firstNotNullOfOrNull { page ->
            (PoolPages.place(pool, workspaceId, page.id, appId, null, GridSpan()) as? PoolResult.Done)?.edit?.pool
        }
}
