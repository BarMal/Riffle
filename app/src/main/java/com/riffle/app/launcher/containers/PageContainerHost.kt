package com.riffle.app.launcher.containers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.container.AxisDeclaration
import com.riffle.core.domain.launcher.workspace.container.AxisDeclarations
import com.riffle.core.domain.launcher.workspace.container.WidgetGridPlanner

/**
 * One full page: either a single lens + expression, or a free grid of widgets. Composition depth is
 * bounded by the model (page -> widget -> expression).
 *
 * [onAxesDeclared] receives the gesture axes this page and its widgets consume, for the surface to pass to
 * `homeGestureInput(childAxes = ...)`; nothing here handles pointer input itself.
 */
@Composable
fun PageContainerHost(
    container: PageContainer,
    services: ContainerServices,
    modifier: Modifier = Modifier,
    onAxesDeclared: (AxisDeclaration) -> Unit = {},
) {
    val declaration = remember(container) { AxisDeclarations.resolve(container) }
    LaunchedEffect(declaration) { onAxesDeclared(declaration) }
    when (val content = container.content) {
        is PageContent.Bound -> LensBoundExpression(content.binding, services, modifier.fillMaxSize())
        is PageContent.WidgetGrid -> WidgetGridHost(content, services, modifier)
    }
}

/**
 * Draws a planned widget grid: stored placements are planned once (overlapping or out-of-bounds widgets
 * are dropped, not drawn), laid out by their cell rectangles, and composed in reading order so keyboard and
 * TalkBack traversal follow it. Insets pad the grid as a whole; widgets inside get none of their own.
 */
@Composable
internal fun WidgetGridHost(
    grid: PageContent.WidgetGrid,
    services: ContainerServices,
    modifier: Modifier = Modifier,
) {
    val plan = remember(grid) { WidgetGridPlanner.plan(grid) }
    val widgetServices =
        remember(services) {
            services.copy(environment = services.environment.copy(contentPadding = PaddingValues()))
        }
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().padding(services.environment.contentPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        val width = if (maxWidth < GridMaxWidth) maxWidth else GridMaxWidth
        val height = if (constraints.hasBoundedHeight) maxHeight else GridFallbackRowHeight * plan.rows
        Box(modifier = Modifier.size(width, height).semantics { isTraversalGroup = true }) {
            plan.widgets.forEach { planned ->
                key(planned.key) {
                    val rect = WidgetGridPlanner.rectFor(plan, planned, width.value, height.value, GridGap.value)
                    WidgetContainerHost(
                        container = planned.widget,
                        services = widgetServices,
                        modifier =
                            Modifier
                                .offset(x = rect.left.dp, y = rect.top.dp)
                                .size(width = rect.width.dp, height = rect.height.dp),
                    )
                }
            }
        }
    }
}

/** Keeps cells readable on tablets and unfolded foldables rather than stretching them across the window. */
private val GridMaxWidth: Dp = 840.dp

/** Row height when the host gives the grid unbounded height (for example inside a scrolling parent). */
private val GridFallbackRowHeight: Dp = 120.dp

private val GridGap: Dp = RiffleSpacing.m
