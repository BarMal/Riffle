package com.riffle.app.launcher.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.CountRecomposition
import com.riffle.core.domain.launcher.onboarding.OnboardingStep

/** The gesture a tour step shows on its small Dock: what the finger does. */
internal enum class TourGesture {
    /** Press the Dock down and let go: the Stream's cards rise. */
    PRESS,

    /** Pull the Dock up: the Library's sheet rises with the finger. */
    PULL,

    /** Hold, then slide along the Dock: the ring moves from tile to tile and the cards follow. */
    HOLD_SLIDE,
    ;

    companion object {
        /** The gesture the [step] shows, or null for a step that has none to show (Home). */
        fun of(step: OnboardingStep): TourGesture? =
            when (step) {
                OnboardingStep.HOME -> null
                OnboardingStep.DOCK -> PRESS
                OnboardingStep.LIBRARY -> PULL
                OnboardingStep.STREAM -> HOLD_SLIDE
            }
    }
}

/**
 * One frame of a tour illustration, all in the unit square of the small screen: where the finger is and how visible,
 * how far the Dock is pressed down ([dockDip], 0 to 1), how far what the gesture reveals has come ([reveal], 0 to 1)
 * and which Dock tile wears the ring ([ringIndex], or -1 for none).
 */
internal data class TourFrame(
    val finger: Offset,
    val fingerAlpha: Float,
    val dockDip: Float,
    val reveal: Float,
    val ringIndex: Int,
)

/**
 * The tour's motion as pure maths: [frame] is the picture at loop time `t` (0 to 1), and [staticFrame] the one picture
 * that stands for the whole gesture under reduced motion (the finger where it ends, what it reveals shown).
 */
internal object TourMotion {
    const val LOOP_MILLIS = 3200

    const val DOCK_Y = 0.86f
    const val DIP = 0.05f
    val TILE_X = listOf(0.34f, 0.5f, 0.66f)

    fun frame(
        gesture: TourGesture,
        t: Float,
    ): TourFrame {
        val time = t.coerceIn(0f, 1f)
        return when (gesture) {
            TourGesture.PRESS -> press(time)
            TourGesture.PULL -> pull(time)
            TourGesture.HOLD_SLIDE -> holdSlide(time)
        }
    }

    fun staticFrame(gesture: TourGesture): TourFrame =
        when (gesture) {
            TourGesture.PRESS ->
                TourFrame(Offset(TILE_X[1], DOCK_Y + DIP), fingerAlpha = 1f, dockDip = 1f, reveal = 1f, ringIndex = -1)
            TourGesture.PULL ->
                TourFrame(Offset(TILE_X[1], PULL_TOP), fingerAlpha = 1f, dockDip = 0f, reveal = 1f, ringIndex = -1)
            TourGesture.HOLD_SLIDE ->
                TourFrame(Offset(TILE_X[2], DOCK_Y), fingerAlpha = 1f, dockDip = 0f, reveal = 1f, ringIndex = 2)
        }

    private const val PULL_TOP = 0.4f

    // Down, hold, let go: the Dock dips under the finger and the cards rise as it lifts.
    private fun press(t: Float): TourFrame {
        val down = ease(segment(t, 0.12f, 0.4f))
        val up = ease(segment(t, 0.58f, 0.78f))
        val gone = ease(segment(t, 0.9f, 1f))
        return TourFrame(
            finger = Offset(TILE_X[1], DOCK_Y + DIP * down),
            fingerAlpha = ease(segment(t, 0f, 0.12f)) * (1f - ease(segment(t, 0.58f, 0.72f))),
            dockDip = down * (1f - up),
            reveal = up * (1f - gone),
            ringIndex = -1,
        )
    }

    // The finger takes the Dock's edge up and the sheet follows it.
    private fun pull(t: Float): TourFrame {
        val rise = ease(segment(t, 0.15f, 0.6f))
        val gone = ease(segment(t, 0.88f, 1f))
        return TourFrame(
            finger = Offset(TILE_X[1], DOCK_Y + (PULL_TOP - DOCK_Y) * rise),
            fingerAlpha = ease(segment(t, 0f, 0.15f)) * (1f - ease(segment(t, 0.72f, 0.82f))),
            dockDip = 0f,
            reveal = rise * (1f - gone),
            ringIndex = -1,
        )
    }

    // Hold on the first tile until the ring comes, then slide along to the third; the cards follow the ring.
    private fun holdSlide(t: Float): TourFrame {
        val slide = ease(segment(t, 0.4f, 0.8f))
        val x = TILE_X[0] + (TILE_X[2] - TILE_X[0]) * slide
        val ringed = t >= 0.3f
        val index = if (!ringed) -1 else if (slide < 0.35f) 0 else if (slide < 0.85f) 1 else 2
        return TourFrame(
            finger = Offset(x, DOCK_Y),
            fingerAlpha = ease(segment(t, 0f, 0.1f)) * (1f - ease(segment(t, 0.9f, 1f))),
            dockDip = 0f,
            reveal = 1f,
            ringIndex = index,
        )
    }

    /** 0 before [from], 1 after [to], linear between. */
    fun segment(
        t: Float,
        from: Float,
        to: Float,
    ): Float = ((t - from) / (to - from)).coerceIn(0f, 1f)

    /** Smoothstep: starts and ends slowly. */
    fun ease(x: Float): Float = x * x * (3f - 2f * x)
}

