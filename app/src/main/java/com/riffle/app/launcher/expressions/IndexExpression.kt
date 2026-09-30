package com.riffle.app.launcher.expressions

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.LensResult

/**
 * The Index expression: text first, each entry a title with its body snippet inline after it. Draws
 * flat results as one list and grouped results under a heading per group (for example notifications
 * per app). Owns the vertical scroll axis.
 */
@Composable
fun IndexExpression(
    result: LensResult,
    onItemClick: (Item) -> Unit,
    modifier: Modifier = Modifier,
    state: ExpressionState = ExpressionState.Ready,
    environment: ExpressionEnvironment = ExpressionEnvironment(),
) {
    val groups = remember(result) { result.asGroups().filter { it.items.isNotEmpty() } }
    ExpressionStateHost(
        state = state,
        isEmpty = groups.isEmpty(),
        reducedMotion = environment.reducedMotion,
        modifier = modifier,
        maxContentWidth = ExpressionMaxReadableWidth,
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = environment.contentPadding) {
            groups.forEach { group ->
                group.heading()?.let { heading ->
                    item(key = "group:${group.key}", contentType = HEADING_TYPE) { SectionHeading(text = heading) }
                }
                items(
                    items = group.items,
                    key = { "${group.key}|${it.lazyKey()}" },
                    contentType = { ENTRY_TYPE },
                ) { item ->
                    IndexRow(
                        item = item,
                        loader = environment.imageLoader,
                        timeFormatter = environment.timeFormatter,
                        onClick = onItemClick,
                    )
                }
            }
        }
    }
}

private const val HEADING_TYPE = "heading"
private const val ENTRY_TYPE = "entry"
