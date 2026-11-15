package com.riffle.app.launcher.stream

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import kotlin.math.abs

/**
 * The opacity of an open card's body (its thread and its actions) at an [openness] from 0 (a strip) to 1 (whole): it is
 * nothing until the card is half open and rises to full from there, so while the stack moves from one card to the next
 * the card that leaves has lost its body before the card that arrives has begun to show its own.
 */
internal fun bodyAlphaAt(openness: Float): Float =
    ((openness - BODY_FADE_START) / (1f - BODY_FADE_START)).coerceIn(0f, 1f)

private const val BODY_FADE_START = 0.5f

/** Test hooks: how opaque the open card is as the layer draws it, and how opaque its body is. */
internal val OpenCardAlphaKey = SemanticsPropertyKey<Float>("OpenCardAlpha")
internal val OpenCardBodyAlphaKey = SemanticsPropertyKey<Float>("OpenCardBodyAlpha")

/** Publishes the card's opacity and its body's for tests (read when the semantics are read, never per frame). */
internal fun Modifier.openCardAlphaSemantics(presence: OpenCardPresence): Modifier =
    semantics {
        this[OpenCardAlphaKey] = presence.alpha
        this[OpenCardBodyAlphaKey] = bodyAlphaAt(presence.openness)
    }

/** The smallest the header is drawn, as a strip: the headers of the strips around it are about this size. */
internal const val HEADER_STRIP_SCALE = 0.75f

/** The scale of an open card's header at an [openness] from 0 (a strip) to 1 (whole). */
internal fun headerScaleAt(openness: Float): Float =
    HEADER_STRIP_SCALE + (1f - HEADER_STRIP_SCALE) * openness.coerceIn(0f, 1f)

/** Fades the body with the card's [presence] (read in draw, so a moving stack recomposes nothing). */
internal fun Modifier.bodyFade(
    presence: OpenCardPresence?,
    cue: HandoffCueState? = null,
): Modifier =
    if (presence == null) {
        this
    } else {
        graphicsLayer { alpha = bodyAlphaAt(presence.openness) * (1f - CUE_DIM * abs(cue?.fraction ?: 0f)) }
    }

/** How much of the body's strength the cue takes at its fullest: the body gives way to it. */
private const val CUE_DIM = 0.55f

/** Scales the header from its start edge with the card's [presence], so it is the size of a strip's as the card is. */
internal fun Modifier.headerScale(presence: OpenCardPresence?): Modifier =
    if (presence == null) {
        this
    } else {
        graphicsLayer {
            val scale = headerScaleAt(presence.openness)
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }

/**
 * A column's arrangement: [spacing] between the children from the top and, when the column is taller than they are and
 * [pinLast], the last child (the card's actions) at the bottom, so a card held at its browse height that has little to
 * say keeps its reply row at its foot and not under its text.
 */
internal class PinnedLastArrangement(
    private val spacingDp: Dp,
    private val pinLast: Boolean,
) : Arrangement.Vertical {
    override val spacing: Dp get() = spacingDp

    override fun Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        outPositions: IntArray,
    ) {
        val gap = spacingDp.roundToPx()
        var y = 0
        sizes.forEachIndexed { index, size ->
            outPositions[index] = y
            y += size + gap
        }
        val used = y - if (sizes.isEmpty()) 0 else gap
        val extra = totalSize - used
        if (pinLast && sizes.size > 1 && extra > 0) outPositions[sizes.size - 1] += extra
    }
}
