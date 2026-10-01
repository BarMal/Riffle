package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.HostedWidgetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PoolCopyTest {
    private val source =
        emptyPool(newApps = NewAppPlacement.HOME_AND_FINDER)
            .add(poolApp("a"), cell = at(0, 0))
            .add(poolFolder("f", "x"), cell = at(1, 0))
            .add(poolWidget("w", host = 9), page = P2, cell = at(0, 1, 2, 2))

    private fun items(
        pool: PlacedItemPool,
        ws: com.riffle.core.domain.launcher.workspace.WorkspaceId,
    ) = pool.arrangements.getValue(ws).pages.flatMap { it.placements }.map { it.item }

    @Test
    fun duplicateClonesByDefaultAndWidgetsBecomePlaceholders() {
        val edit = PoolCopy.duplicateWorkspace(source, W1, W3, counterIds()).done()
        val copy = edit.pool
        assertEquals(3, items(copy, W3).size)
        assertTrue(items(copy, W3).none { it in items(copy, W1) }, "nothing is shared")
        assertEquals(
            source.arrangements.getValue(W1).pages.map { it.placements.map { p -> p.at } },
            copy.arrangements.getValue(W3).pages.map { it.placements.map { p -> p.at } },
        )
        val widgets = copy.items.values.filterIsInstance<PoolWidget>()
        assertEquals(2, widgets.size)
        val placeholder = widgets.first { it.hostedId == null }
        assertEquals(provider, placeholder.provider)
        assertEquals(HostedWidgetId(9), widgets.first { it.hostedId != null }.hostedId)
        assertEquals(source.arrangements.getValue(W1).newAppPlacement, copy.arrangements.getValue(W3).newAppPlacement)
        assertInvariants(copy)
    }

    @Test
    fun clonedFoldersAreIndependent() {
        val copy = PoolCopy.duplicateWorkspace(source, W1, W3, counterIds()).done().pool
        val originalFolder = copy.items.getValue(PoolItemId("f")) as PoolFolder
        val clone = items(copy, W3).map { copy.items.getValue(it) }.filterIsInstance<PoolFolder>().single()
        assertNotEquals(originalFolder.id, clone.id)
        assertEquals(originalFolder.entries, clone.entries)
    }

    @Test
    fun keepSharedReferencesAppsAndFoldersButNeverWidgets() {
        val copy = PoolCopy.duplicateWorkspace(source, W1, W3, counterIds(), CopyMode.KEEP_SHARED).done().pool
        assertEquals(2, copy.refs("a"))
        assertEquals(2, copy.refs("f"))
        assertEquals(1, copy.refs("w"))
        assertInvariants(copy)
    }

    @Test
    fun duplicateRefusesAnExistingTargetAndCopyArrangementReplacesIt() {
        assertEquals(
            PoolRejection.WORKSPACE_EXISTS,
            PoolCopy.duplicateWorkspace(source, W1, W2, counterIds()).rejection(),
        )
        assertEquals(PoolRejection.SAME_WORKSPACE, PoolCopy.copyArrangement(source, W1, W1, counterIds()).rejection())
        val withTarget = source.add(poolWidget("old", host = 55), ws = W2)
        val edit = PoolCopy.copyArrangement(withTarget, W1, W2, counterIds()).done()
        assertNull(edit.pool.items[PoolItemId("old")])
        assertEquals(setOf(HostedWidgetId(55)), edit.releasedHostIds)
        assertInvariants(edit.pool)
    }

    @Test
    fun copyFromOtherLayoutClonesWithFreshIdsAndKeepsSharingAmongCopiedWorkspaces() {
        val shared = PoolPlacement.placeExisting(source, W2, P1, PoolItemId("a")).done().pool
        val target = emptyPool(listOf(W3)).add(poolWidget("t", host = 3), ws = W3).add(poolApp("a"), ws = W3, page = P2)
        val edit = PoolCopy.copyFromOtherLayout(shared, target, mapOf(W1 to W1, W2 to W2), counterIds("c"))
        val pool = edit.pool
        assertEquals(setOf(W1, W2), pool.arrangements.keys)
        assertEquals(setOf(HostedWidgetId(3)), edit.releasedHostIds)
        val copiedA = items(pool, W1).first { pool.items[it] is PoolApp }
        assertTrue(copiedA in items(pool, W2), "an app shared by two copied workspaces stays shared")
        assertNotEquals(PoolItemId("a"), copiedA)
        assertTrue(pool.items.keys.none { it.value in setOf("a", "f", "w", "t") })
        assertTrue(pool.items.values.filterIsInstance<PoolWidget>().all { it.hostedId == null })
        assertInvariants(pool)
    }

    @Test
    fun makeIndependentGivesTheWorkspaceItsOwnFolder() {
        val shared = PoolPlacement.shareFolder(source, W2, P1, PoolItemId("f")).done().pool
        val own = PoolCopy.makeFolderIndependent(shared, W2, PoolItemId("f"), counterIds()).done().pool
        assertEquals(1, own.refs("f"))
        assertEquals(2, own.items.values.filterIsInstance<PoolFolder>().size)
        assertEquals(own, PoolCopy.makeFolderIndependent(own, W2, PoolItemId("n1"), counterIds()).done().pool)
        assertInvariants(own)
    }
}
