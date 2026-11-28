package com.riffle.core.domain.launcher.summon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Keeping a card with [SummonKeepGesture.REST_TO_LOCK]: rest to lock the aim, slide onto the readout, let go. */
class SummonKeepGestureTest {
    private fun config(keep: SummonKeepGesture) =
        SummonGestureConfig(
            timings = SummonTimings(),
            bounds = RingBounds(40f, 100f, 1040f, 2200f),
            radius = 300f,
            clearance = 90f,
            deadZone = 80f,
            pushThrough = 130f,
            touchSlop = 24f,
            keep = keep,
        )

    // Eight slots clockwise from the top; 1 (up right) and 6 (left) have cards.
    private val root =
        listOf(
            SummonSlotRole.APP,
            SummonSlotRole.PREVIEW,
            SummonSlotRole.APP,
            SummonSlotRole.APP,
            SummonSlotRole.FAVOURITES,
            SummonSlotRole.FOLDER,
            SummonSlotRole.PREVIEW,
            SummonSlotRole.APP,
        )
    private val roles: (List<Int>) -> List<SummonSlotRole> = { root }
    private val centre = RingPoint(540f, 1100f)
    private val readout = RingPoint(540f, 300f)

    private fun at(
        degrees: Float,
        distance: Float = 200f,
    ): RingPoint {
        val a = Math.toRadians(degrees.toDouble())
        return RingPoint(
            centre.x + (distance * kotlin.math.cos(a)).toFloat(),
            centre.y + (distance * kotlin.math.sin(a)).toFloat(),
        )
    }

    private class Run(
        val config: SummonGestureConfig,
        val roles: (List<Int>) -> List<SummonSlotRole>,
    ) {
        var phase: SummonPhase = SummonPhase.Idle
        val effects = mutableListOf<SummonEffect>()

        fun send(event: SummonEvent): Run {
            val step = SummonGesture.reduce(phase, event, config, roles)
            phase = step.phase
            effects += step.effects
            return this
        }

        val open: SummonPhase.Open get() = assertIs<SummonPhase.Open>(phase)
    }

    /** Armed, aimed at the left card slot at 210 ms and rested until its preview (and lock) at 560 ms. */
    private fun lockedOnTheLeftCard(keep: SummonKeepGesture = SummonKeepGesture.REST_TO_LOCK): Run =
        Run(config(keep), roles)
            .send(SummonEvent.Down(centre, 0))
            .send(SummonEvent.Tick(200))
            .send(SummonEvent.Move(at(180f), 210))
            .send(SummonEvent.Tick(560))

    @Test
    fun crossingTheCentreAndOtherSlotsOnTheWayToTheReadoutKeepsTheLockedCard() {
        val run = lockedOnTheLeftCard()
        assertTrue(run.open.previewing)
        run
            .send(SummonEvent.Move(at(0f, 10f), 600))
            .send(SummonEvent.Move(at(-90f), 620))
        assertEquals(6, run.open.aim)
        run.send(SummonEvent.Move(readout, 640, overReadout = true))
        assertEquals(6, run.open.aim)
        assertTrue(run.open.overReadout)
        assertTrue(SummonEffect.ReachedReadout(emptyList(), 6) in run.effects)
        run.send(SummonEvent.Up(700))
        assertEquals(SummonEffect.KeepCard(emptyList(), 6), run.effects.last())
    }

    @Test
    fun lettingGoAwayFromTheReadoutStillOpensTheLockedSlot() {
        val run = lockedOnTheLeftCard().send(SummonEvent.Move(at(-90f), 600)).send(SummonEvent.Up(650))
        assertEquals(SummonEffect.Launch(emptyList(), 6), run.effects.last())
    }

    @Test
    fun restingOnAnotherSlotMovesTheLockToIt() {
        val config = config(SummonKeepGesture.REST_TO_LOCK)
        val run = lockedOnTheLeftCard().send(SummonEvent.Move(at(-45f), 600))
        assertEquals(6, run.open.aim)
        assertEquals(950L, SummonGesture.nextDeadline(run.phase, config, roles))
        run.send(SummonEvent.Tick(950))
        assertEquals(1, run.open.aim)
        assertFalse(run.open.previewing)
        assertEquals(SummonEffect.Aimed(emptyList(), 1), run.effects.last())
        run.send(SummonEvent.Tick(1300))
        assertTrue(run.open.previewing)
    }

    @Test
    fun comingBackToTheLockedSlotStopsTheSwitch() {
        val config = config(SummonKeepGesture.REST_TO_LOCK)
        val run =
            lockedOnTheLeftCard()
                .send(SummonEvent.Move(at(-45f), 600))
                .send(SummonEvent.Move(at(180f), 700))
        assertNull(SummonGesture.nextDeadline(run.phase, config, roles))
        run.send(SummonEvent.Tick(1100))
        assertEquals(6, run.open.aim)
    }

    @Test
    fun restingInTheCentreUnlocks() {
        val run = lockedOnTheLeftCard().send(SummonEvent.Move(at(0f, 10f), 600)).send(SummonEvent.Tick(950))
        assertNull(run.open.aim)
        assertFalse(run.open.previewing)
    }

    @Test
    fun pushingThroughNoLongerKeepsACardWhenKeepingIsByResting() {
        val run =
            Run(config(SummonKeepGesture.REST_TO_LOCK), roles)
                .send(SummonEvent.Down(centre, 0))
                .send(SummonEvent.Tick(200))
                .send(SummonEvent.Move(at(180f, 500f), 210))
        assertFalse(run.open.live)
        run.send(SummonEvent.Up(260))
        assertEquals(SummonEffect.Launch(emptyList(), 6), run.effects.last())
    }

    @Test
    fun whenKeepingIsByPushingThroughTheReadoutIsJustAPlace() {
        val run = lockedOnTheLeftCard(SummonKeepGesture.PUSH_THROUGH)
        run.send(SummonEvent.Move(at(-90f), 600, overReadout = true))
        assertEquals(0, run.open.aim)
        assertFalse(run.open.overReadout)
        run.send(SummonEvent.Up(650))
        assertEquals(SummonEffect.Launch(emptyList(), 0), run.effects.last())
    }
}
