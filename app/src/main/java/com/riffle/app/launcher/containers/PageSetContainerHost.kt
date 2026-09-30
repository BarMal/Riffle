package com.riffle.app.launcher.containers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import com.riffle.app.launcher.designsystem.RiffleMotion
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.expressions.ExpressionState
import com.riffle.app.launcher.expressions.ExpressionStateHost
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LensResult
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.container.AxisDeclaration
import com.riffle.core.domain.launcher.workspace.container.AxisDeclarations
import com.riffle.core.domain.launcher.workspace.container.LensAvailability
import com.riffle.core.domain.launcher.workspace.container.PageSetPage
import com.riffle.core.domain.launcher.workspace.container.PageSetPlan
import com.riffle.core.domain.launcher.workspace.container.PageSetPlanner
import com.riffle.core.domain.launcher.workspace.container.PageSetSelection
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * A grouped lens expanded to one page per group, paged with the platform [HorizontalPager] (no pointer
 * handling of its own) under a tab row, which also gives a non-gesture way to reach every page.
 *
 * Pages are keyed by group, and the selected page follows its group when groups change; only the visible
 * page (plus the pager's own neighbour) is composed, however many groups there are (the plan caps them).
 * Reduced motion makes page changes jump instead of settle.
 *
 * [onAxesDeclared] receives the gesture axes this container consumes, without the pager axis while there
 * is nothing to page to, for the surface to pass to `homeGestureInput(childAxes = ...)`.
 */
@Composable
fun PageSetContainerHost(
    container: PageSetContainer,
    services: ContainerServices,
    modifier: Modifier = Modifier,
    onAxesDeclared: (AxisDeclaration) -> Unit = {},
) {
    val output by rememberLensOutput(services.provider, container.binding.lens)
    val plan = remember(output) { PageSetPlanner.plan(output.result ?: LensResult.Grouped(emptyList())) }
    val declaration =
        remember(container, plan.pages.size) { AxisDeclarations.resolve(container).forPageCount(plan.pages.size) }
    LaunchedEffect(declaration) { onAxesDeclared(declaration) }
    val state =
        if (output.availability == LensAvailability.READY && plan.shapeMismatch) {
            ExpressionState.Unavailable(ContainerText.NEEDS_GROUPED)
        } else {
            output.toExpressionState()
        }
    ExpressionStateHost(
        state = state,
        isEmpty = plan.isEmpty,
        reducedMotion = services.environment.reducedMotion,
        modifier = modifier.fillMaxSize(),
    ) {
        PageSetPager(plan = plan, kind = container.binding.expression, services = services)
    }
}

@Composable
private fun PageSetPager(
    plan: PageSetPlan,
    kind: ExpressionKind,
    services: ContainerServices,
) {
    val environment = services.environment
    val selection = remember { PageSetSelection() }
    val pageCount by rememberUpdatedState(plan.pages.size)
    val pagerState = rememberPagerState(pageCount = { pageCount })
    LaunchedEffect(plan) {
        val target = selection.reconcile(plan, pagerState.currentPage)
        if (target != pagerState.currentPage) pagerState.scrollToPage(target)
        snapshotFlow { pagerState.settledPage }.collect { index -> selection.settled(plan, index) }
    }
    val direction = LocalLayoutDirection.current
    val outer = environment.contentPadding
    // The tab row takes the top and side insets; pages keep only the bottom one.
    val pageEnvironment = environment.copy(contentPadding = PaddingValues(bottom = outer.calculateBottomPadding()))
    val pageServices = remember(services) { services.copy(environment = pageEnvironment) }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    start = outer.calculateStartPadding(direction),
                    top = outer.calculateTopPadding(),
                    end = outer.calculateEndPadding(direction),
                ),
    ) {
        if (plan.pages.size > 1) {
            PageTabs(plan = plan, pagerState = pagerState, reducedMotion = environment.reducedMotion)
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            flingBehavior =
                PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapAnimationSpec = RiffleMotion.smooth<Float>(environment.reducedMotion),
                ),
            key = { index -> plan.pages.getOrNull(index)?.key ?: index },
        ) { index ->
            val page = plan.pages.getOrNull(index)
            if (page != null) {
                val result = remember(page) { LensResult.Flat(page.items) }
                BoundExpression(
                    kind = kind,
                    result = result,
                    state = ExpressionState.Ready,
                    environment = pageServices.environment,
                    actions = pageServices.actions,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun PageTabs(
    plan: PageSetPlan,
    pagerState: PagerState,
    reducedMotion: Boolean,
) {
    val scope = rememberCoroutineScope()
    ScrollableTabRow(
        selectedTabIndex = pagerState.currentPage.coerceIn(0, plan.pages.lastIndex),
        edgePadding = RiffleSpacing.l,
    ) {
        plan.pages.forEachIndexed { index, page ->
            Tab(
                selected = pagerState.currentPage == index,
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(index, animationSpec = RiffleMotion.smooth<Float>(reducedMotion))
                    }
                },
                text = { Text(text = page.title(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

/** The tab label: the group's label, else its key, else a neutral name for the ungrouped page. */
internal fun PageSetPage.title(): String =
    label?.takeIf { it.isNotBlank() } ?: groupKey.takeIf { it.isNotBlank() } ?: ContainerText.OTHER_PAGE
