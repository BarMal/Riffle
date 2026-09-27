package com.riffle.app.launcher

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/**
 * Riffle's one tactile spring, shared by the appearance sheet's expand/collapse, its tab-switch
 * pill, and (were it not for drag tracking needing to stay synchronous with the finger, see
 * [DiscreteSettingSlider]) a slider's snap-to-value. Bouncy enough to read as a physical detent
 * rather than a plain fade.
 */
internal fun <T> tactileMotionSpec(): FiniteAnimationSpec<T> =
    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)

/**
 * Riffle's single slider look, used by every discrete setting in the Cards appearance sheet: one
 * accent-colour fill, a neutral glass track, and small pips only at the boundaries -- the meaningful
 * values a dial-like control should mark, not decorative ticks with no explained meaning.
 *
 * Wraps Material's [Slider] rather than replacing it, so drag tracking, keyboard/rotary input, and
 * the accessibility semantics a caller attaches via [modifier] all stay Material's; only the track
 * and thumb visuals are Riffle's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TactileSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    val neutralTrack = MaterialTheme.colorScheme.onSurface.copy(alpha = TACTILE_TRACK_NEUTRAL_ALPHA)

    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier.fillMaxWidth(),
        thumb = {
            Box(
                modifier =
                    Modifier
                        .size(width = TACTILE_THUMB_WIDTH_DP.dp, height = TACTILE_THUMB_HEIGHT_DP.dp)
                        // 12dp: the shared cross-PR spec's "small" radius for thumbs/badges. Not yet
                        // a step on RiffleRadiusScale (closest is radiusS at 8dp), so this stays a
                        // local literal rather than reaching into a token file two sibling redesigns
                        // may be touching at the same time.
                        .clip(RoundedCornerShape(TACTILE_THUMB_RADIUS_DP.dp))
                        .background(accent),
            )
        },
        track = {
            Canvas(modifier = Modifier.fillMaxWidth().height(TACTILE_TRACK_HEIGHT_DP.dp)) {
                val fraction =
                    ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
                        .coerceIn(0f, 1f)
                val y = size.height / 2f
                val strokeWidthPx = TACTILE_TRACK_STROKE_DP.dp.toPx()
                val pipRadiusPx = TACTILE_PIP_RADIUS_DP.dp.toPx()

                drawLine(
                    color = neutralTrack,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = accent,
                    start = Offset(0f, y),
                    end = Offset(size.width * fraction, y),
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Round,
                )
                // Boundary pips only: the min and max are the only values a dial-like control needs
                // to mark, not every intermediate step.
                drawCircle(color = neutralTrack, radius = pipRadiusPx, center = Offset(pipRadiusPx, y))
                drawCircle(
                    color = neutralTrack,
                    radius = pipRadiusPx,
                    center = Offset(size.width - pipRadiusPx, y),
                )
            }
        },
    )
}

/** Which haptic, if any, a value change should tick -- a boundary tick at the range's ends, a plain
 * detent tick for every other step crossed, and nothing when the step has not actually changed. */
internal enum class TactileSliderHapticTick { NONE, DETENT, BOUNDARY }

/** Pure so the crossing rule (boundary beats detent, no tick for a no-op change) is unit-testable. */
internal fun tactileSliderHapticTick(
    previousValue: Int,
    newValue: Int,
    valueRange: IntRange,
): TactileSliderHapticTick =
    when {
        previousValue == newValue -> TactileSliderHapticTick.NONE
        newValue == valueRange.first || newValue == valueRange.last -> TactileSliderHapticTick.BOUNDARY
        else -> TactileSliderHapticTick.DETENT
    }

private const val TACTILE_TRACK_NEUTRAL_ALPHA = 0.24f
private const val TACTILE_TRACK_HEIGHT_DP = 24
private const val TACTILE_TRACK_STROKE_DP = 4
private const val TACTILE_PIP_RADIUS_DP = 2
private const val TACTILE_THUMB_WIDTH_DP = 4
private const val TACTILE_THUMB_HEIGHT_DP = 28
private const val TACTILE_THUMB_RADIUS_DP = 12
