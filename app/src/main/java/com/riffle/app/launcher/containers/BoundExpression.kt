package com.riffle.app.launcher.containers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.riffle.app.launcher.expressions.AlphaListExpression
import com.riffle.app.launcher.expressions.CardExpression
import com.riffle.app.launcher.expressions.CardStackExpression
import com.riffle.app.launcher.expressions.CategoriesExpression
import com.riffle.app.launcher.expressions.ExpressionEnvironment
import com.riffle.app.launcher.expressions.ExpressionState
import com.riffle.app.launcher.expressions.IconGridExpression
import com.riffle.app.launcher.expressions.IconRowExpression
import com.riffle.app.launcher.expressions.IndexExpression
import com.riffle.app.launcher.expressions.ListExpression
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensResult

/**
 * Draws [result] with the expression for [kind]. The one place `ExpressionKind` maps to a composable, so
 * widgets, pages and page-set pages all resolve expressions the same way.
 */
@Composable
internal fun BoundExpression(
    kind: ExpressionKind,
    result: LensResult,
    state: ExpressionState,
    environment: ExpressionEnvironment,
    actions: ContainerActions,
    modifier: Modifier = Modifier,
) {
    when (kind) {
        ExpressionKind.ICON_ROW ->
            IconRowExpression(result, actions.onItemClick, modifier, state, environment)
        ExpressionKind.ICON_GRID ->
            IconGridExpression(result, actions.onItemClick, modifier, state, environment)
        ExpressionKind.LIST ->
            ListExpression(result, actions.onItemClick, modifier, state, environment)
        ExpressionKind.INDEX ->
            IndexExpression(result, actions.onItemClick, modifier, state, environment)
        ExpressionKind.CARD ->
            CardExpression(result, actions.onItemClick, modifier, actions.onAction, state, environment)
        ExpressionKind.CARD_STACK ->
            CardStackExpression(result, actions.onItemClick, modifier, actions.onAction, state, environment)
        ExpressionKind.CATEGORIES ->
            CategoriesExpression(result, actions.onItemClick, modifier, actions.onGroupClick, state, environment)
        ExpressionKind.ALPHA_LIST ->
            AlphaListExpression(result, actions.onItemClick, modifier, state, environment)
    }
}

/** A lens binding observed through the provider and drawn by its expression. */
@Composable
internal fun LensBoundExpression(
    binding: LensBinding,
    services: ContainerServices,
    modifier: Modifier = Modifier,
) {
    val output by rememberLensOutput(services.provider, binding.lens)
    BoundExpression(
        kind = binding.expression,
        result = output.resultOrEmpty(),
        state = output.toExpressionState(),
        environment = services.environment,
        actions = services.actions,
        modifier = modifier,
    )
}
