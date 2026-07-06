package com.riffle.core.domain.launcher.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeGestureConflictDetectorTest {
    @Test
    fun returnsNoConflictWhenEnabledActionsAreUnique() {
        val settings =
            HomeGestureSettings(
                actions =
                    mapOf(
                        HomeGesture.ONE_FINGER_UP to LauncherGestureAction.OPEN_APP_DRAWER,
                        HomeGesture.ONE_FINGER_DOWN to LauncherGestureAction.OPEN_NOTIFICATIONS,
                        HomeGesture.TWO_FINGER_UP to LauncherGestureAction.OPEN_SEARCH,
                    ),
            )

        assertTrue(HomeGestureConflictDetector.conflictsIn(settings).isEmpty())
    }

    @Test
    fun returnsNoConflictForMultipleNoneMappings() {
        val settings =
            HomeGestureSettings(
                actions =
                    mapOf(
                        HomeGesture.ONE_FINGER_UP to LauncherGestureAction.NONE,
                        HomeGesture.ONE_FINGER_DOWN to LauncherGestureAction.NONE,
                    ),
            )

        assertTrue(HomeGestureConflictDetector.conflictsIn(settings).isEmpty())
    }

    @Test
    fun returnsConflictWhenTwoGesturesMapToSameEnabledAction() {
        val settings =
            HomeGestureSettings(
                actions =
                    mapOf(
                        HomeGesture.ONE_FINGER_UP to LauncherGestureAction.OPEN_SEARCH,
                        HomeGesture.TWO_FINGER_UP to LauncherGestureAction.OPEN_SEARCH,
                    ),
            )

        assertEquals(
            listOf(
                HomeGestureConflict(
                    action = LauncherGestureAction.OPEN_SEARCH,
                    gestures = listOf(HomeGesture.ONE_FINGER_UP, HomeGesture.TWO_FINGER_UP),
                ),
            ),
            HomeGestureConflictDetector.conflictsIn(settings),
        )
    }

    @Test
    fun defaultSettingsReportCurrentOpenAppDrawerConflict() {
        val conflict =
            HomeGestureConflictDetector.conflictsIn(HomeGestureSettings()).single {
                it.action == LauncherGestureAction.OPEN_APP_DRAWER
            }

        assertEquals(
            listOf(HomeGesture.ONE_FINGER_UP, HomeGesture.PINCH_OUT),
            conflict.gestures,
        )
    }
}
