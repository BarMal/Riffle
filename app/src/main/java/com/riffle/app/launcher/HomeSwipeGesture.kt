package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.HomeSwipeGestureSettings
import com.riffle.core.domain.launcher.settings.LauncherGestureAction

enum class HomeSwipeGesture {
    UP,
    DOWN,
    LEFT,
    RIGHT,
}

class HomeSwipeGestureInterpreter(
    private val thresholdPx: Float,
) {
    fun gestureFor(
        horizontalDragPx: Float,
        verticalDragPx: Float,
    ): HomeSwipeGesture? =
        if (kotlin.math.abs(horizontalDragPx) > kotlin.math.abs(verticalDragPx)) {
            horizontalGestureFor(horizontalDragPx)
        } else {
            verticalGestureFor(verticalDragPx)
        }

    private fun verticalGestureFor(verticalDragPx: Float): HomeSwipeGesture? =
        when {
            verticalDragPx <= -thresholdPx -> HomeSwipeGesture.UP
            verticalDragPx >= thresholdPx -> HomeSwipeGesture.DOWN
            else -> null
        }

    private fun horizontalGestureFor(horizontalDragPx: Float): HomeSwipeGesture? =
        when {
            horizontalDragPx <= -thresholdPx -> HomeSwipeGesture.LEFT
            horizontalDragPx >= thresholdPx -> HomeSwipeGesture.RIGHT
            else -> null
        }
}

class HomeSwipeGestureActionMapper {
    fun actionFor(
        gesture: HomeSwipeGesture,
        settings: HomeSwipeGestureSettings = HomeSwipeGestureSettings(),
    ): LauncherShellAction? =
        when (gesture) {
            HomeSwipeGesture.UP -> settings.up
            HomeSwipeGesture.DOWN -> settings.down
            HomeSwipeGesture.LEFT -> settings.left
            HomeSwipeGesture.RIGHT -> settings.right
        }.toShellAction()

    private fun LauncherGestureAction.toShellAction(): LauncherShellAction? =
        when (this) {
            LauncherGestureAction.NONE -> null
            LauncherGestureAction.OPEN_APP_DRAWER -> LauncherShellAction.OpenAppDrawer
            LauncherGestureAction.OPEN_NOTIFICATIONS -> LauncherShellAction.OpenNotifications
            LauncherGestureAction.OPEN_SEARCH -> LauncherShellAction.OpenSearch
            LauncherGestureAction.OPEN_SETTINGS -> LauncherShellAction.OpenSettings
            LauncherGestureAction.ENTER_HOME_EDIT_MODE -> LauncherShellAction.EnterHomeEditMode
            LauncherGestureAction.SELECT_NEXT_HOME_PAGE -> LauncherShellAction.SelectNextHomePage
            LauncherGestureAction.SELECT_PREVIOUS_HOME_PAGE -> LauncherShellAction.SelectPreviousHomePage
        }
}