/**
 * A small looped picture of the gesture a tour step teaches: a screen with a Dock and a finger. Drawn in Compose, no
 * assets. The loop runs in the draw phase (the animated value is read only inside the canvas), so nothing recomposes
 * per frame; under [reducedMotion] one still frame is drawn and no animation exists. It is decorative: the step's text
 * says the same thing.
 */
@Composable
internal fun TourIllustration(
    gesture: TourGesture,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    CountRecomposition("tour-illustration")
    val still = remember(gesture) { TourMotion.staticFrame(gesture) }
    val clock: State<Float>? =
        if (reducedMotion) {
            null
        } else {
            rememberInfiniteTransition(label = "tour").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(tween(TourMotion.LOOP_MILLIS, easing = LinearEasing), RepeatMode.Restart),
                label = "tour-loop",
            )
        }
    val scheme = MaterialTheme.colorScheme
    val colors =
        TourColors(
            screen = scheme.outlineVariant,
            dock = scheme.surfaceContainerHigh,
            icon = scheme.onSurfaceVariant,
            cards = listOf(scheme.secondaryContainer, scheme.surfaceContainerHighest, scheme.surfaceContainer),
            finger = scheme.primary,
            touch = scheme.primaryContainer,
        )
    Canvas(
        modifier =
            modifier
                .widthIn(max = 240.dp)
                .fillMaxWidth()
                .height(150.dp)
                .testTag(TOUR_ILLUSTRATION_TEST_TAG)
                .clearAndSetSemantics {},
    ) {
        // The value is read here, in the draw phase: a new frame redraws this canvas and recomposes nothing.
        val frame = clock?.let { TourMotion.frame(gesture, it.value) } ?: still
        drawTour(gesture, frame, colors)
    }
}

internal const val TOUR_ILLUSTRATION_TEST_TAG = "tour-illustration"

private class TourColors(
    val screen: Color,
    val dock: Color,
    val icon: Color,
    /** Front to back. */
    val cards: List<Color>,
    val finger: Color,
    val touch: Color,
)

private fun DrawScope.drawTour(
    gesture: TourGesture,
    frame: TourFrame,
    colors: TourColors,
) {
    val w = size.width
    val h = size.height
    val line = 2.dp.toPx()
    val corner = CornerRadius(14.dp.toPx())
    drawRoundRect(colors.screen, Offset(line / 2, line / 2), Size(w - line, h - line), corner, Stroke(line))

    val dockW = w * 0.62f
    val dockH = h * 0.15f
    val dockTop = h * TourMotion.DOCK_Y - dockH / 2 + frame.dockDip * h * TourMotion.DIP
    val dockLeft = (w - dockW) / 2
    drawReveal(gesture, frame, colors, dockTop)
    drawRoundRect(colors.dock, Offset(dockLeft, dockTop), Size(dockW, dockH), CornerRadius(dockH / 2))
    TourMotion.TILE_X.forEachIndexed { index, x ->
        val centre = Offset(w * x, dockTop + dockH / 2)
        drawCircle(colors.icon, radius = dockH * 0.26f, center = centre)
        if (index == frame.ringIndex) {
            drawCircle(colors.finger, radius = dockH * 0.42f, center = centre, style = Stroke(line))
        }
    }

    if (frame.fingerAlpha > 0f) {
        val at = Offset(w * frame.finger.x, h * frame.finger.y)
        drawCircle(colors.touch, radius = 12.dp.toPx(), center = at, alpha = frame.fingerAlpha)
        drawCircle(colors.finger, radius = 6.dp.toPx(), center = at, alpha = frame.fingerAlpha)
    }
}

/** What the gesture reveals above the Dock: cards (the Stream) or a sheet (the Library), by [TourFrame.reveal]. */
private fun DrawScope.drawReveal(
    gesture: TourGesture,
    frame: TourFrame,
    colors: TourColors,
    dockTop: Float,
) {
    if (frame.reveal <= 0f) return
    val w = size.width
    val h = size.height
    val pad = w * 0.12f
    val gap = 6.dp.toPx()
    when (gesture) {
        TourGesture.PULL -> {
            // The sheet's top edge follows the finger up from the Dock; the sheet is as wide as the screen's margins.
            val top = dockTop - (dockTop - h * 0.18f) * frame.reveal
            drawRoundRect(
                colors.cards.first(),
                Offset(pad, top),
                Size(w - 2 * pad, (dockTop - gap - top).coerceAtLeast(0f)),
                CornerRadius(10.dp.toPx()),
                alpha = frame.reveal,
            )
        }
        TourGesture.PRESS, TourGesture.HOLD_SLIDE -> {
            // A stack of three cards standing above the Dock; each peeks a strip above the one in front of it.
            val cardH = h * 0.2f
            val strip = h * 0.09f
            // Drawn back to front, so the front card covers the strips of those behind it.
            for (depth in CARDS - 1 downTo 0) {
                val top = dockTop - gap - cardH - strip * depth - (1f - frame.reveal) * h * 0.1f
                val inset = pad + depth * w * 0.03f
                drawRoundRect(
                    colors.cards[depth],
                    Offset(inset, top),
                    Size(w - 2 * inset, cardH),
                    CornerRadius(8.dp.toPx()),
                    alpha = frame.reveal,
                )
            }
        }
    }
}

private const val CARDS = 3
