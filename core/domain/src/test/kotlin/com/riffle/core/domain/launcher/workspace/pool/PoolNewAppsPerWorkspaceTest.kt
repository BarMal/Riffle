package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.NewAppDefaults
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceNewApps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PoolNewAppsPerWorkspaceTest {
    private val homeOnly = WorkspaceNewApps(NewAppPlacement.HOME_AND_FINDER, autoPages = false)

    private fun ws(
        id: WorkspaceId,
        presetId: String? = null,
        newApps: WorkspaceNewApps? = null,
    ) = Workspace(id, id.value, presetId = presetId, newApps = newApps)

    private fun layout(vararg workspaces: Workspace) =
        LayoutWorkspaces(workspaces.toList(), workspaces.first().id, workspaces.first().id)

    private fun place(
        pool: PlacedItemPool,
        layout: LayoutWorkspaces,
        pkg: String = "new.app",
        arrival: AppArrival = AppArrival.INSTALLED,
        candidatesOf: (WorkspaceId) -> List<WorkspaceId> = { listOf(it) },
    ) = PoolNewApps.placeNewApp(pool, appIdentity(pkg), "New", counterIds(pkg), layout, arrival, candidatesOf)

    private fun tinyPool(workspaces: List<WorkspaceId>) =
        PlacedItemPool(
            arrangements = workspaces.associateWith { Arrangement(listOf(ArrangementPage(P1, GridDimensions(1, 1)))) },
        )

    @Test
    fun theWorkspaceSettingDecidesNotTheArrangementsStoredValue() {
        // The arrangements say FINDER_ONLY (the as-built default); the workspaces' own settings win.
        val pool = emptyPool(listOf(W1, W2, W3))
        val result =
            place(pool, layout(ws(W1, presetId = "ios"), ws(W2, presetId = "nova"), ws(W3, newApps = homeOnly)))
        assertEquals(listOf(W1, W3), result.placedIn)
        val app = result.edit.pool.items.values.single()
        assertEquals(2, PoolReferences.count(result.edit.pool, app.id))
        assertEquals(
            listOf(app.id),
            result.edit.pool.arrangements.getValue(W1).pages.first().placements.map { it.item },
        )
        assertTrue(result.edit.pool.arrangements.getValue(W2).pages.all { it.placements.isEmpty() })
        assertInvariants(result.edit.pool)
    }

    @Test
    fun onlyAnInstallPlacesAnything() {
        val pool = emptyPool()
        val l = layout(ws(W1, newApps = homeOnly), ws(W2, newApps = homeOnly))
        listOf(AppArrival.PROFILE_AVAILABLE, AppArrival.UPDATED, AppArrival.RESTORED).forEach { arrival ->
            val result = place(pool, l, arrival = arrival)
            assertEquals(pool, result.edit.pool, "$arrival")
            assertEquals(emptyList(), result.placedIn)
        }
    }

    @Test
    fun aRepeatedEventIsIdempotentAndAWorkProfileTwinIsADifferentApp() {
        val l = layout(ws(W1, newApps = homeOnly))
        val once = place(emptyPool(listOf(W1)), l).edit.pool
        val twice = place(once, l)
        assertEquals(once, twice.edit.pool)
        assertEquals(listOf(W1), twice.skipped)
        val work =
            PoolNewApps.placeNewApp(
                once,
                appIdentity("new.app", AppProfile(AppProfileId("work-10"), AppProfileType.WORK)),
                "New",
                counterIds("w"),
                l,
                AppArrival.INSTALLED,
            )
        assertEquals(listOf(W1), work.placedIn)
        assertEquals(2, work.edit.pool.items.size)
    }

    @Test
    fun aFullWorkspaceGetsAPageOnlyWithAutoPages() {
        val pool = tinyPool(listOf(W1, W2)).add(poolApp("a"), W1).add(poolApp("b"), W2)
        val l = layout(ws(W1, presetId = "ios"), ws(W2, newApps = homeOnly))
        val result = place(pool, l)
        assertEquals(listOf(W1), result.placedIn)
        assertEquals(listOf(W1), result.pagesAdded)
        assertEquals(listOf(W2), result.skipped)
        val pages = result.edit.pool.arrangements.getValue(W1).pages
        assertEquals(2, pages.size)
        assertEquals(GridDimensions(1, 1), pages.last().grid)
        assertNotEquals(P1, pages.last().id)
        assertEquals(1, pages.last().placements.size)
        assertEquals(1, result.edit.pool.arrangements.getValue(W2).pages.size)
        assertInvariants(result.edit.pool)
    }

    @Test
    fun aBorrowingWorkspaceForksToTakeTheIconUnlessItAlreadyShowsIt() {
        val lender = WorkspaceId("ws:phone:standard")
        val pool = emptyPool(listOf(lender))
        val l = layout(ws(W1, presetId = "ios"), ws(lender))
        val candidates = { id: WorkspaceId -> listOf(id, lender) }
        val result = place(pool, l, candidatesOf = candidates)
        assertEquals(listOf(W1), result.placedIn)
        assertEquals(listOf(W1), result.forked)
        assertTrue(result.edit.pool.arrangements.getValue(lender).pages.all { it.placements.isEmpty() })
        assertInvariants(result.edit.pool)
        // The lender already shows the app: nothing to do, and no fork.
        val shown = place(pool.add(poolApp("x", "new.app"), lender), l, candidatesOf = candidates)
        assertEquals(emptyList(), shown.forked)
        assertEquals(listOf(W1), shown.skipped)
        // Without candidates a borrower is skipped.
        assertEquals(listOf(W1), place(pool, l).skipped)
    }

    @Test
    fun aForkThatCouldNotTakeTheIconIsDropped() {
        val lender = WorkspaceId("lender")
        val full = tinyPool(listOf(lender, W2)).add(poolApp("a"), lender)
        val l = layout(ws(W1, newApps = homeOnly), ws(W2, newApps = homeOnly), ws(lender))
        val result = place(full, l, candidatesOf = { listOf(it, lender) })
        assertEquals(listOf(W2), result.placedIn)
        assertEquals(emptyList(), result.forked)
        assertTrue(W1 !in result.edit.pool.arrangements)
    }

    @Test
    fun trailingEmptyPagesAreTrimmedButNeverTheFirstOrAPinnedOne() {
        val pinned = ArrangementPage(LauncherPageId("pin"), GridDimensions(4, 4), isPinned = true)
        val base = emptyPool(listOf(W1))
        val withPages =
            PoolAutoPages.appendPage(base, W1, counterIds())!!.first.let {
                PoolAutoPages.appendPage(it, W1, counterIds("m"))!!.first
            }
        assertEquals(4, withPages.arrangements.getValue(W1).pages.size)
        assertEquals(1, PoolAutoPages.trimTrailingEmpty(withPages, W1).arrangements.getValue(W1).pages.size)
        val kept = base.add(poolApp("a"), W1, P2)
        assertEquals(kept, PoolAutoPages.trimTrailingEmpty(kept, W1))
        val withPinned =
            base.copy(arrangements = mapOf(W1 to Arrangement(base.arrangements.getValue(W1).pages + pinned)))
        assertEquals(withPinned, PoolAutoPages.trimTrailingEmpty(withPinned, W1))
        assertEquals(base, PoolAutoPages.trimTrailingEmpty(base, W2))
    }

    @Test
    fun presetDefaultsOptInOnlyIosStyle() {
        assertEquals(NewAppPlacement.HOME_AND_FINDER, NewAppDefaults.forPreset("ios").placement)
        assertTrue(NewAppDefaults.forPreset("ios").autoPages)
        assertEquals(NewAppDefaults.MIGRATED, NewAppDefaults.forPreset("nova"))
    }
}
