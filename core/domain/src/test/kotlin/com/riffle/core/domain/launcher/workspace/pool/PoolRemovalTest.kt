package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.home.HostedWidgetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PoolRemovalTest {
    private val a = PoolItemId("a")

    private fun sharedApp(): PlacedItemPool =
        emptyPool().add(poolApp("a")).let { PoolPlacement.placeExisting(it, W2, P1, a).done().pool }

    @Test
    fun removingOneOfTwoReferencesKeepsTheItem() {
        val edit = PoolRemoval.remove(sharedApp(), W1, a).done()
        assertEquals(1, edit.pool.refs("a"))
        assertTrue(edit.removedItems.isEmpty())
    }

    @Test
    fun removingTheLastReferenceCollectsTheItem() {
        val edit = PoolRemoval.remove(emptyPool().add(poolApp("a")), W1, a).done()
        assertFalse(a in edit.pool.items)
        assertEquals(listOf(poolApp("a")), edit.removedItems)
        assertInvariants(edit.pool)
    }

    @Test
    fun removeEverywhereDropsEveryReference() {
        val edit = PoolRemoval.removeEverywhere(sharedApp(), a).done()
        assertTrue(edit.pool.items.isEmpty())
        assertEquals(PoolRejection.UNKNOWN_ITEM, PoolRemoval.removeEverywhere(edit.pool, a).rejection())
        assertEquals(
            PoolRejection.NOT_PLACED_HERE,
            PoolRemoval.remove(emptyPool().add(poolApp("a")), W2, a).rejection(),
        )
    }

    @Test
    fun collectingAWidgetReleasesItsHostIdForDeferredDeletion() {
        val pool = emptyPool().add(poolWidget("w", host = 7))
        val edit = PoolRemoval.remove(pool, W1, PoolItemId("w")).done()
        assertEquals(setOf(HostedWidgetId(7)), edit.releasedHostIds)
        assertEquals(1, pool.refs("w"))
    }

    @Test
    fun undoIsExactBecauseThePreviousValueIsRestoredAndTheQueueForgetsReleasedIds() {
        val pool = emptyPool().add(poolWidget("w", host = 7)).add(poolApp("a"))
        val edit = PoolRemoval.deleteWorkspace(pool, W1).done()
        val queue = HostIdDeletionQueue().plus(edit)
        assertEquals(setOf(HostedWidgetId(7)), queue.pending)
        assertEquals(emptySet(), queue.withoutReferenced(pool).pending)
        val (now, rest) = queue.drain()
        assertEquals(setOf(HostedWidgetId(7)), now)
        assertTrue(rest.pending.isEmpty())
    }

    @Test
    fun deletingAWorkspaceKeepsSharedItemsAndReportsTheImpact() {
        val pool = sharedApp().add(poolApp("solo"), page = P2).add(poolWidget("w"), page = P2)
        val impact = PoolRemoval.deletionImpact(pool, W1)
        assertEquals(setOf(PoolItemId("solo"), PoolItemId("w")), impact.exclusiveItems.map { it.id }.toSet())
        assertEquals(listOf(a), impact.sharedItems.map { it.id })
        assertEquals(1, impact.exclusiveWidgets)
        val edit = PoolRemoval.deleteWorkspace(pool, W1).done()
        assertEquals(setOf(a), edit.pool.items.keys)
        assertEquals(PoolRejection.UNKNOWN_WORKSPACE, PoolRemoval.deleteWorkspace(edit.pool, W1).rejection())
    }

    @Test
    fun uninstallPrunesPlacementsFolderEntriesAndEmptiedFolders() {
        val pool =
            emptyPool()
                .add(poolApp("a", "gone.pkg"))
                .add(poolApp("keep", "keep.pkg"))
                .add(poolFolder("f1", "gone.pkg", "keep.pkg"))
                .add(poolFolder("f2", "gone.pkg"))
                .add(poolApp("work", "gone.pkg").copy(appIdentity = appIdentity("gone.pkg", AppProfile.work())))
        val edit = PoolRemoval.pruneUninstalled(pool, AppPackageName("gone.pkg"), AppProfile.personal())
        assertEquals(setOf("keep", "f1", "work"), edit.pool.items.keys.map { it.value }.toSet())
        assertEquals(
            listOf("f1-keep.pkg"),
            (edit.pool.items.getValue(PoolItemId("f1")) as PoolFolder).entries.map {
                it.entryId
            },
        )
        assertEquals(
            PoolRemoval.pruneUninstalled(edit.pool, AppPackageName("gone.pkg"), AppProfile.personal()).pool,
            edit.pool,
        )
        assertInvariants(edit.pool)
    }
}
