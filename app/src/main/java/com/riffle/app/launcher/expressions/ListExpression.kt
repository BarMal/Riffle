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
 * The List expression: one row per item with optional icon, subtitle and time. Owns the vertical
 * scroll axis (a [LazyColumn]); the host supplies [contentPadding] for system-bar and cutout insets.
 *
 * Reduced motion only affects the loading state here; a list has no motion of its own to remove.
 */
@Composable
fun ListExpression(
    result: LensResult,
    onItemClick: (Item) -> Unit,
    modifier: Modifier = Modifier,
    state: ExpressionState = ExpressionState.Ready,
    environment: ExpressionEnvironment = ExpressionEnvironment(),
) {
    val rows = remember(result) { result.allItems() }
    ExpressionStateHost(
        state = state,
        isEmpty = rows.isEmpty(),
        reducedMotion = environment.reducedMotion,
        modifier = modifier,
        maxContentWidth = ExpressionMaxReadableWidth,
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = environment.contentPadding) {
            items(items = rows, key = { it.lazyKey() }, contentType = { ROW_TYPE }) { item ->
                ItemRow(
                    item = item,
                    loader = environment.imageLoader,
                    timeFormatter = environment.timeFormatter,
                    onClick = onItemClick,
                )
            }
        }
    }
}

private const val ROW_TYPE = "row"
