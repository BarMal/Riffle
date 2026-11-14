package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.dock.DockChoiceStart
import com.riffle.core.domain.launcher.dock.DockMotionConstants
import com.riffle.core.domain.launcher.dock.DockMotionFrame
import com.riffle.core.domain.launcher.dock.DockMotionTuning
import com.riffle.core.domain.launcher.dock.DockRelease
import com.riffle.core.domain.launcher.dock.DockSpring
import com.riffle.core.domain.launcher.dock.DockSpringState
import com.riffle.core.domain.launcher.stream.StackFling
import com.riffle.core.domain.launcher.stream.StackSpring
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DockMotionSettingsTest {
    @Test
    fun theDefaultsAreTheConstantsTheMotionRanWithBeforeThereWasASetting() {
        val defaults = DockMotionSettings()

        assertEquals(DockMotionConstants.DEFAULT_SPEED_MILLIS, defaults.speedMillis)
        assertEquals(DockMotionConstants.DEFAULT_HOLD_MILLIS, defaults.holdMillis)
        assertEquals(DockMotionTuning(), defaults.tuning())
        assertEquals(DockMotionConstants.DEFAULT_TRAMPOLINE, defaults.tuning().trampoline)
        assertEquals(DockMotionConstants.DEFAULT_MAGNET, defaults.tuning().magnet)
        assertEquals(DockMotionConstants.DEFAULT_STACK_INERTIA, defaults.tuning().inertia)
        assertTrue(defaults.ticks)
        assertEquals(defaults, defaults.coerced())
    }

    @Test
    fun valuesOutOfRangeAreHeldToTheirRangesNeverRejected() {
        val wild =
            DockMotionSettings(
                speedMillis = -10,
                trampolinePercent = 400,
                inertiaPercent = -1,
                magnetPercent = 101,
                holdMillis = 99_999,
            ).coerced()

        assertEquals(DockMotionSettings.MIN_SPEED_MILLIS, wild.speedMillis)
        assertEquals(100, wild.trampolinePercent)
        assertEquals(0, wild.inertiaPercent)
        assertEquals(100, wild.magnetPercent)
        assertEquals(DockMotionSettings.MAX_HOLD_MILLIS, wild.holdMillis)
        // The tuning built from unclamped values is the clamped one.
        assertEquals(wild.tuning(), DockMotionSettings(-10, 400, -1, 101, 99_999).tuning())
    }

    @Test
    fun everyAdjustableValueReachesTheTuningTheMotionReads() {
        val tuning =
            DockMotionSettings(
                speedMillis = 300,
                trampolinePercent = 80,
                inertiaPercent = 150,
                magnetPercent = 20,
                holdMillis = 400,
            ).tuning()

        assertEquals(300, tuning.speedMillis)
        assertEquals(0.8f, tuning.trampoline)
        assertEquals(1.5f, tuning.inertia)
        assertEquals(0.2f, tuning.magnet)
        assertEquals(400, tuning.holdMillis)
    }

    private fun framesToSettle(tuning: DockMotionTuning): Int {
        var state = DockRelease.opening(DockMotionFrame(0.1f, 9f))
        var frames = 0
        while (!DockSpring.isSettled(state, 1f) && frames < MAX_FRAMES) {
            state = DockSpring.advance(state, 1f, FRAME, tuning)
            frames++
        }
        return frames
    }

    private fun bounce(tuning: DockMotionTuning): Float {
        var state: DockSpringState = DockRelease.opening(DockMotionFrame(0.1f, 9f))
        var highest = 0f
        var frames = 0
        while (!DockSpring.isSettled(state, 1f) && frames++ < MAX_FRAMES) {
            state = DockSpring.advance(state, 1f, FRAME, tuning)
            highest = maxOf(highest, -state.dip)
        }
        return highest
    }

    @Test
    fun theOpeningSpeedSettingSetsHowLongTheOpeningTakes() {
        val fast = DockMotionSettings(speedMillis = 100).tuning()
        val slow = DockMotionSettings(speedMillis = 400).tuning()

        assertTrue(framesToSettle(slow) > framesToSettle(DockMotionSettings().tuning()))
        assertTrue(framesToSettle(DockMotionSettings().tuning()) > framesToSettle(fast))
    }

    @Test
    fun theTrampolineSettingSetsTheBounceAndZeroIsADeadDock() {
        assertTrue(bounce(DockMotionSettings(trampolinePercent = 0).tuning()) < 0.05f)
        assertTrue(
            bounce(DockMotionSettings(trampolinePercent = 100).tuning()) >
                bounce(DockMotionSettings(trampolinePercent = 50).tuning()),
        )
    }

    @Test
    fun theInertiaSettingSetsHowFarAFlungStackCoasts() {
        val velocity = 6f
        val none = DockMotionSettings(inertiaPercent = 0).tuning()
        val normal = DockMotionSettings().tuning()
        val most = DockMotionSettings(inertiaPercent = DockMotionSettings.MAX_INERTIA_PERCENT).tuning()

        assertEquals(0, StackFling.target(0f, velocity, count = 10, inertia = none.stackInertia))
        assertEquals(1, StackFling.target(0f, velocity, count = 10, inertia = normal.stackInertia))
        assertTrue(StackFling.target(0f, velocity, count = 10, inertia = most.stackInertia) >= 2)
    }

    @Test
    fun theMagnetSettingSetsHowHardTheStackIsDrawnOntoACard() {
        val off = DockMotionSettings(magnetPercent = 0).tuning()
        val strong = DockMotionSettings(magnetPercent = 100).tuning()

        assertEquals(1f, StackSpring.pullAt(0f, off))
        assertTrue(StackSpring.pullAt(0.1f, strong) > StackSpring.pullAt(0.1f, DockMotionSettings().tuning()))
        // Whatever the magnet, the progress spring never overshoots.
        listOf(off, strong).forEach { tuning ->
            var state = DockRelease.opening(DockMotionFrame(0.1f, 9f))
            repeat(MAX_FRAMES) { state = DockSpring.advance(state, 1f, FRAME, tuning) }
            assertTrue(state.p <= 1f)
        }
    }

    @Test
    fun theHoldTimeSettingSetsWhenAStillPressBecomesChoosing() {
        val resting = DockChoiceStart().next(0, travelDp = PAST, alongDp = 0f)
        val quick = DockMotionSettings(holdMillis = 100).tuning().holdMillis.toLong()
        val slow = DockMotionSettings(holdMillis = 600).tuning().holdMillis.toLong()

        assertTrue(resting.next(quick, PAST, 0f, holdMillis = quick).choosing)
        assertFalse(resting.next(quick, PAST, 0f, holdMillis = slow).choosing)
        assertTrue(resting.next(slow, PAST, 0f, holdMillis = slow).choosing)
    }

    private companion object {
        val PAST: Float = com.riffle.core.domain.launcher.dock.DockPress.COMMIT_DP + 4f
        const val FRAME = 1f / 60f
        const val MAX_FRAMES = 600
    }
}
