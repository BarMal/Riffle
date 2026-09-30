package com.riffle.app.launcher.expressions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.LensResult

/**
 * The IconRow expression: icons in a single horizontally scrolling row, each with its title as a
 * label when the lens projects one. Owns the horizontal scroll axis (a [LazyRow]). Wraps to its
 * content height, so a host can place it in a widget or above a list.
 */
@Composable
fun IconRowExpression(
    result: LensResult,
    onItemClick: (Item) -> Unit,
    modifier: Modifier = Modifier,
    state: ExpressionState = ExpressionState.Ready,
    environment: ExpressionEnvironment = ExpressionEnvironment(),
    showLabels: Boolean = true,
) {
    val icons = remember(result) { result.allItems().filter { it.icon != null } }
    ExpressionStateHost(
        state = state,
        isEmpty = icons.isEmpty(),
        reducedMotion = environment.reducedMotion,
        modifier = modifier,
    ) {
        LazyRow(
            contentPadding = environment.contentPadding,
            horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
        ) {
            items(items = icons, key = { it.lazyKey() }, contentType = { CELL_TYPE }) { item ->
                IconCell(
                    item = item,
                    loader = environment.imageLoader,
                    onClick = onItemClick,
                    showLabel = showLabels,
                )
            }
        }
    }
}

private const val CELL_TYPE = "cell"
