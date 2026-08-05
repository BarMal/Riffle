package com.riffle.core.domain.launcher.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DockGestureSettingsTest {
    @Test
    fun defaultsToExitingAdaptiveStageOnSwipeUp() {
        assertEquals(LauncherGestureAction.EXIT_ADAPTIVE_STAGE, DockGestureSettings().swipeUp)
    }

    @Test
    fun onlyExposesTheThreeActionsTheDockPhysicallySupports() {
        assertEquals(
            setOf(
                LauncherGestureAction.NONE,
                LauncherGestureAction.EXIT_ADAPTIVE_STAGE,
                LauncherGestureAction.OPEN_APP_DRAWER,
            ),
            DockGestureSettings.ALLOWED_SWIPE_UP_ACTIONS,
        )
        assertTrue(LauncherGestureAction.OPEN_APP_DRAWER.isValidDockSwipeUpAction)
        assertFalse(LauncherGestureAction.OPEN_SEARCH.isValidDockSwipeUpAction)
    }

    @Test
    fun dockSwipeUpParticipatesInTheSharedGestureMappings() {
        val mappings = DockGestureSettings(swipeUp = LauncherGestureAction.OPEN_APP_DRAWER).toLauncherGestureMappings()

        assertEquals(
            LauncherGestureAction.OPEN_APP_DRAWER,
            mappings.actionFor(LauncherGestureSurface.DOCK, LauncherGesture.ONE_FINGER_UP),
        )
        assertEquals(
            LauncherGestureAction.NONE,
            mappings.actionFor(LauncherGestureSurface.HOME_PAGE, LauncherGesture.ONE_FINGER_UP),
        )
    }

    @Test
    fun gestureSettingsExposesDockConflictsSeparatelyFromHomePageConflicts() {
        val settings =
            GestureSettings(
                homeGestures =
                    HomeGestureSettings(
                        actions = mapOf(HomeGesture.ONE_FINGER_UP to LauncherGestureAction.OPEN_SEARCH),
                    ),
                dockGestures = DockGestureSettings(swipeUp = LauncherGestureAction.OPEN_APP_DRAWER),
            )

        // The Dock currently exposes a single gesture, so it can never conflict with itself; it
        // still flows through the shared surface-scoped detector alongside home-page conflicts.
        assertTrue(settings.conflicts.none { conflict -> conflict.surface == LauncherGestureSurface.DOCK })
    }
}
