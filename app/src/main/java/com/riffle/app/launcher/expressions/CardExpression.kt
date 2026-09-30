package com.riffle.app.launcher.expressions

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemAction
import com.riffle.core.domain.launcher.workspace.LensResult

/**
 * The Card expression: the first item of the result as one rich card (artwork, text, actions).
 * Owns no gesture axis and wraps to its content height. Extra items are ignored, since the lens
 * is expected to limit the result to one.
 */
@Composable
fun CardExpression(
    result: LensResult,
    onItemClick: (Item) -> Unit,
    modifier: Modifier = Modifier,
    onAction: (Item, ItemAction) -> Unit = { _, _ -> },
    state: ExpressionState = ExpressionState.Ready,
    environment: ExpressionEnvironment = ExpressionEnvironment(),
) {
    val item = remember(result) { result.allItems().firstOrNull() }
    ExpressionStateHost(
        state = state,
        isEmpty = item == null,
        reducedMotion = environment.reducedMotion,
        modifier = modifier,
        maxContentWidth = ExpressionMaxReadableWidth,
    ) {
        if (item != null) {
            ItemCard(
                item = item,
                environment = environment,
                onItemClick = onItemClick,
                onAction = onAction,
                modifier = Modifier.fillMaxWidth().padding(environment.contentPadding),
            )
        }
    }
}
