package com.riffle.app.launcher.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.WorkspaceMenuHost
import com.riffle.app.launcher.WorkspaceMenuLayer
import com.riffle.app.launcher.containers.ContainerServices
import com.riffle.app.launcher.containers.LensBoundExpression
import com.riffle.app.launcher.containers.PageContainerHost
import com.riffle.app.launcher.containers.PageSetContainerHost
import com.riffle.app.launcher.designsystem.RiffleMotion
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.pool.PlacedHomeContent
import com.riffle.app.launcher.pool.PoolHomePage
import com.riffle.app.launcher.workspaceMenuActionModifier
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageHost
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.ReturnBehavior
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceDock
import com.riffle.core.domain.launcher.workspace.WorkspaceSourceIds
import com.riffle.core.domain.launcher.workspace.dock.PreviewDock
import com.riffle.core.domain.launcher.workspace.dock.PreviewDockModel
import com.riffle.core.domain.launcher.workspace.finderPage
import com.riffle.core.domain.launcher.workspace.menu.WorkspaceMenuAction
import com.riffle.core.domain.launcher.workspace.pagerPages
import com.riffle.core.domain.launcher.workspace.pool.PoolHomeView

internal const val WORKSPACE_PREVIEW_TEST_TAG = "workspace-preview"
internal const val WORKSPACE_PREVIEW_EXIT_TEST_TAG = "workspace-preview-exit"
internal const val WORKSPACE_PREVIEW_HOME_PLACEHOLDER_TEST_TAG = "workspace-preview-home-placeholder"
internal const val WORKSPACE_PREVIEW_DOCK_TEST_TAG = "workspace-preview-dock"
internal const val WORKSPACE_PREVIEW_FINDER_TEST_TAG = "workspace-preview-finder"
internal const val WORKSPACE_PREVIEW_FINDER_CLOSE_TEST_TAG = "workspace-preview-finder-close"
internal const val WORKSPACE_PREVIEW_REIMPORT_TEST_TAG = "workspace-preview-reimport"

private val PreviewBarHeight: Dp = 56.dp
private val FinderBarHeight: Dp = 48.dp

/**
 * The Workspaces (preview) screen: a platform pager over the workspace's pages, a read-only dock (the pinned
 * items from [dockPins] and the workspace's dynamic section; a marked placeholder while there is no pool), the
 * workspace menu and an always present Exit action. System Back also exits (the menu, when open, closes first).
 *
 * The pager swipes through the workspace's pages except the Finder, which opens as its own surface over the
 * pager (from the menu's Finder entry, or as the start page) and closes with its Close button or Back. The page
 * shown on opening, on coming back from an app and on a Home press follows [returnBehavior] (see
 * [PreviewPositions]); [returnRequest] is raised by the activity and consumed once.
 *
 * [servicesFor] builds the container services for the content padding this surface computes from the window
 * insets, so expressions never draw under the bars. [workspace] is null while workspaces are still loading.
 * Pages that reference `home.grid` draw the user's real placed items read-only through [placedHome] (the pool),
 * or an honest placeholder when there is no [placedHome] or the page is not a single bound home page.
 */
