package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.HostedWidgetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PoolArrangementsTest {
    private val source =
        emptyPool(listOf(W1), NewAppPlacement.HOME_AND_FINDER)
            .add(poolApp("a"), cell = at(0, 0))
            .add(poolFolder("f", "x"), cell = at(1, 0))
            .add(poolWidget("w", host = 9), page = P2, cell = at(0, 1, 2, 2))

    private fun refsIn(
        pool: PlacedItemPool,
        ws: com.riffle.core.domain.launcher.workspace.WorkspaceId,
    ) = pool.arrangements.getValue(ws).pages.flatMap { it.placements }.map { it.item }

    @Test
    fun forkSharesAppsAndWidgetsClonesFoldersAndCopiesPages() {
        val edit = PoolArrangements.fork(source, W3, listOf(W3, W1), counterIds("k")).done()
        val pool = edit.pool
        assertEquals(
            source.arrangements.getValue(W1).pages.map { it.id },
            pool.arrangements.getValue(W3).pages.map { it.id },
        )
        assertEquals(
            source.arrangements.getValue(W1).pages.map { page -> page.placements.map { it.at } },
            pool.arrangements.getValue(W3).pages.map { page -> page.placements.map { it.at } },
        )
        assertEquals(2, pool.refs("a"), "app shared by reference")
        assertEquals(2, pool.refs("w"), "widget instance shared by reference")
        assertEquals(1, pool.refs("f"), "folder cloned")
        val clone = refsIn(pool, W3).map { pool.items.getValue(it) }.filterIsInstance<PoolFolder>().single()
        assertNotEquals(PoolItemId("f"), clone.id)
        assertEquals((source.items.getValue(PoolItemId("f")) as PoolFolder).entries, clone.entries)
        assertEquals(NewAppPlacement.HOME_AND_FINDER, pool.arrangements.getValue(W3).newAppPlacement)
        assertEquals(emptySet(), edit.releasedHostIds)
        assertEquals(1, pool.items.values.count { it is PoolWidget })
        assertInvariants(pool)
    }

    @Test
    fun forkIsIdempotentAndRefusedWithNothingToBorrow() {
        val forked = PoolArrangements.fork(source, W3, listOf(W3, W1), counterIds()).done().pool
        assertEquals(forked, PoolArrangements.fork(forked, W3, listOf(W3, W1), counterIds("z")).done().pool)
        assertEquals(forked, PoolArrangements.fork(forked, W1, listOf(W1), counterIds("z")).done().pool)
        assertEquals(
            PoolRejection.NOTHING_TO_FORK,
            PoolArrangements.fork(source, W3, listOf(W3, W2), counterIds()).rejection(),
        )
        assertTrue(PoolArrangements.ownsArrangement(forked, W3))
    }

    @Test
    fun editsAfterAForkStayInTheForkButTheSharedWidgetSurvivesEitherSideRemovingIt() {
        val forked = PoolArrangements.fork(source, W3, listOf(W3, W1), counterIds()).done().pool
        val moved = PoolPlacement.move(forked, W3, PoolItemId("a"), P1, at(3, 3)).done().pool
        assertEquals(at(0, 0), PoolHomeEditing.siteOf(moved, W1, PoolItemId("a"))!!.placement.at)
        val removed = PoolRemoval.remove(moved, W3, PoolItemId("w")).done()
        assertEquals(emptySet(), removed.releasedHostIds)
        assertEquals(HostedWidgetId(9), (removed.pool.items.getValue(PoolItemId("w")) as PoolWidget).hostedId)
        val gone = PoolRemoval.remove(removed.pool, W1, PoolItemId("w")).done()
        assertEquals(setOf(HostedWidgetId(9)), gone.releasedHostIds)
    }

    @Test
    fun forkLeavesTheDockAlone() {
        val docked =
            source.copy(
                items = source.items + (PoolItemId("d") to poolApp("d")),
                dock = PoolDock(pins = listOf(PoolItemId("d"))),
            )
        val forked = PoolArrangements.fork(docked, W3, listOf(W3, W1), counterIds()).done().pool
        assertEquals(docked.dock, forked.dock)
        assertEquals(1, forked.refs("d"))
        assertInvariants(forked)
    }
}
