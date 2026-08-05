package com.riffle.app.launcher

import android.graphics.Rect
import com.riffle.core.domain.launcher.cards.AdaptiveStageHingeBounds
import com.riffle.core.domain.launcher.cards.AdaptiveStagePosture
import com.riffle.core.domain.launcher.cards.AdaptiveStageWindowLayout
import kotlin.math.roundToInt

/** Converts Android window coordinates into the framework-independent AdaptiveStage pane input. */
internal fun adaptiveStageWindowLayoutFromPixels(
    widthPx: Int,
    heightPx: Int,
    density: Float,
    separatingHingeBounds: List<Rect>,
    posture: AdaptiveStagePosture = AdaptiveStagePosture.UNKNOWN,
): AdaptiveStageWindowLayout {
    val safeDensity = density.takeIf { value -> value > 0f } ?: 1f

    fun Int.toDp(): Int = (this / safeDensity).roundToInt()

    return AdaptiveStageWindowLayout(
        widthDp = widthPx.toDp(),
        heightDp = heightPx.toDp(),
        separatingHinges =
            separatingHingeBounds.map { bounds ->
                AdaptiveStageHingeBounds(
                    leftDp = bounds.left.toDp(),
                    topDp = bounds.top.toDp(),
                    rightDp = bounds.right.toDp(),
                    bottomDp = bounds.bottom.toDp(),
                )
            },
        posture = posture,
    )
}