@Suppress("LongParameterList")
@Composable
internal fun WorkspacePreviewSurface(
    workspace: Workspace?,
    servicesFor: (PaddingValues) -> ContainerServices,
    menu: WorkspaceMenuHost?,
    reducedMotion: Boolean,
    navigation: PreviewNavigation?,
    onNavigationConsumed: (PreviewNavigation) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    returnBehavior: ReturnBehavior = ReturnBehavior.RESTORE,
    returnRequest: ReturnRequest? = null,
    onReturnConsumed: (ReturnRequest) -> Unit = {},
    placedHome: PlacedHomeContent? = null,
    dockPins: PreviewDockModel? = null,
) {
    BackHandler(onBack = onExit)
    val dockBarHeight = (dockPins?.barHeightDp ?: PreviewDock.MIN_BAR_HEIGHT_DP).dp
    val direction = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val top = insets.calculateTopPadding() + PreviewBarHeight
    val bottom = insets.calculateBottomPadding() + dockBarHeight
    val start = insets.calculateStartPadding(direction)
    val end = insets.calculateEndPadding(direction)
    val services =
        remember(servicesFor, top, bottom, start, end) { servicesFor(PaddingValues(start, top, end, bottom)) }
    Surface(
        modifier = modifier.fillMaxSize().testTag(WORKSPACE_PREVIEW_TEST_TAG),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (workspace == null) {
                Text(
                    text = WorkspacePreviewText.LOADING,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                key(workspace.id) {
                    WorkspaceStage(
                        workspace = workspace,
                        services = services,
                        reducedMotion = reducedMotion,
                        returnBehavior = returnBehavior,
                        navigation = navigation,
                        onNavigationConsumed = onNavigationConsumed,
                        returnRequest = returnRequest,
                        onReturnConsumed = onReturnConsumed,
                        placedHome = placedHome,
                    )
                }
                DockBar(
                    dock = workspace.dock,
                    services = services,
                    pins = dockPins,
                    home = placedHome,
                    height = dockBarHeight,
                    onOpenMenu = menu?.let { host -> { host.onAction(WorkspaceMenuAction.Open) } },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            PreviewTopBar(workspace?.name, onExit, placedHome?.onReimport, Modifier.align(Alignment.TopCenter))
            if (menu != null) {
                WorkspaceMenuLayer(
                    host = menu,
                    dockEdge = DockPosition.BOTTOM,
                    dockExtent = dockBarHeight,
                    reducedMotion = reducedMotion,
                )
            }
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun WorkspaceStage(
    workspace: Workspace,
    services: ContainerServices,
    reducedMotion: Boolean,
    returnBehavior: ReturnBehavior,
    navigation: PreviewNavigation?,
    onNavigationConsumed: (PreviewNavigation) -> Unit,
    returnRequest: ReturnRequest?,
    onReturnConsumed: (ReturnRequest) -> Unit,
    placedHome: PlacedHomeContent?,
) {
    var position by remember { mutableStateOf(PreviewPositions.initial(workspace, returnBehavior)) }
    val behavior by rememberUpdatedState(returnBehavior)
    val pageCount by rememberUpdatedState(workspace.pagerPages.size)
    val initialPage = position.page?.let(workspace::pagerIndexOf)?.coerceAtLeast(0) ?: 0
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pageCount })
    LaunchedEffect(pagerState, workspace) {
        snapshotFlow { pagerState.settledPage }.collect { index ->
            workspace.pagerPages.getOrNull(index)?.let { page ->
                position = PreviewPositions.settled(position, workspace, page.id)
            }
        }
    }
    LaunchedEffect(navigation, workspace) {
        if (navigation is PreviewNavigation.ToPage) {
            position = PreviewPositions.navigated(position, workspace, navigation.containerId)
            onNavigationConsumed(navigation)
        }
    }
    LaunchedEffect(returnRequest, workspace) {
        if (returnRequest != null) {
            position = PreviewPositions.returned(position, workspace, behavior, returnRequest)
            onReturnConsumed(returnRequest)
        }
    }
    LaunchedEffect(position.page, workspace) {
        val index = position.page?.let(workspace::pagerIndexOf) ?: -1
        if (index >= 0 && index != pagerState.currentPage) {
            if (reducedMotion) {
                pagerState.scrollToPage(index)
            } else {
                pagerState.animateScrollToPage(index, animationSpec = RiffleMotion.smooth<Float>(false))
            }
        }
    }
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        key = { index -> workspace.pagerPages.getOrNull(index)?.id?.value ?: index },
    ) { index ->
        workspace.pagerPages.getOrNull(index)?.let { page -> PreviewPage(page, services, placedHome) }
    }
    val finder = workspace.finderPage
    if (position.finderOpen && finder != null) {
        BackHandler { position = PreviewPositions.finderClosed(position) }
        FinderSurface(finder, services) { position = PreviewPositions.finderClosed(position) }
    }
}

/**
 * The Finder as its own surface over the pager: it is not a swipe position. A bar with a 48dp Close action sits
 * under the preview bar; the Finder's content fills the rest.
 */
@Composable
private fun FinderSurface(
    finder: PageContainer,
    services: ContainerServices,
    onClose: () -> Unit,
) {
    val direction = LocalLayoutDirection.current
    val padding = services.environment.contentPadding
    val contentServices =
        remember(services, padding, direction) {
            val below =
                PaddingValues(
                    start = padding.calculateStartPadding(direction),
                    top = 0.dp,
                    end = padding.calculateEndPadding(direction),
                    bottom = padding.calculateBottomPadding(),
                )
            services.copy(environment = services.environment.copy(contentPadding = below))
        }
    Surface(
        modifier = Modifier.fillMaxSize().testTag(WORKSPACE_PREVIEW_FINDER_TEST_TAG),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = padding.calculateStartPadding(direction) + RiffleSpacing.m,
                            end = padding.calculateEndPadding(direction) + RiffleSpacing.m,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = WorkspacePreviewText.FINDER_TITLE,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                TextButton(
                    onClick = onClose,
                    modifier =
                        Modifier
                            .heightIn(min = FinderBarHeight)
                            .testTag(WORKSPACE_PREVIEW_FINDER_CLOSE_TEST_TAG),
                ) {
                    Text(WorkspacePreviewText.CLOSE_FINDER)
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                PageContainerHost(finder, contentServices, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun PreviewPage(
    page: PageHost,
    services: ContainerServices,
    placedHome: PlacedHomeContent?,
) {
    when {
        placedHome != null && PoolHomeView.isPlacedHomePage(page) ->
            PoolHomePage(placedHome.resolve(page), placedHome, services.environment.contentPadding)
        page.referencesHomeGrid() -> HomeGridPlaceholder(services.environment.contentPadding)
        page is PageSetContainer -> PageSetContainerHost(page, services, Modifier.fillMaxSize())
        page is PageContainer -> PageContainerHost(page, services, Modifier.fillMaxSize())
    }
}

/** A page whose items live in `HomeLayout`: no adapter feeds `home.grid` into lenses yet. */
@Composable
private fun HomeGridPlaceholder(contentPadding: PaddingValues) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(RiffleSpacing.l)
                .testTag(WORKSPACE_PREVIEW_HOME_PLACEHOLDER_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = WorkspacePreviewText.HOME_GRID_TITLE,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = WorkspacePreviewText.HOME_GRID_BODY,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PreviewTopBar(
    workspaceName: String?,
    onExit: () -> Unit,
    onReimport: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val barInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier =
                Modifier
                    .windowInsetsPadding(barInsets)
                    .heightIn(min = PreviewBarHeight)
                    .padding(horizontal = RiffleSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
        ) {
            Text(
                text = listOfNotNull(WorkspacePreviewText.TITLE, workspaceName).joinToString(" · "),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onReimport != null) {
                TextButton(onClick = onReimport, modifier = Modifier.testTag(WORKSPACE_PREVIEW_REIMPORT_TEST_TAG)) {
                    Text(WorkspacePreviewText.REIMPORT)
                }
            }
            TextButton(onClick = onExit, modifier = Modifier.testTag(WORKSPACE_PREVIEW_EXIT_TEST_TAG)) {
                Text(WorkspacePreviewText.EXIT)
            }
        }
    }
}

/**
 * The preview dock: the pinned items and the workspace's dynamic section when both [pins] and [home] are present
 * (the pool is wired), else the marked placeholder (or just the dynamic section) as before. It carries the
 * "Workspace menu" accessibility action when the menu is on; the visible handle stays in the menu layer.
 */
@Composable
private fun DockBar(
    dock: WorkspaceDock,
    services: ContainerServices,
    pins: PreviewDockModel?,
    home: PlacedHomeContent?,
    height: Dp,
    onOpenMenu: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val dockServices =
        remember(services) { services.copy(environment = services.environment.copy(contentPadding = PaddingValues())) }
    val barInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(WORKSPACE_PREVIEW_DOCK_TEST_TAG)
                .then(workspaceMenuActionModifier(onOpenMenu)),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box(
            modifier = Modifier.windowInsetsPadding(barInsets).height(height),
            contentAlignment = Alignment.Center,
        ) {
            val dynamicSection = dock.dynamicSection
            if (pins != null && home != null) {
                PreviewDockContent(pins, home, dynamicSection, dockServices)
            } else if (dynamicSection == null) {
                Text(
                    text = WorkspacePreviewText.DOCK_PLACEHOLDER,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = RiffleSpacing.l),
                )
            } else {
                LensBoundExpression(dynamicSection, dockServices, Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * Whether this page's items come from `home.grid`, which has no source adapter yet: a page or page-set bound
 * to it, or a widget grid made only of such widgets.
 */
internal fun PageHost.referencesHomeGrid(): Boolean =
    when (this) {
        is PageSetContainer -> WorkspaceSourceIds.HOME_GRID in binding.lens.sources
        is PageContainer ->
            when (val body = content) {
                is PageContent.Bound -> WorkspaceSourceIds.HOME_GRID in body.binding.lens.sources
                is PageContent.WidgetGrid ->
                    body.placements.isNotEmpty() &&
                        body.placements.all { WorkspaceSourceIds.HOME_GRID in it.widget.binding.lens.sources }
            }
    }
