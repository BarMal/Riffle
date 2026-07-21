package com.riffle.app.launcher

import android.graphics.Rect
import com.riffle.core.domain.launcher.cards.TimeScapeHingeBounds
import com.riffle.core.domain.launcher.cards.TimeScapeWindowLayout
import kotlin.math.roundToInt

/** Converts Android window coordinates into the framework-independent TimeScape pane input. */
internal fun timeScapeWindowLayoutFromPixels(
    widthPx: Int,
    heightPx: Int,
    density: Float,
    separatingHingeBounds: List<Rect>,
): TimeScapeWindowLayout {
    val safeDensity = density.takeIf { value -> value > 0f } ?: 1f

    fun Int.toDp(): Int = (this / safeDensity).roundToInt()

    return TimeScapeWindowLayout(
        widthDp = widthPx.toDp(),
        heightDp = heightPx.toDp(),
        separatingHinges =
            separatingHingeBounds.map { bounds ->
                TimeScapeHingeBounds(
                    leftDp = bounds.left.toDp(),
                    topDp = bounds.top.toDp(),
                    rightDp = bounds.right.toDp(),
                    bottomDp = bounds.bottom.toDp(),
                )
            },
    )
}
