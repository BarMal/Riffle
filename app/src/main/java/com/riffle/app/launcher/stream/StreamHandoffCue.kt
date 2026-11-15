package com.riffle.app.launcher.stream

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.designsystem.GlassWeight

/**
 * How the cue's opacity follows the drag ([fraction] 0 to 1): it rises with the first part of the drag and is whole by
 * two thirds of the way to the threshold, while its line goes on filling to the threshold itself.
 */
internal fun handoffCueAlpha(
    fraction: Float,
    openness: Float,
): Float = (kotlin.math.abs(fraction) * CUE_RISE).coerceIn(0f, 1f) * bodyAlphaAt(openness)

private const val CUE_RISE = 1.5f

/**
 * The cue at the edge of an open card's scrolling body: with the finger dragged past the body's top (down) the older
 * card above is named at the top, past its bottom (up) the newer card below is named at the bottom, each a chevron and
 * the neighbour's title in the type of the Dismiss cue, with a thin line that fills as the drag nears the hand-off
 * threshold. Nothing is drawn when there is no neighbour in that direction ([olderTitle] or [newerTitle] null) or no
 * drag. Read in draw, so the drag recomposes nothing; the cue is no part of the tree for assistive technology (the
 * card's own Next and Previous card actions are).
 */
@Composable
internal fun androidx.compose.foundation.layout.BoxScope.HandoffCue(
    cue: HandoffCueState,
    presence: OpenCardPresence,
    olderTitle: String?,
    newerTitle: String?,
) {
    com.riffle.app.launcher.CountRecomposition("handoff-cue")
    if (olderTitle != null) CuePill(cue, presence, olderTitle, older = true, Modifier.align(Alignment.TopCenter))
    if (newerTitle != null) CuePill(cue, presence, newerTitle, older = false, Modifier.align(Alignment.BottomCenter))
}

@Composable
private fun CuePill(
    cue: HandoffCueState,
    presence: OpenCardPresence,
    title: String,
    older: Boolean,
    modifier: Modifier,
) {
    val line = MaterialTheme.colorScheme.onSurface
    Box(
        modifier =
            modifier
                .padding(vertical = RiffleSpacing.s)
                .widthIn(max = CueMaxWidth)
                .clearAndSetSemantics {}
                .graphicsLayer {
                    // Dragging down (positive) reaches for the older card above; up (negative) for the one below.
                    val toward = if (older) cue.fraction else -cue.fraction
                    alpha = if (toward > 0f) handoffCueAlpha(toward, presence.openness) else 0f
                },
    ) {
        GlassSurface(
            weight = GlassWeight.Regular,
            shape = RiffleShapes.full,
            tint = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Box(
                modifier =
                    Modifier.drawBehind {
                        val toward = if (older) cue.fraction else -cue.fraction
                        val fill = toward.coerceIn(0f, 1f)
                        val h = 2.dp.toPx()
                        drawRect(
                            line,
                            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - h),
                            size = Size(size.width * fill, h),
                            alpha = 0.6f,
                        )
                    },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = RiffleSpacing.m, vertical = RiffleSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(RiffleSpacing.xs),
                ) {
                    Icon(
                        imageVector = if (older) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
    }
}

private val CueMaxWidth = 280.dp
