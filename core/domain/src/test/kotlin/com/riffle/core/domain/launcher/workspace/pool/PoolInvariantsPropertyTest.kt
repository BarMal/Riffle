package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridPlacementEngine
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Seeded random operation sequences: the invariants and the host-id accounting hold after every step. */
class PoolInvariantsPropertyTest {
    private class Run(seed: Int) {
        val rnd = Random(seed)
        val ids = counterIds("g$seed-")
        var counter = 0
        val workspaces = listOf(W1, W2, W3, WorkspaceId("w4"))
        val pages = listOf(P1, P2, LauncherPageId("p3"))

        fun ws() = workspaces[rnd.nextInt(workspaces.size)]

        fun page() = pages[rnd.nextInt(pages.size)]

        fun cell(): GridPlacement? =
            if (rnd.nextInt(3) == 0) {
                null
            } else {
                at(
                    rnd.nextInt(5),
                    rnd.nextInt(5),
                    1 + rnd.nextInt(2),
                    1 + rnd.nextInt(2),
                )
            }

        fun item(pool: PlacedItemPool): PoolItemId =
            pool.items.keys.toList().let {
                if (it.isEmpty() || rnd.nextInt(10) == 0) PoolItemId("none") else it[rnd.nextInt(it.size)]
            }

        fun newItem(): PoolItem {
            counter++
            return when (rnd.nextInt(3)) {
                0 -> poolApp("i$counter", "p${rnd.nextInt(4)}")
                1 -> poolFolder("i$counter", "p${rnd.nextInt(4)}", "p${rnd.nextInt(4)}")
                else -> poolWidget("i$counter", host = if (rnd.nextInt(5) == 0) null else 1000 + counter)
            }
        }
    }

    private fun PoolResult.edit(): PoolEdit? = (this as? PoolResult.Done)?.edit

    private fun structural(
        r: Run,
        pool: PlacedItemPool,
    ): PoolEdit? =
        when (r.rnd.nextInt(8)) {
            0, 1, 2 -> PoolPlacement.addNew(pool, r.ws(), r.page(), r.newItem(), r.cell()).edit()
            3 -> PoolPlacement.placeExisting(pool, r.ws(), r.page(), r.item(pool), r.cell()).edit()
            4 -> PoolPlacement.shareFolder(pool, r.ws(), r.page(), r.item(pool), r.cell()).edit()
            5 -> PoolPlacement.move(pool, r.ws(), r.item(pool), r.page(), r.cell() ?: at(0, 0)).edit()
            6 -> PoolPlacement.moveToWorkspace(pool, r.item(pool), r.ws(), r.ws(), r.page(), r.cell()).edit()
            else -> PoolRemoval.remove(pool, r.ws(), r.item(pool)).edit()
        }

    private fun removalsAndCopies(
        r: Run,
        pool: PlacedItemPool,
    ): PoolEdit? =
        when (r.rnd.nextInt(8)) {
            0 -> PoolRemoval.removeEverywhere(pool, r.item(pool)).edit()
            1 -> PoolRemoval.deleteWorkspace(pool, r.ws()).edit()
            2 -> PoolCopy.duplicateWorkspace(pool, r.ws(), r.ws(), r.ids, CopyMode.entries[r.rnd.nextInt(2)]).edit()
            3 -> PoolCopy.copyArrangement(pool, r.ws(), r.ws(), r.ids).edit()
            4 -> PoolCopy.makeFolderIndependent(pool, r.ws(), r.item(pool), r.ids).edit()
            5 -> PoolCopy.copyFromOtherLayout(pool, pool, mapOf(W1 to W1, W2 to W3), r.ids)
            6 -> PoolNewApps.placeNewApp(pool, appIdentity("p${r.rnd.nextInt(4)}"), "n", r.ids).edit
            else -> PoolRemoval.pruneUninstalled(pool, AppPackageName("p${r.rnd.nextInt(4)}"), AppProfile.personal())
        }

    private fun widgetsAndIngest(
        r: Run,
        pool: PlacedItemPool,
    ): PoolEdit? {
        val host = if (r.rnd.nextBoolean()) null else HostedWidgetId(5000 + r.counter++)
        return when (r.rnd.nextInt(4)) {
            0 -> PoolWidgets.separateCopy(pool, r.item(pool), r.ws(), r.page(), r.ids, host, r.cell()).edit()
            1 -> PoolWidgets.bind(pool, r.item(pool), HostedWidgetId(7000 + r.counter)).edit()
            2 -> PoolWidgets.unbind(pool, r.item(pool)).edit()
            else -> ingestWithRandomRemoval(r, pool)
        }
    }

    private fun ingestWithRandomRemoval(
        r: Run,
        pool: PlacedItemPool,
    ): PoolEdit? {
        val ws = r.ws()
        val arrangement = pool.arrangements[ws]
        val victim = r.item(pool).value
        val edited =
            arrangement?.let { a ->
                ArrangementAdapter.toLauncherPages(a, pool).map { page ->
                    GridPlacementEngine().removeItem(page, com.riffle.core.domain.launcher.home.LauncherItemId(victim))
                }
            }
        return edited?.let { ArrangementIngest.ingest(pool, ws, it, r.ids).edit() }
    }

    @Test
    fun invariantsAndHostIdAccountingHoldAfterEveryRandomOperation() {
        for (seed in 1..80) {
            val r = Run(seed)
            var pool = emptyPool(listOf(W1, W2, W3), NewAppPlacement.HOME_AND_FINDER)
            repeat(120) { step ->
                val edit =
                    when (r.rnd.nextInt(3)) {
                        0 -> structural(r, pool)
                        1 -> removalsAndCopies(r, pool)
                        else -> widgetsAndIngest(r, pool)
                    }
                if (edit != null) {
                    assertInvariants(edit.pool)
                    val dropped = PoolReferences.hostIds(pool) - PoolReferences.hostIds(edit.pool)
                    assertTrue(edit.releasedHostIds.containsAll(dropped), "seed $seed step $step leaked $dropped")
                    assertTrue(
                        edit.releasedHostIds.none { it in PoolReferences.hostIds(edit.pool) },
                        "seed $seed step $step released a live id",
                    )
                    pool = edit.pool
                }
                assertTrue(PoolValidation.repair(pool).isClean, "seed $seed step $step repair found issues")
            }
        }
    }

    @Test
    fun aRejectedOperationReturnsTheSameValueAndAnUndoRestoresItExactly() {
        val pool = emptyPool().add(poolWidget("w"), cell = at(0, 0)).add(poolApp("a"), cell = at(1, 0))
        val rejected = PoolPlacement.placeExisting(pool, W2, P1, PoolItemId("w"))
        assertEquals(pool, rejected.poolOr(pool))
        val deleted = PoolRemoval.deleteWorkspace(pool, W1).done()
        assertTrue(deleted.pool.items.isEmpty())
        // Undo is "keep the previous immutable value": it is still intact and its host ids are not queued for deletion.
        assertEquals(setOf(HostedWidgetId(100)), deleted.releasedHostIds)
        assertEquals(emptySet(), HostIdDeletionQueue().plus(deleted).withoutReferenced(pool).pending)
    }
}
