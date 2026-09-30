package com.riffle.app.launcher.expressions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.designsystem.RiffleElevation
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.expressions.AlphaIndex
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.LensResult
import kotlinx.coroutines.launch

/**
 * The AlphaList expression: items bucketed under A-Z headings (see [AlphaIndex]) with a scrubber
 * beside the list. The scrubber is draggable and every letter is also a labelled button, so jumping
 * sections never needs a gesture. Owns the vertical scroll axis.
 *
 * Jumping uses an instant scroll, so there is no travel to remove under reduced motion.
 */
@Composable
fun AlphaListExpression(
    result: LensResult,
    onItemClick: (Item) -> Unit,
    modifier: Modifier = Modifier,
    state: ExpressionState = ExpressionState.Ready,
    environment: ExpressionEnvironment = ExpressionEnvironment(),
) {
    val sections = remember(result) { AlphaIndex.sections(result.allItems()) { it.title } }
    val headerIndices = remember(sections) { AlphaIndex.headerIndices(sections) }
    val letters = remember(sections) { sections.map { it.letter } }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var bubbleLetter by remember { mutableStateOf<Char?>(null) }
    val direction = LocalLayoutDirection.current
    ExpressionStateHost(
        state = state,
        isEmpty = sections.isEmpty(),
        reducedMotion = environment.reducedMotion,
        modifier = modifier,
        maxContentWidth = ExpressionMaxReadableWidth,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    contentPadding =
                        PaddingValues(
                            start = environment.contentPadding.calculateStartPadding(direction),
                            top = environment.contentPadding.calculateTopPadding(),
                            end = 0.dp,
                            bottom = environment.contentPadding.calculateBottomPadding(),
                        ),
                ) {
                    sections.forEach { section ->
                        item(key = "letter:${section.letter}", contentType = HEADING_TYPE) {
                            SectionHeading(text = section.letter.toString())
                        }
                        items(items = section.entries, key = { it.lazyKey() }, contentType = { ROW_TYPE }) { item ->
                            ItemRow(
                                item = item,
                                loader = environment.imageLoader,
                                timeFormatter = environment.timeFormatter,
                                onClick = onItemClick,
                            )
                        }
                    }
                }
                AlphaScrubber(
                    letters = letters,
                    onLetterSelected = { letter ->
                        bubbleLetter = letter
                        headerIndices[letter]?.let { index -> scope.launch { listState.scrollToItem(index) } }
                    },
                    onDragEnd = { bubbleLetter = null },
                    modifier =
                        Modifier.padding(
                            top = environment.contentPadding.calculateTopPadding(),
                            end = environment.contentPadding.calculateEndPadding(direction),
                            bottom = environment.contentPadding.calculateBottomPadding(),
                        ),
                )
            }
            bubbleLetter?.let { letter -> LetterBubble(letter = letter, modifier = Modifier.align(Alignment.Center)) }
        }
    }
}

/** Large letter shown over the list while dragging the scrubber. Purely visual: the list itself announces the jump. */
@Composable
private fun LetterBubble(
    letter: Char,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clearAndSetSemantics {},
        shape = RiffleShapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = RiffleElevation.level3,
        shadowElevation = RiffleElevation.level3,
    ) {
        Text(
            text = letter.toString(),
            modifier = Modifier.padding(horizontal = RiffleSpacing.xl, vertical = RiffleSpacing.l),
            style = MaterialTheme.typography.displaySmall,
        )
    }
}

private const val HEADING_TYPE = "heading"
private const val ROW_TYPE = "row"
