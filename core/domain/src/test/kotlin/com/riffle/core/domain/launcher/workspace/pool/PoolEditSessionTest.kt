package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PoolEditSessionTest {
    private val start =
        emptyPool().add(poolApp("a"), cell = at(0, 0)).add(poolWidget("w", host = 41), cell = at(1, 0, 2, 2))

    @Test
    fun anEditIsOneUndoStepAndRedoReappliesIt() {
        val step = PoolEditSession(start).apply(PoolHomeEditing.nudge(start, W1, PoolItemId("a"), 0, 1))
        assertTrue(step.changed)
        assertTrue(step.session.canUndo)
        val undone = step.session.undo()
        assertEquals(start, undone.pool)
        assertFalse(undone.canUndo)
        assertTrue(undone.canRedo)
        assertEquals(step.session.pool, undone.redo().pool)
    }

    @Test
    fun aRejectedOrNoOpEditLeavesTheSessionUntouched() {
        val session = PoolEditSession(start)
        val rejected = session.apply(PoolHomeEditing.nudge(start, W1, PoolItemId("a"), -1, 0))
        assertFalse(rejected.changed)
        assertEquals(session, rejected.session)
        val noop = session.apply(PoolResult.Done(PoolEdit(start)))
        assertFalse(noop.changed)
        assertEquals(session, noop.session)
    }

    @Test
    fun aNewEditClearsRedo() {
        var s = PoolEditSession(start).apply(PoolHomeEditing.nudge(start, W1, PoolItemId("a"), 0, 1)).session.undo()
        assertTrue(s.canRedo)
        s = s.apply(PoolHomeEditing.nudge(s.pool, W1, PoolItemId("a"), 0, 2)).session
        assertFalse(s.canRedo)
    }

    @Test
    fun removingAWidgetQueuesItsHostIdAndUndoForgetsItRedoQueuesItAgain() {
        val removed = PoolEditSession(start).apply(PoolRemoval.remove(start, W1, PoolItemId("w"))).session
        assertEquals(setOf(HostedWidgetId(41)), removed.queue.pending)
        val undone = removed.undo()
        assertTrue(undone.queue.pending.isEmpty())
        assertEquals(setOf(HostedWidgetId(41)), undone.redo().queue.pending)
    }

    @Test
    fun finishDrainsTheQueueOnceAndKeepsThePool() {
        val removed = PoolEditSession(start).apply(PoolRemoval.remove(start, W1, PoolItemId("w"))).session
        val (ids, closed) = removed.finish()
        assertEquals(setOf(HostedWidgetId(41)), ids)
        assertEquals(removed.pool, closed.pool)
        assertFalse(closed.canUndo)
        assertTrue(closed.finish().first.isEmpty())
    }

    @Test
    fun movingAWidgetKeepsItsHostIdOutOfTheQueue() {
        val moved =
            PoolEditSession(start)
                .apply(PoolHomeEditing.moveToCell(start, W1, PoolItemId("w"), P2, GridCell(0, 0)))
        assertTrue(moved.changed)
        assertTrue(moved.session.queue.pending.isEmpty())
    }

    @Test
    fun historyIsBounded() {
        var s = PoolEditSession(start)
        repeat(PoolEditSession.MAX_HISTORY + 20) { n ->
            val rows = if (n % 2 == 0) 1 else -1
            s = s.apply(PoolHomeEditing.nudge(s.pool, W1, PoolItemId("a"), 0, rows)).session
        }
        var undone = 0
        while (s.canUndo) {
            s = s.undo()
            undone++
        }
        assertEquals(PoolEditSession.MAX_HISTORY, undone)
    }

    @Test
    fun randomEditSequencesKeepInvariantsAndUndoRedoAreExact() {
        for (seed in 1..60) {
            val rnd = Random(seed)
            val ids = counterIds("s$seed-")
            val initial =
                emptyPool(listOf(W1, W2))
                    .add(poolApp("a"), cell = at(0, 0))
                    .add(poolWidget("w", 500 + seed), cell = at(1, 1, 2, 2))
            var session = PoolEditSession(initial)
            var applied = 0
            repeat(40) { step ->
                val next = session.apply(randomOp(rnd, ids, session.pool, step))
                if (next.changed) applied++
                session = next.session
                assertInvariants(session.pool)
                val live = PoolReferences.hostIds(session.pool)
                assertTrue(session.queue.pending.none { it in live }, "seed $seed step $step queued a live id")
            }
            val finalPool = session.pool
            val dropped = PoolReferences.hostIds(initial) - PoolReferences.hostIds(finalPool)
            assertEquals(dropped, session.finish().first.intersect(dropped), "seed $seed leaked host ids")
            var undone = 0
            while (session.canUndo) {
                session = session.undo()
                assertInvariants(session.pool)
                undone++
            }
            assertEquals(initial, session.pool, "seed $seed")
            assertEquals(minOf(applied, PoolEditSession.MAX_HISTORY), undone)
            while (session.canRedo) session = session.redo()
            assertEquals(finalPool, session.pool, "seed $seed redo")
        }
    }

    private fun randomOp(
        rnd: Random,
        ids: WorkspaceIdFactory,
        pool: PlacedItemPool,
        step: Int,
    ): PoolResult {
        val items = pool.items.keys.toList()
        val item = if (items.isEmpty()) PoolItemId("none") else items[rnd.nextInt(items.size)]
        val ws = if (rnd.nextBoolean()) W1 else W2
        return when (rnd.nextInt(9)) {
            0 -> PoolHomeEditing.nudge(pool, ws, item, rnd.nextInt(3) - 1, rnd.nextInt(3) - 1)
            1 -> PoolHomeEditing.moveToAdjacentPage(pool, ws, item, if (rnd.nextBoolean()) 1 else -1)
            2 -> PoolHomeEditing.moveToCell(pool, ws, item, P1, GridCell(rnd.nextInt(4), rnd.nextInt(4)))
            3 -> PoolHomeEditing.addApp(pool, ws, P1, appIdentity("p${rnd.nextInt(6)}"), "p$step", ids)
            4 -> PoolRemoval.remove(pool, ws, item)
            5 -> PoolRemoval.removeEverywhere(pool, item)
            6 -> PoolWidgets.separateCopy(pool, item, ws, P2, ids, null)
            7 -> PoolHomeEditing.addPage(pool, ws, ids)
            else -> PoolPlacement.placeExisting(pool, ws, P2, item)
        }
    }
}
