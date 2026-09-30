package com.riffle.app.launcher.expressions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.LensResult

/**
 * The IconGrid expression: icons with labels in an adaptive grid. The column count follows the
 * available width, so an unfolded foldable or tablet shows more columns rather than bigger gaps.
 * Owns the vertical scroll axis (a [LazyVerticalGrid]).
 */
@Composable
fun IconGridExpression(
    result: LensResult,
    onItemClick: (Item) -> Unit,
    modifier: Modifier = Modifier,
    state: ExpressionState = ExpressionState.Ready,
    environment: ExpressionEnvironment = ExpressionEnvironment(),
) {
    val icons = remember(result) { result.allItems().filter { it.icon != null } }
    ExpressionStateHost(
        state = state,
        isEmpty = icons.isEmpty(),
        reducedMotion = environment.reducedMotion,
        modifier = modifier,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = IconCellWidth + RiffleSpacing.s),
            modifier = Modifier.fillMaxSize(),
            contentPadding = environment.contentPadding,
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
            horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.xs, Alignment.CenterHorizontally),
        ) {
            items(items = icons, key = { it.lazyKey() }, contentType = { CELL_TYPE }) { item ->
                IconCell(item = item, loader = environment.imageLoader, onClick = onItemClick)
            }
        }
    }
}

private const val CELL_TYPE = "cell"
