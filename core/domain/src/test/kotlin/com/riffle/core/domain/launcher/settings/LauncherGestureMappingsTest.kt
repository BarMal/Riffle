package com.riffle.core.domain.launcher.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LauncherGestureMappingsTest {
    @Test
    fun resolvesActionsPerSurface() {
        val mappings =
            LauncherGestureMappings()
                .withAction(
                    surface = LauncherGestureSurface.HOME_PAGE,
                    gesture = LauncherGesture.ONE_FINGER_UP,
                    action = LauncherGestureAction.OPEN_APP_DRAWER,
                ).withAction(
                    surface = LauncherGestureSurface.DOCK,
                    gesture = LauncherGesture.ONE_FINGER_UP,
                    action = LauncherGestureAction.OPEN_SEARCH,
                )

        assertEquals(
            LauncherGestureAction.OPEN_APP_DRAWER,
            mappings.actionFor(LauncherGestureSurface.HOME_PAGE, LauncherGesture.ONE_FINGER_UP),
        )
        assertEquals(
            LauncherGestureAction.OPEN_SEARCH,
            mappings.actionFor(LauncherGestureSurface.DOCK, LauncherGesture.ONE_FINGER_UP),
        )
        assertEquals(
            LauncherGestureAction.NONE,
            mappings.actionFor(LauncherGestureSurface.CARD, LauncherGesture.ONE_FINGER_UP),
        )
    }

    @Test
    fun conflictsStayWithinOneSurface() {
        val mappings =
            LauncherGestureMappings()
                .withAction(
                    surface = LauncherGestureSurface.HOME_PAGE,
                    gesture = LauncherGesture.ONE_FINGER_UP,
                    action = LauncherGestureAction.OPEN_SEARCH,
                ).withAction(
                    surface = LauncherGestureSurface.HOME_PAGE,
                    gesture = LauncherGesture.TWO_FINGER_UP,
                    action = LauncherGestureAction.OPEN_SEARCH,
                ).withAction(
                    surface = LauncherGestureSurface.DOCK,
                    gesture = LauncherGesture.ONE_FINGER_UP,
                    action = LauncherGestureAction.OPEN_SEARCH,
                )

        assertEquals(
            listOf(
                LauncherGestureConflict(
                    surface = LauncherGestureSurface.HOME_PAGE,
                    action = LauncherGestureAction.OPEN_SEARCH,
                    gestures = listOf(LauncherGesture.ONE_FINGER_UP, LauncherGesture.TWO_FINGER_UP),
                ),
            ),
            LauncherGestureConflictDetector.conflictsIn(mappings),
        )
    }

    @Test
    fun homeGestureSettingsExportToSharedMappings() {
        val mappings =
            HomeGestureSettings(
                actions =
                    mapOf(
                        HomeGesture.ONE_FINGER_UP to LauncherGestureAction.OPEN_SEARCH,
                        HomeGesture.PINCH_OUT to LauncherGestureAction.OPEN_SEARCH,
                    ),
            ).toLauncherGestureMappings()

        assertEquals(
            LauncherGestureAction.OPEN_SEARCH,
            mappings.actionFor(LauncherGestureSurface.HOME_PAGE, LauncherGesture.ONE_FINGER_UP),
        )
        assertEquals(
            LauncherGestureAction.OPEN_SEARCH,
            mappings.actionFor(LauncherGestureSurface.HOME_PAGE, LauncherGesture.PINCH_OUT),
        )
        assertTrue(
            LauncherGestureConflictDetector.conflictsIn(mappings).isNotEmpty(),
        )
    }
}
