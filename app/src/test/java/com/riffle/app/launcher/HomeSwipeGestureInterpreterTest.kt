package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.HomeSwipeGestureSettings
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeSwipeGestureInterpreterTest {
    private val interpreter = HomeSwipeGestureInterpreter(thresholdPx = 80f)
    private val actionMapper = HomeSwipeGestureActionMapper()

    @Test
    fun interpretsSwipeUpPastThreshold() {
        assertEquals(HomeSwipeGesture.UP, interpreter.gestureFor(horizontalDragPx = 0f, verticalDragPx = -80f))
    }

    @Test
    fun interpretsSwipeDownPastThreshold() {
        assertEquals(HomeSwipeGesture.DOWN, interpreter.gestureFor(horizontalDragPx = 0f, verticalDragPx = 80f))
    }

    @Test
    fun interpretsSwipeLeftPastThreshold() {
        assertEquals(HomeSwipeGesture.LEFT, interpreter.gestureFor(horizontalDragPx = -80f, verticalDragPx = 0f))
    }

    @Test
    fun interpretsSwipeRightPastThreshold() {
        assertEquals(HomeSwipeGesture.RIGHT, interpreter.gestureFor(horizontalDragPx = 80f, verticalDragPx = 0f))
    }

    @Test
    fun usesDominantDragAxis() {
        assertEquals(HomeSwipeGesture.LEFT, interpreter.gestureFor(horizontalDragPx = -120f, verticalDragPx = 90f))
        assertEquals(HomeSwipeGesture.DOWN, interpreter.gestureFor(horizontalDragPx = -90f, verticalDragPx = 120f))
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
        assertEquals(LauncherShellAction.OpenAppDrawer, actionMapper.actionFor(HomeSwipeGesture.UP))
        assertEquals(LauncherShellAction.OpenNotifications, actionMapper.actionFor(HomeSwipeGesture.DOWN))
        assertEquals(LauncherShellAction.SelectNextHomePage, actionMapper.actionFor(HomeSwipeGesture.LEFT))
        assertEquals(LauncherShellAction.SelectPreviousHomePage, actionMapper.actionFor(HomeSwipeGesture.RIGHT))
    }

    @Test
    fun mapsSwipeGesturesToConfiguredActions() {
        val settings =
            HomeSwipeGestureSettings(
                up = LauncherGestureAction.OPEN_SEARCH,
                down = LauncherGestureAction.OPEN_SETTINGS,
                left = LauncherGestureAction.ENTER_HOME_EDIT_MODE,
                right = LauncherGestureAction.OPEN_APP_DRAWER,
            )

        assertEquals(LauncherShellAction.OpenSearch, actionMapper.actionFor(HomeSwipeGesture.UP, settings))
        assertEquals(LauncherShellAction.OpenSettings, actionMapper.actionFor(HomeSwipeGesture.DOWN, settings))
        assertEquals(LauncherShellAction.EnterHomeEditMode, actionMapper.actionFor(HomeSwipeGesture.LEFT, settings))
        assertEquals(LauncherShellAction.OpenAppDrawer, actionMapper.actionFor(HomeSwipeGesture.RIGHT, settings))
    }

    @Test
    fun mapsDisabledGestureToNoAction() {
        val settings = HomeSwipeGestureSettings(up = LauncherGestureAction.NONE)

        assertNull(actionMapper.actionFor(HomeSwipeGesture.UP, settings))
    }

    @Test
    fun mapsConfiguredSwipeUpDragToAppDrawerAction() {
        val settings = HomeSwipeGestureSettings(up = LauncherGestureAction.OPEN_APP_DRAWER)

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
        val settings = HomeSwipeGestureSettings(up = LauncherGestureAction.OPEN_APP_DRAWER)

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
}
