package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeGestureConflictDetectorTest {
    @Test
    fun ignoresLaunchAppGesturesWithDifferentTargets() {
        val settings =
            HomeGestureSettings(
                actions =
                    mapOf(
                        HomeGesture.TWO_FINGER_LEFT to LauncherGestureAction.LAUNCH_APP,
                        HomeGesture.TWO_FINGER_RIGHT to LauncherGestureAction.LAUNCH_APP,
                    ),
                launchTargets =
                    mapOf(
                        HomeGesture.TWO_FINGER_LEFT to LauncherGestureLaunchTarget.App(appIdentity("mail")),
                        HomeGesture.TWO_FINGER_RIGHT to LauncherGestureLaunchTarget.App(appIdentity("calendar")),
                    ),
            )

        assertTrue(
            HomeGestureConflictDetector
                .conflictsIn(settings)
                .none { conflict -> conflict.action == LauncherGestureAction.LAUNCH_APP },
        )
    }

    @Test
    fun returnsNoConflictWhenEnabledActionsAreUnique() {
        val settings =
            HomeGestureSettings(
                actions =
                    mapOf(
                        HomeGesture.ONE_FINGER_UP to LauncherGestureAction.OPEN_APP_DRAWER,
                        HomeGesture.ONE_FINGER_DOWN to LauncherGestureAction.OPEN_NOTIFICATIONS,
                        HomeGesture.TWO_FINGER_UP to LauncherGestureAction.OPEN_SEARCH,
                        HomeGesture.PINCH_OUT to LauncherGestureAction.NONE,
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

    @Test
    fun partialSettingsStillIncludeDefaultConflictsForUnspecifiedGestures() {
        val conflict =
            HomeGestureConflictDetector.conflictsIn(
                HomeGestureSettings(
                    actions =
                        mapOf(
                            HomeGesture.TWO_FINGER_LEFT to LauncherGestureAction.NONE,
                        ),
                ),
            ).single {
                it.action == LauncherGestureAction.OPEN_APP_DRAWER
            }

        assertEquals(
            listOf(HomeGesture.ONE_FINGER_UP, HomeGesture.PINCH_OUT),
            conflict.gestures,
        )
    }

    private fun appIdentity(name: String): AppIdentity =
        AppIdentity(
            packageName = AppPackageName("com.riffle.$name"),
            activityName = AppActivityName("com.riffle.$name.MainActivity"),
        )
}
