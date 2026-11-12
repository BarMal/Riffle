package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.LauncherShellStateReducer
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.ShellNavigationAction
import com.riffle.core.domain.launcher.settings.GestureSettings
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** The Stream's entry points in this slice: the OPEN_STREAM gesture action, navigation to it and back to Home. */
class StreamGestureTest {
    private val bound =
        HomeGestureSettings(actions = mapOf(HomeGesture.THREE_FINGER_UP to LauncherGestureAction.OPEN_STREAM))

    @Test
    fun aBoundGestureOpensTheStream() {
        assertEquals(
            LauncherShellAction.OpenStream,
            HomeSwipeGestureActionMapper().actionFor(HomeGesture.THREE_FINGER_UP, bound),
        )
    }

    @Test
    fun noGestureIsBoundToTheStreamByDefault() {
        assertFalse(LauncherGestureAction.OPEN_STREAM in HomeGestureSettings().actions.values)
    }

    @Test
    fun theStoredNameRoundTripsAndAnUnknownNameIsNoAction() {
        assertEquals(LauncherGestureAction.OPEN_STREAM, LauncherGestureAction.fromStoredName("OPEN_STREAM"))
        assertNull(LauncherGestureAction.fromStoredName("OPEN_HOLOGRAM"))

        val decoded = encodeGestures(GestureSettings(homeGestures = bound)).toGestures(GestureSettings())
        assertEquals(LauncherGestureAction.OPEN_STREAM, decoded.homeGestures.actionFor(HomeGesture.THREE_FINGER_UP))
    }

    @Test
    fun theGestureIsOfferedWhereGesturesAreChosen() {
        assertEquals(true, LauncherGestureAction.OPEN_STREAM in gestureActionOptions())
        assertEquals("Stream", LauncherGestureAction.OPEN_STREAM.label)
    }

    @Test
    fun openingTheStreamIsANavigationAndHomeComesBack() {
        val reducer = LauncherShellStateReducer()
        val opened = reducer.navigationActionSelected(LauncherShellState(), ShellNavigationAction.OpenStream)
        assertEquals(ShellDestination.STREAM, opened.destination)

        val home = reducer.navigationActionSelected(opened, ShellNavigationAction.OpenHome)
        assertEquals(ShellDestination.HOME, home.destination)
    }
}
