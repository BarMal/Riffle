package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import com.riffle.core.domain.launcher.settings.LauncherGestureAction

class HomeSwipeGestureInterpreter(
    private val thresholdPx: Float,
    private val pinchThreshold: Float = 0.18f,
    private val axisDominanceRatio: Float = 1.2f,
) {
    fun gestureFor(
        pointerCount: Int = 1,
        horizontalDragPx: Float,
        verticalDragPx: Float,
        scaleDelta: Float = 0f,
    ): HomeGesture? {
        val dragAxis = dominantAxis(horizontalDragPx = horizontalDragPx, verticalDragPx = verticalDragPx)

        return when {
            pointerCount >= 2 && kotlin.math.abs(scaleDelta) >= pinchThreshold ->
                pinchGestureFor(scaleDelta)

            dragAxis == GestureAxis.HORIZONTAL ->
                horizontalGestureFor(pointerCount = pointerCount, horizontalDragPx = horizontalDragPx)

            dragAxis == GestureAxis.VERTICAL ->
                verticalGestureFor(pointerCount = pointerCount, verticalDragPx = verticalDragPx)

            else -> null
        }
    }

    private fun dominantAxis(
        horizontalDragPx: Float,
        verticalDragPx: Float,
    ): GestureAxis? {
        val horizontal = kotlin.math.abs(horizontalDragPx)
        val vertical = kotlin.math.abs(verticalDragPx)

        return when {
            horizontal >= vertical * axisDominanceRatio -> GestureAxis.HORIZONTAL
            vertical >= horizontal * axisDominanceRatio -> GestureAxis.VERTICAL
            else -> null
        }
    }

    private fun verticalGestureFor(
        pointerCount: Int,
        verticalDragPx: Float,
    ): HomeGesture? =
        when {
            verticalDragPx <= -thresholdPx ->
                if (pointerCount >= 2) {
                    HomeGesture.TWO_FINGER_UP
                } else {
                    HomeGesture.ONE_FINGER_UP
                }

            verticalDragPx >= thresholdPx ->
                if (pointerCount >= 2) {
                    HomeGesture.TWO_FINGER_DOWN
                } else {
                    HomeGesture.ONE_FINGER_DOWN
                }

            else -> null
        }

    private fun horizontalGestureFor(
        pointerCount: Int,
        horizontalDragPx: Float,
    ): HomeGesture? =
        when {
            horizontalDragPx <= -thresholdPx ->
                if (pointerCount >= 2) {
                    HomeGesture.TWO_FINGER_LEFT
                } else {
                    HomeGesture.ONE_FINGER_LEFT
                }

            horizontalDragPx >= thresholdPx ->
                if (pointerCount >= 2) {
                    HomeGesture.TWO_FINGER_RIGHT
                } else {
                    HomeGesture.ONE_FINGER_RIGHT
                }

            else -> null
        }

    private fun pinchGestureFor(scaleDelta: Float): HomeGesture =
        if (scaleDelta < 0f) {
            HomeGesture.PINCH_IN
        } else {
            HomeGesture.PINCH_OUT
        }
}

private enum class GestureAxis {
    HORIZONTAL,
    VERTICAL,
}

class HomeSwipeGestureActionMapper {
    fun actionFor(
        gesture: HomeGesture,
        settings: HomeGestureSettings = HomeGestureSettings(),
    ): LauncherShellAction? = settings.actionFor(gesture).toShellAction()

    private fun LauncherGestureAction.toShellAction(): LauncherShellAction? =
        when (this) {
            LauncherGestureAction.NONE -> null
            LauncherGestureAction.OPEN_APP_DRAWER -> LauncherShellAction.OpenAppDrawer
            LauncherGestureAction.OPEN_NOTIFICATIONS -> LauncherShellAction.OpenNotifications
            LauncherGestureAction.OPEN_SEARCH -> LauncherShellAction.OpenSearch
            LauncherGestureAction.OPEN_SETTINGS -> LauncherShellAction.OpenSettings
            LauncherGestureAction.ENTER_HOME_EDIT_MODE -> LauncherShellAction.EnterHomeEditMode
            LauncherGestureAction.ENTER_HOME_PAGE_OVERVIEW -> LauncherShellAction.EnterHomePageOverview
            LauncherGestureAction.ENTER_FULLSCREEN_HOME -> LauncherShellAction.SelectFullscreenHomeEnabled(true)
            LauncherGestureAction.SELECT_NEXT_HOME_PAGE -> LauncherShellAction.SelectNextHomePage
            LauncherGestureAction.SELECT_PREVIOUS_HOME_PAGE -> LauncherShellAction.SelectPreviousHomePage
        }
}

fun homeSwipeActionForDrag(
    pointerCount: Int = 1,
    horizontalDragPx: Float,
    verticalDragPx: Float,
    scaleDelta: Float = 0f,
    settings: HomeGestureSettings = HomeGestureSettings(),
    interpreter: HomeSwipeGestureInterpreter,
    actionMapper: HomeSwipeGestureActionMapper = HomeSwipeGestureActionMapper(),
): LauncherShellAction? =
    interpreter.gestureFor(
        pointerCount = pointerCount,
        horizontalDragPx = horizontalDragPx,
        verticalDragPx = verticalDragPx,
        scaleDelta = scaleDelta,
    )?.let { gesture -> actionMapper.actionFor(gesture = gesture, settings = settings) }
