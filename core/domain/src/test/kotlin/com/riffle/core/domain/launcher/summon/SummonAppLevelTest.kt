package com.riffle.core.domain.launcher.summon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Pushing through an app with quick actions opens them as a level of the ring. */
class SummonAppLevelTest {
    private fun config(keep: SummonKeepGesture = SummonKeepGesture.PUSH_THROUGH) =
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

    // Top: an app with two actions; lower right: an app with a card and actions; lower left: an app with none.
    private val roles: (List<Int>) -> List<SummonSlotRole> = { path ->
        when (path) {
            emptyList<Int>() -> listOf(SummonSlotRole.APP, SummonSlotRole.PREVIEW, SummonSlotRole.APP)
            listOf(0), listOf(1) -> listOf(SummonSlotRole.APP, SummonSlotRole.APP)
            else -> emptyList()
        }
    }
    private val centre = RingPoint(540f, 1100f)

    private fun at(
        degrees: Float,
        distance: Float,
    ): RingPoint {
        val a = Math.toRadians(degrees.toDouble())
        return RingPoint(
            centre.x + (distance * kotlin.math.cos(a)).toFloat(),
            centre.y + (distance * kotlin.math.sin(a)).toFloat(),
        )
    }

    private fun run(
        keep: SummonKeepGesture,
        vararg events: SummonEvent,
    ): Pair<SummonPhase, List<SummonEffect>> {
        var phase: SummonPhase = SummonPhase.Idle
        val effects = mutableListOf<SummonEffect>()
        (listOf(SummonEvent.Down(centre, 0), SummonEvent.Tick(200)) + events).forEach {
            val step = SummonGesture.reduce(phase, it, config(keep), roles)
            phase = step.phase
            effects += step.effects
        }
        return phase to effects
    }

    @Test
    fun pushingThroughAnAppWithActionsOpensThemAndLettingGoRunsOne() {
        val (phase, effects) = run(SummonKeepGesture.PUSH_THROUGH, SummonEvent.Move(at(-90f, 500f), 210))
        assertEquals(listOf(0), assertIs<SummonPhase.Open>(phase).path)
        assertTrue(SummonEffect.OpenedFolder(listOf(0)) in effects)
    }

    @Test
    fun anAppWithNoActionsJustOpensWhenLetGo() {
        val (phase, effects) =
            run(SummonKeepGesture.PUSH_THROUGH, SummonEvent.Move(at(150f, 500f), 210), SummonEvent.Up(300))
        assertEquals(SummonPhase.Idle, phase)
        assertEquals(SummonEffect.Launch(emptyList(), 2), effects.last())
    }

    @Test
    fun aCardAppKeepsItsCardWhenPushThroughKeepsCards() {
        val (phase, _) = run(SummonKeepGesture.PUSH_THROUGH, SummonEvent.Move(at(30f, 500f), 210))
        val open = assertIs<SummonPhase.Open>(phase)
        assertEquals(emptyList(), open.path)
        assertTrue(open.live)
    }

    @Test
    fun aCardAppOpensItsActionsWhenCardsAreKeptByResting() {
        val (phase, _) = run(SummonKeepGesture.REST_TO_LOCK, SummonEvent.Move(at(30f, 500f), 210))
        assertEquals(listOf(1), assertIs<SummonPhase.Open>(phase).path)
    }
}
