package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.WidgetResizeConstraints
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PoolPlacementTest {
    @Test
    fun addNewPlacesInFirstFreeCellAndCreatesOneReference() {
        val pool = emptyPool().add(poolApp("a")).add(poolApp("b"))
        val placements = pool.arrangements.getValue(W1).pages.first().placements
        assertEquals(listOf(at(0, 0), at(1, 0)), placements.map { it.at })
        assertEquals(1, pool.refs("a"))
        assertInvariants(pool)
    }

    @Test
    fun rejectedAddLeavesNoTraceInThePool() {
        val pool = emptyPool().add(poolApp("a"), cell = at(0, 0))
        assertEquals(PoolRejection.COLLISION, PoolPlacement.addNew(pool, W1, P1, poolApp("b"), at(0, 0)).rejection())
        assertEquals(
            PoolRejection.OUT_OF_BOUNDS,
            PoolPlacement.addNew(pool, W1, P1, poolApp("b"), at(4, 4)).rejection(),
        )
        assertEquals(PoolRejection.ITEM_ID_IN_USE, PoolPlacement.addNew(pool, W2, P1, poolApp("a")).rejection())
        assertEquals(PoolRejection.UNKNOWN_WORKSPACE, PoolPlacement.addNew(pool, W3, P1, poolApp("b")).rejection())
        assertEquals(
            PoolRejection.UNKNOWN_PAGE,
            PoolPlacement.addNew(pool, W1, LauncherPageId("missing"), poolApp("b")).rejection(),
        )
        assertNull(pool.items[PoolItemId("b")])
    }

    @Test
    fun appsAreFreelySharedButOncePerArrangement() {
        val pool = emptyPool().add(poolApp("a"))
        val shared = PoolPlacement.placeExisting(pool, W2, P1, PoolItemId("a")).done().pool
        assertEquals(2, shared.refs("a"))
        assertEquals(
            PoolRejection.ALREADY_IN_ARRANGEMENT,
            PoolPlacement.placeExisting(shared, W2, P2, PoolItemId("a")).rejection(),
        )
        assertInvariants(shared)
    }

    @Test
    fun foldersAreSharedOnlyByTheExplicitAction() {
        val pool = emptyPool().add(poolFolder("f", "x", "y"))
        assertEquals(
            PoolRejection.FOLDER_SHARING_IS_EXPLICIT,
            PoolPlacement.placeExisting(pool, W2, P1, PoolItemId("f")).rejection(),
        )
        val shared = PoolPlacement.shareFolder(pool, W2, P1, PoolItemId("f")).done().pool
        assertEquals(2, shared.refs("f"))
        assertEquals(
            PoolRejection.NOT_A_FOLDER,
            PoolPlacement.shareFolder(shared.add(poolApp("a")), W2, P1, PoolItemId("a")).rejection(),
        )
        assertEquals(
            PoolRejection.UNKNOWN_ITEM,
            PoolPlacement.shareFolder(shared, W2, P1, PoolItemId("zz")).rejection(),
        )
    }

    @Test
    fun aWidgetHasASinglePlacementInTheLayout() {
        val pool = emptyPool().add(poolWidget("w"))
        assertEquals(
            PoolRejection.WIDGET_ALREADY_PLACED,
            PoolPlacement.placeExisting(pool, W2, P1, PoolItemId("w")).rejection(),
        )
        assertEquals(
            PoolRejection.WIDGET_ALREADY_PLACED,
            PoolPlacement.placeExisting(pool, W1, P2, PoolItemId("w")).rejection(),
        )
        assertEquals(
            PoolRejection.HOST_ID_IN_USE,
            PoolPlacement.addNew(pool, W2, P1, poolWidget("w2", host = 100)).rejection(),
        )
    }

    @Test
    fun movingAWidgetToAnotherWorkspaceMovesItsOnePlacementAndKeepsTheHostId() {
        val pool = emptyPool().add(poolWidget("w"), cell = at(0, 0))
        val moved = PoolPlacement.moveToWorkspace(pool, PoolItemId("w"), W1, W2, P2, at(1, 1)).done()
        assertEquals(HostedWidgetId(100), (moved.pool.items.getValue(PoolItemId("w")) as PoolWidget).hostedId)
        assertEquals(emptyList(), moved.pool.arrangements.getValue(W1).pages.flatMap { it.placements })
        assertEquals(emptySet(), moved.releasedHostIds)
        assertInvariants(moved.pool)
    }

    @Test
    fun widgetSpanMustSatisfyProviderConstraints() {
        val tall = poolWidget("w").copy(resizeConstraints = WidgetResizeConstraints(minSpan = GridSpan(2, 2)))
        val pool = emptyPool()
        assertEquals(PoolRejection.INVALID_PLACEMENT, PoolPlacement.addNew(pool, W1, P1, tall, at(0, 0)).rejection())
        assertTrue(PoolPlacement.addNew(pool, W1, P1, tall, at(0, 0, 2, 2)).done().pool.refs("w") == 1)
    }

    @Test
    fun moveWorksWithinAndAcrossPagesAndRespectsCollisions() {
        val pool = emptyPool().add(poolApp("a"), cell = at(0, 0)).add(poolApp("b"), cell = at(1, 0))
        val within = PoolPlacement.move(pool, W1, PoolItemId("a"), P1, at(3, 3)).done().pool
        assertEquals(
            at(3, 3),
            within.arrangements.getValue(W1).pages.first().placements.first { it.item.value == "a" }.at,
        )
        assertEquals(PoolRejection.COLLISION, PoolPlacement.move(pool, W1, PoolItemId("a"), P1, at(1, 0)).rejection())
        val across = PoolPlacement.move(pool, W1, PoolItemId("a"), P2, at(2, 2)).done().pool
        assertEquals(listOf("b"), across.arrangements.getValue(W1).pages[0].placements.map { it.item.value })
        assertEquals(listOf("a"), across.arrangements.getValue(W1).pages[1].placements.map { it.item.value })
        assertEquals(
            PoolRejection.NOT_PLACED_HERE,
            PoolPlacement.move(pool, W2, PoolItemId("a"), P1, at(0, 0)).rejection(),
        )
        assertNotEquals(pool, across)
        assertInvariants(across)
    }

    @Test
    fun movingAnAppBetweenWorkspacesLeavesOtherReferencesAlone() {
        val pool = emptyPool().add(poolApp("a"), cell = at(0, 0))
        val moved = PoolPlacement.moveToWorkspace(pool, PoolItemId("a"), W1, W2, P1).done().pool
        assertEquals(1, moved.refs("a"))
        assertEquals(
            PoolRejection.SAME_WORKSPACE,
            PoolPlacement.moveToWorkspace(pool, PoolItemId("a"), W1, W1, P1).rejection(),
        )
    }
}
