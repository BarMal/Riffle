package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.GestureSettings
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeSwipeGestureInterpreterTest {
    private val interpreter = HomeSwipeGestureInterpreter(thresholdPx = 80f)
    private val actionMapper = HomeSwipeGestureActionMapper()

    @Test
    fun interpretsSwipeUpPastThreshold() {
        assertEquals(HomeGesture.ONE_FINGER_UP, interpreter.gestureFor(horizontalDragPx = 0f, verticalDragPx = -80f))
    }

    @Test
    fun interpretsSwipeDownPastThreshold() {
        assertEquals(HomeGesture.ONE_FINGER_DOWN, interpreter.gestureFor(horizontalDragPx = 0f, verticalDragPx = 80f))
    }

    @Test
    fun interpretsSwipeLeftPastThreshold() {
        assertEquals(HomeGesture.ONE_FINGER_LEFT, interpreter.gestureFor(horizontalDragPx = -80f, verticalDragPx = 0f))
    }

    @Test
    fun interpretsSwipeRightPastThreshold() {
        assertEquals(HomeGesture.ONE_FINGER_RIGHT, interpreter.gestureFor(horizontalDragPx = 80f, verticalDragPx = 0f))
    }

    @Test
    fun interpretsTwoFingerSwipes() {
        assertEquals(
            HomeGesture.TWO_FINGER_UP,
            interpreter.gestureFor(pointerCount = 2, horizontalDragPx = 0f, verticalDragPx = -100f),
        )
        assertEquals(
            HomeGesture.TWO_FINGER_RIGHT,
            interpreter.gestureFor(pointerCount = 2, horizontalDragPx = 100f, verticalDragPx = 0f),
        )
    }

    @Test
    fun interpretsThreeFingerSwipes() {
        assertEquals(
            HomeGesture.THREE_FINGER_UP,
            interpreter.gestureFor(pointerCount = 3, horizontalDragPx = 0f, verticalDragPx = -100f),
        )
        assertEquals(
            HomeGesture.THREE_FINGER_DOWN,
            interpreter.gestureFor(pointerCount = 3, horizontalDragPx = 0f, verticalDragPx = 100f),
        )
        assertEquals(
            HomeGesture.THREE_FINGER_LEFT,
            interpreter.gestureFor(pointerCount = 3, horizontalDragPx = -100f, verticalDragPx = 0f),
        )
        assertEquals(
            HomeGesture.THREE_FINGER_RIGHT,
            interpreter.gestureFor(pointerCount = 3, horizontalDragPx = 100f, verticalDragPx = 0f),
        )
    }

    @Test
    fun interpretsPinchesBeforeTwoFingerSwipes() {
        assertEquals(
            HomeGesture.PINCH_IN,
            interpreter.gestureFor(
                pointerCount = 2,
                horizontalDragPx = 120f,
                verticalDragPx = 0f,
                scaleDelta = -0.2f,
            ),
        )
        assertEquals(
            HomeGesture.PINCH_OUT,
            interpreter.gestureFor(
                pointerCount = 2,
                horizontalDragPx = 0f,
                verticalDragPx = -120f,
                scaleDelta = 0.2f,
            ),
        )
    }

    @Test
    fun usesDominantDragAxis() {
        assertEquals(
            HomeGesture.ONE_FINGER_LEFT,
            interpreter.gestureFor(horizontalDragPx = -120f, verticalDragPx = 90f),
        )
        assertEquals(
            HomeGesture.ONE_FINGER_DOWN,
            interpreter.gestureFor(horizontalDragPx = -90f, verticalDragPx = 120f),
        )
    }

    @Test
    fun ignoresAmbiguousDiagonalDrags() {
        assertNull(interpreter.gestureFor(horizontalDragPx = 100f, verticalDragPx = 95f))
        assertNull(interpreter.gestureFor(horizontalDragPx = -95f, verticalDragPx = -100f))
    }

    @Test
    fun ignoresDragBelowThreshold() {
        assertNull(interpreter.gestureFor(horizontalDragPx = 0f, verticalDragPx = 79f))
        assertNull(interpreter.gestureFor(horizontalDragPx = 0f, verticalDragPx = -79f))
        assertNull(interpreter.gestureFor(horizontalDragPx = 79f, verticalDragPx = 0f))
        assertNull(interpreter.gestureFor(horizontalDragPx = -79f, verticalDragPx = 0f))
    }

    @Test
    fun mapsSwipeGesturesToDefaultHomeActions() {
        assertEquals(LauncherShellAction.OpenAppDrawer, actionMapper.actionFor(HomeGesture.ONE_FINGER_UP))
        assertEquals(LauncherShellAction.OpenNotifications, actionMapper.actionFor(HomeGesture.ONE_FINGER_DOWN))
        assertEquals(LauncherShellAction.SelectNextHomePage, actionMapper.actionFor(HomeGesture.ONE_FINGER_LEFT))
        assertEquals(LauncherShellAction.SelectPreviousHomePage, actionMapper.actionFor(HomeGesture.ONE_FINGER_RIGHT))
        assertEquals(LauncherShellAction.OpenSearch, actionMapper.actionFor(HomeGesture.TWO_FINGER_UP))
        assertEquals(LauncherShellAction.OpenSettings, actionMapper.actionFor(HomeGesture.TWO_FINGER_DOWN))
        assertEquals(LauncherShellAction.EnterHomeEditMode, actionMapper.actionFor(HomeGesture.PINCH_IN))
    }

    @Test
    fun mapsSwipeGesturesToConfiguredActions() {
        val settings =
            HomeGestureSettings(
                actions =
                    mapOf(
                        HomeGesture.ONE_FINGER_UP to LauncherGestureAction.OPEN_SEARCH,
                        HomeGesture.ONE_FINGER_DOWN to LauncherGestureAction.OPEN_SETTINGS,
                        HomeGesture.ONE_FINGER_LEFT to LauncherGestureAction.ENTER_HOME_EDIT_MODE,
                        HomeGesture.ONE_FINGER_RIGHT to LauncherGestureAction.ENTER_HOME_PAGE_OVERVIEW,
                        HomeGesture.PINCH_OUT to LauncherGestureAction.OPEN_NOTIFICATIONS,
                        HomeGesture.TWO_FINGER_RIGHT to LauncherGestureAction.ENTER_FULLSCREEN_HOME,
                        HomeGesture.THREE_FINGER_LEFT to LauncherGestureAction.OPEN_APP_DRAWER,
                    ),
            )

        assertEquals(LauncherShellAction.OpenSearch, actionMapper.actionFor(HomeGesture.ONE_FINGER_UP, settings))
        assertEquals(LauncherShellAction.OpenSettings, actionMapper.actionFor(HomeGesture.ONE_FINGER_DOWN, settings))
        assertEquals(
            LauncherShellAction.EnterHomeEditMode,
            actionMapper.actionFor(HomeGesture.ONE_FINGER_LEFT, settings),
        )
        assertEquals(
            LauncherShellAction.EnterHomePageOverview,
            actionMapper.actionFor(HomeGesture.ONE_FINGER_RIGHT, settings),
        )
        assertEquals(LauncherShellAction.OpenNotifications, actionMapper.actionFor(HomeGesture.PINCH_OUT, settings))
        assertEquals(
            LauncherShellAction.SelectFullscreenHomeEnabled(true),
            actionMapper.actionFor(HomeGesture.TWO_FINGER_RIGHT, settings),
        )
        assertEquals(
            LauncherShellAction.OpenAppDrawer,
            actionMapper.actionFor(HomeGesture.THREE_FINGER_LEFT, settings),
        )
    }

    @Test
    fun mapsDisabledGestureToNoAction() {
        val settings = HomeGestureSettings(actions = mapOf(HomeGesture.ONE_FINGER_UP to LauncherGestureAction.NONE))

        assertNull(actionMapper.actionFor(HomeGesture.ONE_FINGER_UP, settings))
    }

    @Test
    fun labelsPageOverviewGestureActionForSettings() {
        assertEquals("Manage pages", LauncherGestureAction.ENTER_HOME_PAGE_OVERVIEW.label)
    }

    @Test
    fun labelsFullscreenGestureActionForSettings() {
        assertEquals("Fullscreen home", LauncherGestureAction.ENTER_FULLSCREEN_HOME.label)
    }

    @Test
    fun summarizesHomeGestureConflictsForSettings() {
        val settings =
            HomeGestureSettings(
                actions =
                    mapOf(
                        HomeGesture.ONE_FINGER_UP to LauncherGestureAction.OPEN_SEARCH,
                        HomeGesture.PINCH_OUT to LauncherGestureAction.OPEN_SEARCH,
                    ),
            )

        assertEquals(
            "Conflicting gestures: Search: Swipe up, Two-finger swipe up, Pinch out",
            homeGestureConflictSummary(GestureSettings(homeGestures = settings)),
        )
    }

    @Test
    fun omitsHomeGestureConflictSummaryWhenActionsAreUnique() {
        val settings =
            HomeGestureSettings(
                actions =
                    mapOf(
                        HomeGesture.ONE_FINGER_UP to LauncherGestureAction.OPEN_APP_DRAWER,
                        HomeGesture.TWO_FINGER_UP to LauncherGestureAction.NONE,
                        HomeGesture.PINCH_OUT to LauncherGestureAction.OPEN_SEARCH,
                    ),
            )

        assertNull(homeGestureConflictSummary(GestureSettings(homeGestures = settings)))
    }

    @Test
    fun mapsConfiguredSwipeUpDragToAppDrawerAction() {
        val settings =
            HomeGestureSettings(actions = mapOf(HomeGesture.ONE_FINGER_UP to LauncherGestureAction.OPEN_APP_DRAWER))

        assertEquals(
            LauncherShellAction.OpenAppDrawer,
            homeSwipeActionForDrag(
                horizontalDragPx = 0f,
                verticalDragPx = -120f,
                settings = settings,
                interpreter = interpreter,
                actionMapper = actionMapper,
            ),
        )
    }

    @Test
    fun dominantHorizontalDragIsNotAVerticalHomeSwipe() {
        val settings = HomeGestureSettings()

        assertEquals(
            LauncherShellAction.SelectNextHomePage,
            homeSwipeActionForDrag(
                horizontalDragPx = -120f,
                verticalDragPx = -90f,
                settings = settings,
                interpreter = interpreter,
                actionMapper = actionMapper,
            ),
        )
    }

    @Test
    fun mapsPinchDragToConfiguredAction() {
        val settings = HomeGestureSettings(actions = mapOf(HomeGesture.PINCH_OUT to LauncherGestureAction.OPEN_SEARCH))

        assertEquals(
            LauncherShellAction.OpenSearch,
            homeSwipeActionForDrag(
                pointerCount = 2,
                horizontalDragPx = 0f,
                verticalDragPx = 0f,
                scaleDelta = 0.22f,
                settings = settings,
                interpreter = interpreter,
                actionMapper = actionMapper,
            ),
        )
    }
}
