package com.riffle.app.launcher.diagnostics.pair

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairHolderMachineTest {
    private var now = 0L
    private val all = mutableListOf<HolderEffect>()
    private val h1 = HeldVariants.all[0]

    private fun machine(variant: HeldVariant = h1) = PairHolderMachine(variant) { now }

    private fun List<HolderEffect>.keep(): List<HolderEffect> = also { all += it }

    private fun PairHolderMachine.tickAt(time: Long): List<HolderEffect> {
        now = time
        return onTick().keep()
    }

    private fun notes() = all.filterIsInstance<HolderEffect.Note>().map { it.text }

    private fun count(type: Class<*>) = all.count { type.isInstance(it) }

    @Test
    fun theHappyPathFollowsTheSettleSplitSettleAndFinishTimings() {
        val m = machine()
        now = 1_000
        m.onResume().keep()
        assertEquals(HolderPhase.Settling, m.phase)
        assertEquals(1_300L, m.wakeAt)
        assertTrue(m.tickAt(1_299).none { it !is HolderEffect.Note })

        val first = m.tickAt(1_300)
        assertEquals(listOf(HolderEffect.StartFirst(h1.firstFlags)), first.filterNot { it is HolderEffect.Note })
        assertEquals(HolderPhase.WaitingForSplit, m.phase)
        assertEquals(4_300L, m.wakeAt)

        now = 1_700
        m.onSplitSignal(true, "onMultiWindowModeChanged").keep()
        assertEquals(HolderPhase.SplitSettling, m.phase)
        assertEquals(2_100L, m.wakeAt)
        assertTrue(m.tickAt(2_099).none { it !is HolderEffect.Note })

        val second = m.tickAt(2_100)
        assertEquals(listOf(HolderEffect.StartSecond(h1.secondFlags)), second.filterNot { it is HolderEffect.Note })
        assertEquals(HolderPhase.Finishing, m.phase)
        assertEquals(2_400L, m.wakeAt)
        assertTrue(m.tickAt(2_399).none { it !is HolderEffect.Note })

        val done = m.tickAt(2_400)
        assertTrue(done.contains(HolderEffect.Finish))
        assertEquals(HolderPhase.Done, m.phase)
        assertNull(m.wakeAt)
        assertEquals("finished after the second start", m.finalState)
    }

    @Test
    fun aSplitThatIsNeverObservedTimesOutNotesItAndStartsTheSecondAnyway() {
        val m = machine()
        m.onResume().keep()
        m.tickAt(300)
        assertTrue(m.tickAt(3_299).none { it !is HolderEffect.Note })
        val timeout = m.tickAt(3_300)
        assertTrue(timeout.contains(HolderEffect.StartSecond(h1.secondFlags)))
        assertTrue(notes().toString(), notes().any { it.startsWith("no split after first start") })
        assertEquals(HolderPhase.Finishing, m.phase)
        m.tickAt(3_600)
        assertEquals(HolderPhase.Done, m.phase)
        assertEquals(1, count(HolderEffect.StartSecond::class.java))
        assertEquals(1, count(HolderEffect.Finish::class.java))
    }

    @Test
    fun aVariantThatStopsOnTimeoutFinishesWithoutStartingTheSecondApp() {
        val m = machine(h1.copy(onTimeout = TimeoutAction.STOP))
        m.onResume().keep()
        m.tickAt(300)
        val out = m.tickAt(3_300)
        assertTrue(out.contains(HolderEffect.Finish))
        assertEquals(0, count(HolderEffect.StartSecond::class.java))
        assertEquals(HolderPhase.Done, m.phase)
        assertEquals("no split after first start; stopped", m.finalState)
    }

    @Test
    fun eachOfTheThreeSignalsAndThePollStartsTheSecondStartCountdown() {
        listOf("onMultiWindowModeChanged", "onConfigurationChanged", "poll on onResume", "poll on window focus")
            .forEach { source ->
                now = 0
                val m = machine()
                m.onResume()
                now = 300
                m.onTick()
                now = 500
                val out = m.onSplitSignal(true, source)
                assertEquals(source, HolderPhase.SplitSettling, m.phase)
                assertTrue(source, out.filterIsInstance<HolderEffect.Note>().any { it.text.contains(source) })
                assertEquals(source, 900L, m.wakeAt)
            }
    }

    @Test
    fun aFalseReadingChangesNothingAndASplitSeenBeforeTheFirstStartIsNotCounted() {
        val m = machine()
        m.onResume()
        m.onSplitSignal(true, "poll on onResume")
        assertEquals(HolderPhase.Settling, m.phase)
        now = 300
        m.onTick()
        now = 400
        m.onSplitSignal(false, "poll on window focus")
        assertEquals(HolderPhase.WaitingForSplit, m.phase)
        m.onSplitSignal(true, "poll after the first start")
        assertEquals(HolderPhase.SplitSettling, m.phase)
    }

    @Test
    fun aHolderPausedBeforeTheFirstStartEndsCleanlyAndNeverStarts() {
        val m = machine()
        now = 100
        m.onResume().keep()
        now = 200
        m.onLeft("onPause").keep()
        assertEquals(HolderPhase.Done, m.phase)
        assertNull(m.wakeAt)
        m.tickAt(10_000)
        assertEquals(0, count(HolderEffect.StartFirst::class.java))
        assertEquals(1, count(HolderEffect.Finish::class.java))
        assertEquals("onPause before the first start", m.finalState)
    }

    @Test
    fun aHolderPausedAfterTheFirstStartCarriesOn() {
        val m = machine()
        m.onResume()
        now = 300
        m.onTick()
        assertTrue(m.onLeft("onPause").isEmpty())
        assertEquals(HolderPhase.WaitingForSplit, m.phase)
    }

    @Test
    fun aHolderDestroyedMidWayStartsAndFinishesNothingMore() {
        val m = machine()
        m.onResume().keep()
        m.tickAt(300)
        m.onDestroy().keep()
        assertEquals(HolderPhase.Done, m.phase)
        m.onSplitSignal(true, "onConfigurationChanged").keep()
        m.tickAt(60_000)
        assertEquals(1, count(HolderEffect.StartFirst::class.java))
        assertEquals(0, count(HolderEffect.StartSecond::class.java))
        assertEquals(0, count(HolderEffect.Finish::class.java))
        assertTrue(notes().toString(), notes().any { it.startsWith("destroyed in state WaitingForSplit") })
        assertEquals("destroyed in state WaitingForSplit", m.finalState)
    }

    @Test
    fun noEffectIsEmittedTwiceHoweverOftenTheEventsRepeat() {
        val m = machine()
        repeat(3) {
            m.onResume().keep()
            m.tickAt(300)
        }
        repeat(3) {
            now = 400L + it
            m.onSplitSignal(true, "onConfigurationChanged").keep()
        }
        listOf(800L, 800L, 900L, 1_200L, 1_200L, 5_000L).forEach { m.tickAt(it) }
        m.onLeft("onPause").keep()
        m.onDestroy().keep()
        assertEquals(1, count(HolderEffect.StartFirst::class.java))
        assertEquals(1, count(HolderEffect.StartSecond::class.java))
        assertEquals(1, count(HolderEffect.Finish::class.java))
    }

    @Test
    fun theControlStartsItsSecondAppOnATimerWhateverTheSplitDoes() {
        val h6 = HeldVariants.all[5]
        val m = machine(h6)
        m.onResume().keep()
        m.tickAt(300)
        assertEquals(900L, m.wakeAt)
        now = 400
        m.onSplitSignal(true, "onMultiWindowModeChanged").keep()
        assertEquals(HolderPhase.WaitingForSplit, m.phase)
        assertTrue(m.tickAt(899).none { it !is HolderEffect.Note })
        assertTrue(m.tickAt(900).contains(HolderEffect.StartSecond(h6.secondFlags)))
        assertEquals(1_200L, m.wakeAt)
        assertTrue(m.tickAt(1_200).contains(HolderEffect.Finish))
    }

    @Test
    fun aFinishDelayOfZeroFinishesInTheSameTickAsTheSecondStart() {
        val m = machine(h1.copy(finishDelayMillis = 0))
        m.onResume().keep()
        m.tickAt(300)
        now = 350
        m.onSplitSignal(true, "poll on onResume").keep()
        val out = m.tickAt(750)
        assertEquals(
            listOf(HolderEffect.StartSecond(h1.secondFlags), HolderEffect.Finish),
            out.filterNot { it is HolderEffect.Note },
        )
        assertEquals(HolderPhase.Done, m.phase)
    }
}
