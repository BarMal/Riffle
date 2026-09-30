package com.riffle.app.launcher.expressions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.expressions.AlphaIndex

/**
 * The A-Z scrubber: a column of section letters. Two equivalent ways to use it, so a drag is never
 * the only path: drag along the column (a platform `draggable`, no custom pointer loop), or tap /
 * activate any letter, each of which is a labelled button for TalkBack and switch access.
 *
 * Letters are thinned to what fits at the current font scale, so they never overlap.
 *
 * @param onLetterSelected called when a letter is tapped or the drag moves onto a new letter.
 * @param onDragEnd called when a drag finishes, so the host can hide its letter bubble.
 */
@Composable
internal fun AlphaScrubber(
    letters: List<Char>,
    onLetterSelected: (Char) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val cellHeight = RiffleSpacing.xl * fontScale
    BoxWithConstraints(modifier = modifier.fillMaxHeight().width(ExpressionMinTouchTarget)) {
        val shown = AlphaIndex.thin(letters, (maxHeight / cellHeight).toInt())
        var heightPx by remember { mutableIntStateOf(1) }
        var pointerY by remember { mutableFloatStateOf(0f) }
        var lastCell by remember { mutableIntStateOf(-1) }

        fun select(y: Float) {
            if (shown.isEmpty()) return
            val cell = AlphaIndex.cellAt(y / heightPx, shown.size)
            if (cell != lastCell) {
                lastCell = cell
                onLetterSelected(shown[cell])
            }
        }

        val dragState =
            rememberDraggableState { delta ->
                pointerY += delta
                select(pointerY)
            }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStarted = { start ->
                            pointerY = start.y
                            select(pointerY)
                        },
                        onDragStopped = {
                            lastCell = -1
                            onDragEnd()
                        },
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            shown.forEach { letter ->
                ScrubberLetter(letter = letter, onClick = { onLetterSelected(letter) })
            }
        }
    }
}

@Composable
private fun ColumnScope.ScrubberLetter(
    letter: Char,
    onClick: () -> Unit,
) {
    Text(
        text = letter.toString(),
        modifier =
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(onClickLabel = scrubberLetterLabel(letter), role = Role.Button, onClick = onClick)
                .wrapContentHeight(Alignment.CenterVertically)
                .semantics { contentDescription = scrubberLetterLabel(letter) },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

internal fun scrubberLetterLabel(letter: Char): String =
    if (letter == AlphaIndex.OTHER) "Jump to other items" else "Jump to $letter"
