package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/** The light-ticks switch silences detents and boundaries only; every other haptic still plays. */
class LauncherHapticsTicksTest {
    private class Recording : LauncherHaptics {
        val events = mutableListOf<LauncherHapticEvent>()
        var longPresses = 0

        override fun longPress() {
            longPresses++
        }

        override fun perform(event: LauncherHapticEvent) {
            events += event
        }
    }

    @Test
    fun ticksOnPassesEverythingThrough() {
        val recording = Recording()
        val haptics = recording.withTicks(true)

        LauncherHapticEvent.entries.forEach(haptics::perform)

        assertEquals(LauncherHapticEvent.entries, recording.events)
    }

    @Test
    fun ticksOffDropsDetentsAndBoundariesAndKeepsTheRest() {
        val recording = Recording()
        val haptics = recording.withTicks(false)

        LauncherHapticEvent.entries.forEach(haptics::perform)
        haptics.longPress()

        assertEquals(
            LauncherHapticEvent.entries - LauncherHapticEvent.DETENT - LauncherHapticEvent.BOUNDARY,
            recording.events,
        )
        assertEquals(1, recording.longPresses)
    }
}
