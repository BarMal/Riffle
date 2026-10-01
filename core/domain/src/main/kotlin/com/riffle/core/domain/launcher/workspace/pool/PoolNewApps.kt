package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory

/** The outcome of [PoolNewApps.placeNewApp]. */
data class NewAppEdit(
    val edit: PoolEdit,
    /** Workspaces that now show the app (all through one shared [PoolApp]). */
    val placedIn: List<WorkspaceId>,
    /** Opted-in workspaces that were full, had no page, or already showed the app. */
    val skipped: List<WorkspaceId>,
)

object PoolNewApps {
    /**
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
