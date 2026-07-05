package com.riffle.app.launcher

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import kotlin.math.hypot

internal fun Modifier.homeGestureInput(
    enabled: Boolean,
    settings: HomeGestureSettings,
    onAction: (LauncherShellAction) -> Unit,
): Modifier =
    if (!enabled) {
        this
    } else {
        pointerInput(settings) {
            val interpreter = HomeSwipeGestureInterpreter(thresholdPx = HOME_SWIPE_GESTURE_THRESHOLD_PX)
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                var start = HomeGestureStart.from(listOf(down))
                var handled = false

                while (!handled) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val activeChanges = event.changes.filter { change -> change.pressed }
                    if (activeChanges.isEmpty()) {
                        return@awaitEachGesture
                    }
                    if (activeChanges.size != start.pointerCount) {
                        start = HomeGestureStart.from(activeChanges)
                    }

                    val drag = activeChanges.centroid() - start.centroid
                    val action =
                        homeSwipeActionForDrag(
                            pointerCount = activeChanges.size,
                            horizontalDragPx = drag.x,
                            verticalDragPx = drag.y,
                            scaleDelta = start.scaleDeltaFor(activeChanges),
                            settings = settings,
                            interpreter = interpreter,
                        )

                    if (action != null) {
                        handled = true
                        activeChanges.forEach { change -> change.consume() }
                        onAction(action)
                    }
                }
            }
        }
    }

private data class HomeGestureStart(
    val pointerCount: Int,
    val centroid: Offset,
    val distance: Float,
) {
    fun scaleDeltaFor(changes: List<PointerInputChange>): Float {
        if (pointerCount < 2 || distance <= 0f) {
            return 0f
        }

        return (changes.averageDistanceFromCentroid() - distance) / distance
    }

    companion object {
        fun from(changes: List<PointerInputChange>): HomeGestureStart =
            HomeGestureStart(
                pointerCount = changes.size,
                centroid = changes.centroid(),
                distance = changes.averageDistanceFromCentroid(),
            )
    }
}

private fun List<PointerInputChange>.centroid(): Offset {
    val activeChanges = filter { change -> change.pressed }.takeIf { changes -> changes.isNotEmpty() } ?: this
    val x = activeChanges.sumOf { change -> change.position.x.toDouble() }.toFloat() / activeChanges.size
    val y = activeChanges.sumOf { change -> change.position.y.toDouble() }.toFloat() / activeChanges.size
    return Offset(x, y)
}

private fun List<PointerInputChange>.averageDistanceFromCentroid(): Float {
    if (size < 2) {
        return 0f
    }

    val centroid = centroid()
    return sumOf { change ->
        hypot(
            x = (change.position.x - centroid.x).toDouble(),
            y = (change.position.y - centroid.y).toDouble(),
        )
    }.toFloat() / size
}

private const val HOME_SWIPE_GESTURE_THRESHOLD_PX = 80f
