package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HostedWidgetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PoolWidgetsAndNewAppsTest {
    private val w = PoolItemId("w")

    @Test
    fun separateCopyAllocatesANewInstanceForTheSameProvider() {
        val pool = emptyPool().add(poolWidget("w", host = 5))
        val edit = PoolWidgets.separateCopy(pool, w, W2, P1, counterIds(), HostedWidgetId(6)).done()
        val copy = edit.pool.items.getValue(PoolItemId("n1")) as PoolWidget
        assertEquals(provider, copy.provider)
        assertEquals(HostedWidgetId(6), copy.hostedId)
        assertEquals(1, edit.pool.refs("w"))
        assertEquals(1, edit.pool.refs("n1"))
        assertInvariants(edit.pool)
    }

    @Test
    fun separateCopyNeedsAProviderAndAWidget() {
        val pool = emptyPool().add(poolWidget("legacy", withProvider = false)).add(poolApp("a"))
        val legacy = PoolItemId("legacy")
        assertEquals(
            PoolRejection.PROVIDER_UNKNOWN,
            PoolWidgets.separateCopy(pool, legacy, W2, P1, counterIds(), null).rejection(),
        )
        assertEquals(
            PoolRejection.NOT_A_WIDGET,
            PoolWidgets.separateCopy(pool, PoolItemId("a"), W2, P1, counterIds(), null).rejection(),
        )
        assertEquals(
            PoolRejection.UNKNOWN_ITEM,
            PoolWidgets.separateCopy(pool, w, W2, P1, counterIds(), null).rejection(),
        )
    }

    @Test
    fun separateCopyCannotReuseALiveHostId() {
        val pool = emptyPool().add(poolWidget("w", host = 5))
        assertEquals(
            PoolRejection.HOST_ID_IN_USE,
            PoolWidgets.separateCopy(pool, w, W2, P1, counterIds(), HostedWidgetId(5)).rejection(),
        )
    }

    @Test
    fun bindAndUnbindMoveAPlaceholderInAndOutOfService() {
        val pool = emptyPool().add(poolWidget("w", host = null)).add(poolWidget("other", host = 8))
        assertEquals(PoolRejection.HOST_ID_IN_USE, PoolWidgets.bind(pool, w, HostedWidgetId(8)).rejection())
        val bound = PoolWidgets.bind(pool, w, HostedWidgetId(9)).done().pool
        assertEquals(PoolRejection.WIDGET_ALREADY_BOUND, PoolWidgets.bind(bound, w, HostedWidgetId(10)).rejection())
        val unbound = PoolWidgets.unbind(bound, w).done()
        assertEquals(setOf(HostedWidgetId(9)), unbound.releasedHostIds)
        assertEquals(null, (unbound.pool.items.getValue(w) as PoolWidget).hostedId)
    }

    @Test
    fun aNewAppIsOneSharedItemInEveryHomeAndFinderWorkspace() {
        val base = emptyPool(listOf(W1, W2), NewAppPlacement.HOME_AND_FINDER)
        val finderOnly = Arrangement(listOf(ArrangementPage(P1, GridDimensions(4, 4))))
        val pool = base.copy(arrangements = base.arrangements + (W3 to finderOnly))
        val result = PoolNewApps.placeNewApp(pool, appIdentity("new.app"), "New", counterIds())
        assertEquals(listOf(W1, W2), result.placedIn)
        assertEquals(1, result.edit.pool.items.size)
        assertEquals(2, PoolReferences.count(result.edit.pool, result.edit.pool.items.keys.single()))
        assertTrue(result.edit.pool.arrangements.getValue(W3).pages.all { it.placements.isEmpty() })
        assertInvariants(result.edit.pool)
    }

    @Test
    fun placingTheSameNewAppTwiceIsIdempotentAndNothingPlacedLeavesThePoolUnchanged() {
        val pool = emptyPool(newApps = NewAppPlacement.HOME_AND_FINDER)
        val first = PoolNewApps.placeNewApp(pool, appIdentity("x"), "X", counterIds()).edit.pool
        val again = PoolNewApps.placeNewApp(first, appIdentity("x"), "X", counterIds("m"))
        assertEquals(first, again.edit.pool)
        assertEquals(listOf(W1, W2), again.skipped)
        val finderOnly = emptyPool()
        assertEquals(finderOnly, PoolNewApps.placeNewApp(finderOnly, appIdentity("x"), "X", counterIds()).edit.pool)
    }

    @Test
    fun aFullWorkspaceIsSkippedNotOverfilled() {
        val tiny = ArrangementPage(P1, GridDimensions(1, 1))
        val pool =
            PlacedItemPool(arrangements = mapOf(W1 to Arrangement(listOf(tiny), NewAppPlacement.HOME_AND_FINDER)))
        val one = PoolNewApps.placeNewApp(pool, appIdentity("a"), "A", counterIds()).edit.pool
        val two = PoolNewApps.placeNewApp(one, appIdentity("b"), "B", counterIds("m"))
        assertEquals(listOf(W1), two.skipped)
        assertEquals(one, two.edit.pool)
    }
}
