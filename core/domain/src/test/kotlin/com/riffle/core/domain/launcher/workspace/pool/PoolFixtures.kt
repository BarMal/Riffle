package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

internal val W1 = WorkspaceId("w1")
internal val W2 = WorkspaceId("w2")
internal val W3 = WorkspaceId("w3")
internal val P1 = LauncherPageId("p1")
internal val P2 = LauncherPageId("p2")

internal fun appIdentity(
    pkg: String,
    profile: AppProfile = AppProfile.personal(),
) = AppIdentity(AppPackageName(pkg), AppActivityName("$pkg.Main"), profile)

internal fun poolApp(
    id: String,
    pkg: String = id,
) = PoolApp(PoolItemId(id), appIdentity(pkg), pkg)

internal fun poolFolder(
    id: String,
    vararg pkgs: String,
) = PoolFolder(PoolItemId(id), id, pkgs.map { FolderEntry("$id-$it", appIdentity(it), it) })

internal val provider = WidgetProviderIdentity(AppPackageName("w.pkg"), WidgetProviderClassName("w.Provider"))

internal fun poolWidget(
    id: String,
    host: Int? = 100,
    withProvider: Boolean = true,
) = PoolWidget(PoolItemId(id), id, provider = provider.takeIf { withProvider }, hostedId = host?.let(::HostedWidgetId))

internal fun at(
    column: Int,
    row: Int,
    columns: Int = 1,
    rows: Int = 1,
) = GridPlacement(GridCell(column, row), GridSpan(columns, rows))

internal fun counterIds(prefix: String = "n"): WorkspaceIdFactory {
    var n = 0
    return WorkspaceIdFactory { "$prefix${++n}" }
}

/** Workspaces [W1] and [W2] with two empty 4x4 pages each. */
internal fun emptyPool(
    workspaces: List<WorkspaceId> = listOf(W1, W2),
    newApps: NewAppPlacement = NewAppPlacement.FINDER_ONLY,
): PlacedItemPool =
    PlacedItemPool(
        arrangements =
            workspaces.associateWith {
                Arrangement(
                    pages = listOf(P1, P2).map { ArrangementPage(it, GridDimensions(4, 4)) },
                    newAppPlacement = newApps,
                )
            },
    )

internal fun PoolResult.done(): PoolEdit = (this as? PoolResult.Done)?.edit ?: fail("expected Done but was $this")

internal fun PoolResult.rejection(): PoolRejection =
    (this as? PoolResult.Rejected)?.reason ?: fail("expected Rejected but was $this")

internal fun PlacedItemPool.refs(id: String): Int = PoolReferences.count(this, PoolItemId(id))

internal fun PlacedItemPool.add(
    item: PoolItem,
    ws: WorkspaceId = W1,
    page: LauncherPageId = P1,
    cell: GridPlacement? = null,
): PlacedItemPool = PoolPlacement.addNew(this, ws, page, item, cell).done().pool

/** Independent re-statement of the pool invariants, so a bug in repair cannot hide a bug in an operation. */
internal fun assertInvariants(pool: PlacedItemPool) {
    val refs = HashMap<PoolItemId, Int>()
    pool.arrangements.forEach { (ws, arrangement) ->
        assertEquals(arrangement.pages.size, arrangement.pages.map { it.id }.toSet().size, "page ids unique in $ws")
        val inArrangement = HashSet<PoolItemId>()
        arrangement.pages.forEach { page ->
            assertGeometry(page, "$ws")
            page.placements.forEach { p ->
                assertTrue(p.item in pool.items, "dangling ${p.item} in $ws")
                assertTrue(inArrangement.add(p.item), "${p.item} twice in $ws")
                refs.merge(p.item, 1, Int::plus)
            }
        }
    }
    pool.items.values.forEach { item ->
        assertTrue((refs[item.id] ?: 0) >= 1, "orphan ${item.id}")
        if (item is PoolWidget) assertEquals(1, refs[item.id], "widget ${item.id} placed once")
    }
    val hosts = pool.items.values.mapNotNull { (it as? PoolWidget)?.hostedId }
    assertEquals(hosts.size, hosts.toSet().size, "host ids unique")
}

private fun assertGeometry(
    page: ArrangementPage,
    where: String,
) {
    val cells = HashSet<GridCell>()
    page.placements.forEach { p ->
        assertTrue(holds(page.grid, p.at), "${p.item} outside grid in $where")
        val covered =
            (p.at.cell.column until p.at.cell.column + p.at.span.columns).flatMap { c ->
                (p.at.cell.row until p.at.cell.row + p.at.span.rows).map { r -> GridCell(c, r) }
            }
        covered.forEach { assertTrue(cells.add(it), "collision at $it in $where") }
    }
}

private fun holds(
    grid: GridDimensions,
    p: GridPlacement,
) = p.cell.column >= 0 && p.cell.row >= 0 &&
    p.cell.column + p.span.columns <= grid.columns && p.cell.row + p.span.rows <= grid.rows
