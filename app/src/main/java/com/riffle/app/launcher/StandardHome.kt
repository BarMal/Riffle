@file:Suppress("ComplexCondition", "CyclomaticComplexMethod", "LongMethod", "LongParameterList", "TooManyFunctions")

package com.riffle.app.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.app.launcher.widgets.HomeWidgetViewFactory
import com.riffle.core.domain.launcher.WidgetProviderCatalogStatus
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeEditMode
import com.riffle.core.domain.launcher.home.HomeLabelSettings
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.DockGestureSettings
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import com.riffle.core.domain.launcher.settings.MotionPerformanceTargetFps
import com.riffle.core.domain.launcher.settings.TimeScapeAppearanceSettings
import com.riffle.core.domain.launcher.settings.homeSystemBars
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun StandardHome(
    layout: HomeLayout,
    installedApps: List<InstalledApp>,
    interactions: StandardHomeInteractions,
    presentation: StandardHomePresentation,
    appIconLoader: AppIconLoader,
    widgetPreviewImageLoader: WidgetPreviewImageLoader = EmptyWidgetPreviewImageLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    val visibleLayout = layout.visibleTo(installedApps)
    val openedFolderId = remember { mutableStateOf<LauncherItemId?>(null) }
    val homeDragSession = remember { mutableStateOf<HomeDragSession?>(null) }
    val widgetPickerDragInProgress = remember { mutableStateOf(false) }
    val widgetPickerDragPreview = remember { mutableStateOf<WidgetPickerDragPlacementPreview?>(null) }
    val widgetPickerDockPreview = remember { mutableStateOf<WidgetPickerDockPlacementPreview?>(null) }
    val latestWidgetPickerDrag = remember { mutableStateOf<WidgetPickerDragSnapshot?>(null) }
    val accessibleWidgetPlacement = remember { mutableStateOf<WidgetPickerAccessiblePlacement?>(null) }
    val activeWidgetPickerEdgeHoverSide = remember { mutableStateOf<WidgetPickerEdgeHoverSide?>(null) }
    val widgetPickerDragWorkspaceBounds = remember { mutableStateOf<Rect?>(null) }
    val workspaceGridBounds = remember { mutableStateOf<Rect?>(null) }
    val dockBounds = remember { mutableStateOf<Rect?>(null) }
    val density = LocalDensity.current.density
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val finishWidgetPickerDrag: (Boolean) -> Unit = { restorePicker ->
        val wasInProgress = widgetPickerDragInProgress.value
        widgetPickerDragInProgress.value = false
        widgetPickerDragPreview.value = null
        widgetPickerDockPreview.value = null
        latestWidgetPickerDrag.value = null
        activeWidgetPickerEdgeHoverSide.value = null
        widgetPickerDragWorkspaceBounds.value = null
        if (restorePicker && wasInProgress) {
            onAction(LauncherShellAction.OpenWidgetPicker)
        }
    }
    val cancelAccessibleWidgetPlacement = {
        val placement = accessibleWidgetPlacement.value
        accessibleWidgetPlacement.value = null
        placement
            ?.let { value ->
                accessibleWidgetPlacementCancellationActionFor(
                    placement = value,
                    selectedPageId = visibleLayout.selectedPageId,
                )
            }?.let(onAction)
        Unit
    }
    BackHandler(enabled = widgetPickerDragInProgress.value || accessibleWidgetPlacement.value != null) {
        if (widgetPickerDragInProgress.value) {
            finishWidgetPickerDrag(true)
        } else {
            cancelAccessibleWidgetPlacement()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestFinishWidgetPickerDrag = rememberUpdatedState(finishWidgetPickerDrag)
    val latestCancelAccessibleWidgetPlacement = rememberUpdatedState(cancelAccessibleWidgetPlacement)
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    latestFinishWidgetPickerDrag.value(true)
                    latestCancelAccessibleWidgetPlacement.value()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(workspaceGridBounds.value) {
        val startingBounds = widgetPickerDragWorkspaceBounds.value
        if (
            widgetPickerDragInProgress.value &&
            startingBounds != null &&
            workspaceGridBounds.value != startingBounds
        ) {
            finishWidgetPickerDrag(true)
        }
    }
    LaunchedEffect(
        latestWidgetPickerDrag.value,
        visibleLayout.selectedPage,
        workspaceGridBounds.value,
        dockBounds.value,
        density,
    ) {
        widgetPickerDragPreview.value =
            latestWidgetPickerDrag.value?.let { snapshot ->
                widgetPickerDragPlacementPreviewFor(
                    snapshot = snapshot,
                    page = visibleLayout.selectedPage,
                    workspaceBounds = workspaceGridBounds.value,
                    dockBounds = dockBounds.value,
                    density = density,
                )
            }
    }
    LaunchedEffect(activeWidgetPickerEdgeHoverSide.value, visibleLayout.selectedPageId) {
        val side = activeWidgetPickerEdgeHoverSide.value ?: return@LaunchedEffect
        delay(WIDGET_PICKER_EDGE_HOVER_DELAY_MILLIS)
        val targetPageId =
            widgetPickerEdgeHoverPageId(
                side = side,
                pages = visibleLayout.pages,
                selectedPageId = visibleLayout.selectedPageId,
                isRtl = isRtl,
            )
        if (widgetPickerDragInProgress.value && activeWidgetPickerEdgeHoverSide.value == side && targetPageId != null) {
            onAction(LauncherShellAction.SelectHomePage(targetPageId))
        } else if (targetPageId == null) {
            activeWidgetPickerEdgeHoverSide.value = null
        }
    }
    val actions =
        HomeWorkspaceActions(
            onFolderOpen = { folder -> openedFolderId.value = folder.id },
            onDragSessionChanged = { session -> homeDragSession.value = session },
            currentDragSession = { homeDragSession.value },
            haptics = interactions.haptics,
            onDockInteractionHeightChanged = interactions.onDockInteractionHeightChanged,
            onWorkspaceGridBoundsChanged = { pageId, bounds ->
                if (pageId == visibleLayout.selectedPageId) {
                    workspaceGridBounds.value = bounds
                }
            },
            onDockBoundsChanged = { bounds -> dockBounds.value = bounds },
            onBackgroundClick = {},
            onAction = onAction,
        )

    StandardHomeColumn(
        state =
            StandardHomeContentState(
                layout = layout,
                visibleLayout = visibleLayout,
                dragSession = homeDragSession.value,
                widgetPickerDragPreview = widgetPickerDragPreview.value,
                widgetPickerDockPreview = widgetPickerDockPreview.value,
                presentation = presentation,
            ),
        appIconLoader = appIconLoader,
        actions = actions,
    )
    if (presentation.widgetPicker.isOpen || widgetPickerDragInProgress.value) {
        WidgetPickerSurface(
            providers = presentation.widgetPicker.providers,
            profileContentVisibility = presentation.widgetPicker.profileContentVisibility,
            catalogStatus = presentation.widgetPicker.catalogStatus,
            previewImageLoader = widgetPreviewImageLoader,
            accessiblePlacement = accessibleWidgetPlacement.value,
            isDragHandoffActive = widgetPickerDragInProgress.value,
            onWidgetDragStarted = {
                cancelAccessibleWidgetPlacement()
                widgetPickerDragInProgress.value = true
                widgetPickerDragPreview.value = null
                widgetPickerDockPreview.value = null
                latestWidgetPickerDrag.value = null
                activeWidgetPickerEdgeHoverSide.value = null
                widgetPickerDragWorkspaceBounds.value = workspaceGridBounds.value
                onAction(LauncherShellAction.CloseWidgetPicker)
            },
            onWidgetDragMoved = { provider, position, rootSize ->
                val bounds = workspaceGridBounds.value
                val snapshot =
                    WidgetPickerDragSnapshot(
                        provider = provider,
                        position = position,
                        rootSize = rootSize,
                    )
                latestWidgetPickerDrag.value = snapshot
                activeWidgetPickerEdgeHoverSide.value =
                    bounds?.let { workspaceBounds ->
                        widgetPickerEdgeHoverSide(
                            position = position,
                            workspaceBounds = workspaceBounds,
                            edgeZonePx = WIDGET_PICKER_EDGE_HOVER_ZONE_DP * density,
                        )
                    }
                widgetPickerDragPreview.value =
                    widgetPickerDragPlacementPreviewFor(
                        snapshot = snapshot,
                        page = visibleLayout.selectedPage,
                        workspaceBounds = bounds,
                        dockBounds = dockBounds.value,
                        density = density,
                    )
                widgetPickerDockPreview.value =
                    widgetPickerDockPlacementPreviewFor(
                        snapshot = snapshot,
                        dock = visibleLayout.dock,
                        dockBounds = dockBounds.value,
                        isRtl = isRtl,
                    )
            },
            onWidgetDragCancelled = {
                finishWidgetPickerDrag(true)
            },
            onAccessiblePlacementRequested = { provider, target ->
                accessibleWidgetPlacement.value =
                    accessibleWidgetPlacementFor(
                        provider = provider,
                        target = target,
                        pages = visibleLayout.pages,
                        selectedPageId = visibleLayout.selectedPageId,
                        initialPageId =
                            accessibleWidgetPlacement.value?.initialPageId
                                ?: visibleLayout.selectedPageId,
                        dockItemCount = visibleLayout.dock.items.size,
                        availableWidthDp = workspaceGridBounds.value?.width?.div(density)?.roundToInt() ?: 0,
                        availableHeightDp = workspaceGridBounds.value?.height?.div(density)?.roundToInt() ?: 0,
                    )
            },
            onAccessiblePlacementSelected = { placement ->
                accessibleWidgetPlacement.value = placement
                placement.selectedCandidate?.pageId?.let { pageId ->
                    if (pageId != visibleLayout.selectedPageId) {
                        onAction(LauncherShellAction.SelectHomePage(pageId))
                    }
                }
            },
            onAccessiblePlacementConfirmed = {
                accessibleWidgetPlacement.value?.takeIf { placement -> placement.isValid }?.let { placement ->
                    accessibleWidgetAddActionFor(placement)?.let { action ->
                        onAction(action)
                        accessibleWidgetPlacement.value = null
                        onAction(LauncherShellAction.CloseWidgetPicker)
                    }
                }
            },
            onAccessiblePlacementCancelled = cancelAccessibleWidgetPlacement,
            onCloseRequested = {
                cancelAccessibleWidgetPlacement()
                onAction(LauncherShellAction.CloseWidgetPicker)
            },
            onRetryRequested = { onAction(LauncherShellAction.OpenWidgetPicker) },
            onWidgetDropped = { provider, position, rootSize ->
                val selectedPage = visibleLayout.selectedPage
                val bounds = workspaceGridBounds.value
                val snapshot =
                    WidgetPickerDragSnapshot(
                        provider = provider,
                        position = position,
                        rootSize = rootSize,
                    )
                val target =
                    bounds?.let {
                        widgetPickerDropTarget(position, workspaceBounds = it, dockBounds = dockBounds.value)
                    }
                val preview =
                    widgetPickerDragPlacementPreviewFor(
                        snapshot = snapshot,
                        page = selectedPage,
                        workspaceBounds = bounds,
                        dockBounds = dockBounds.value,
                        density = density,
                    )
                val dockPreview =
                    widgetPickerDockPlacementPreviewFor(
                        snapshot = snapshot,
                        dock = visibleLayout.dock,
                        dockBounds = dockBounds.value,
                        isRtl = isRtl,
                    )
                val isValidDrop =
                    widgetPickerDropIsValid(
                        target = target,
                        homePreview = preview,
                        dockPreview = dockPreview,
                    )
                if (isValidDrop && target != null) {
                    onAction(
                        LauncherShellAction.RequestAddWidget(
                            provider = provider.identity,
                            label = provider.label,
                            dimensions = provider.dimensions,
                            target = target,
                            targetPageId = selectedPage.id.takeIf { target == WidgetAddTarget.HOME },
                            targetCell = preview?.cell.takeIf { target == WidgetAddTarget.HOME },
                            dockIndex = dockPreview?.dockIndex.takeIf { target == WidgetAddTarget.DOCK },
                        ),
                    )
                }
                finishWidgetPickerDrag(!isValidDrop)
            },
            onAction = onAction,
        )
    }
    visibleLayout.openedFolder(openedFolderId.value)?.let { folder ->
        FolderSurface(
            folder = folder,
            layout = visibleLayout,
            installedApps = installedApps,
            appIconLoader = appIconLoader,
            onDismiss = { openedFolderId.value = null },
            onAction = onAction,
        )
    }
}

@Suppress("MaxLineLength")
internal fun accessibleWidgetAddActionFor(placement: WidgetPickerAccessiblePlacement): LauncherShellAction.RequestAddWidget? {
    val candidate = placement.selectedCandidate ?: return null
    return LauncherShellAction.RequestAddWidget(
        provider = placement.provider.identity,
        label = placement.provider.label,
        dimensions = placement.provider.dimensions,
        supportsHorizontalResize = placement.provider.supportsHorizontalResize,
        supportsVerticalResize = placement.provider.supportsVerticalResize,
        target = placement.target,
        targetPageId = candidate.pageId,
        targetCell = candidate.cell,
        dockIndex = candidate.dockIndex,
    )
}

internal fun accessibleWidgetPlacementCancellationActionFor(
    placement: WidgetPickerAccessiblePlacement,
    selectedPageId: LauncherPageId,
): LauncherShellAction.SelectHomePage? =
    placement.initialPageId
        .takeIf { initialPageId -> initialPageId != selectedPageId }
        ?.let(LauncherShellAction::SelectHomePage)

@Suppress("LongParameterList")
internal fun accessibleWidgetPlacementFor(
    provider: InstalledWidgetProvider,
    target: WidgetAddTarget,
    pages: List<LauncherPage>,
    selectedPageId: LauncherPageId,
    initialPageId: LauncherPageId = selectedPageId,
    dockItemCount: Int = 0,
    availableWidthDp: Int,
    availableHeightDp: Int,
): WidgetPickerAccessiblePlacement {
    if (target == WidgetAddTarget.DOCK) {
        return WidgetPickerAccessiblePlacement(
            provider = provider,
            target = target,
            initialPageId = initialPageId,
            candidates =
                (0..dockItemCount).map { index ->
                    WidgetPickerPlacementCandidate(dockIndex = index)
                },
        )
    }

    val orderedPages =
        pages.sortedBy { page ->
            if (page.id == selectedPageId) 0 else 1
        }
    val candidates =
        orderedPages.flatMap { page ->
            (0 until page.grid.rows.coerceAtLeast(0)).flatMap { row ->
                (0 until page.grid.columns.coerceAtLeast(0)).mapNotNull { column ->
                    widgetPickerDragPlacementPreviewFor(
                        page = page,
                        provider = provider,
                        cell = GridCell(column = column, row = row),
                        availableWidthDp = availableWidthDp,
                        availableHeightDp = availableHeightDp,
                    ).takeIf { preview -> preview.isValid }?.let { preview ->
                        WidgetPickerPlacementCandidate(
                            pageId = page.id,
                            cell = preview.cell,
                            span = preview.span,
                        )
                    }
                }
            }
        }
    return WidgetPickerAccessiblePlacement(
        provider = provider,
        target = target,
        initialPageId = initialPageId,
        candidates = candidates,
    )
}

@Suppress("LongMethod")
@Composable
private fun StandardHomeColumn(
    state: StandardHomeContentState,
    appIconLoader: AppIconLoader,
    actions: HomeWorkspaceActions,
) {
    val pagerState =
        rememberImmediateHomePagerState(
            layout = state.visibleLayout,
            reducedMotion = state.presentation.reducedMotion,
            actions = actions,
        )
    val notificationShelfState =
        dockNotificationShelfState(
            showNotificationCards = state.visibleLayout.dock.showNotificationCards,
            groups = state.presentation.notificationGroupsByApp,
            notificationAccessStatus = state.presentation.notificationAccessStatus,
            apps = state.presentation.installedApps,
        )
    val dockShelf = rememberDockShelfController(state.visibleLayout, notificationShelfState)
    val homeActions =
        actions.copy(
            onBackgroundClick = dockShelf.dismiss,
        )
    val margins = state.visibleLayout.settings.grid.margin.centered()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .homeGestureInput(
                    enabled = state.visibleLayout.editMode == HomeEditMode.Browsing,
                    settings = state.presentation.homeGestures,
                    onAction = actions.onAction,
                )
                .windowInsetsPadding(state.presentation.homeInsetPolicy.safeDrawingInsets()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ImmediateWorkspacePager(
            layout = state.visibleLayout,
            pagerState = pagerState,
            gridState =
                HomeGridState(
                    isEditing = state.visibleLayout.editMode is HomeEditMode.EditingPage,
                    pageCount = state.visibleLayout.pages.size,
                    selectedPageIndex = state.visibleLayout.selectedPageIndex,
                    dragSession = state.dragSession,
                    widgetPickerDragPreview = state.widgetPickerDragPreview,
                ),
            presentation = state.homeGridPresentation(actions),
            appIconLoader = appIconLoader,
            actions = homeActions,
            activeDragSession = state.dragSession,
            onDragPageTargetChanged = { pageId ->
                state.dragSession?.let { session ->
                    if (session.targetPageId != pageId) {
                        actions.onDragSessionChanged(session.copy(targetPageId = pageId))
                        actions.onAction(LauncherShellAction.SelectHomePage(pageId))
                    }
                }
            },
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(
                        start = margins.start.coerceAtLeast(0).dp,
                        top = margins.top.coerceAtLeast(0).dp,
                        end = margins.end.coerceAtLeast(0).dp,
                        bottom = margins.bottom.coerceAtLeast(0).dp,
                    ),
        )
        Spacer(modifier = Modifier.height(state.visibleLayout.dock.homeControlsSpacingDp.dp))
        HomeBottomControls(
            layout = state.visibleLayout,
            selectedPageIndex = pagerState.visualSelectedPageIndex,
            showPageIndicator = pagerState.rememberPageIndicatorVisible(),
            reducedMotion = state.presentation.reducedMotion,
            appIconLoader = appIconLoader,
            widgetViewFactory = state.presentation.widgetViewFactory,
            actions = homeActions,
        )
        StandardHomeDockArea(
            layout = state.visibleLayout,
            presentation = state.presentation,
            notificationShelfState = notificationShelfState,
            isDockShelfExpanded = dockShelf.isExpanded,
            onDockShelfExpandedChange = dockShelf.onExpandedChange,
            appIconLoader = appIconLoader,
            actions = actions,
            widgetPickerDockPreview = state.widgetPickerDockPreview,
        )
    }
}

@Composable
private fun rememberDockShelfController(
    layout: HomeLayout,
    notificationShelfState: DockNotificationShelfState,
): DockShelfController {
    val isExpanded = remember { mutableStateOf(false) }
    val hasContent =
        dockHasExpandedContent(
            hasOverflow = dockHasOverflow(capacity = layout.dock.capacity, itemCount = layout.dock.items.size),
            notificationShelfState = notificationShelfState,
        )

    LaunchedEffect(hasContent) {
        isExpanded.value =
            dockShelfExpandedStateForContent(
                isExpanded = isExpanded.value,
                hasContent = hasContent,
            )
    }

    return DockShelfController(
        isExpanded = isExpanded.value,
        dismiss = {
            isExpanded.value =
                dockShelfExpandedStateAfterBackgroundTap(isExpanded = isExpanded.value)
        },
        onExpandedChange = { expanded -> isExpanded.value = expanded },
    )
}

@Composable
private fun HomeBottomControls(
    layout: HomeLayout,
    selectedPageIndex: Int,
    showPageIndicator: Boolean,
    reducedMotion: Boolean,
    appIconLoader: AppIconLoader,
    widgetViewFactory: HomeWidgetViewFactory,
    actions: HomeWorkspaceActions,
) {
    AnimatedContent(
        targetState = layout.editMode,
        transitionSpec = {
            homePageOverviewMotionPolicy(reducedMotion).contentTransform(
                enteringOverview = targetState == HomeEditMode.ManagingPages,
                exitingOverview = initialState == HomeEditMode.ManagingPages,
            )
        },
        label = "home-page-overview",
    ) { editMode ->
        when (editMode) {
            HomeEditMode.Browsing ->
                HomeBottomSearchArea(
                    pageCount = layout.pages.size,
                    selectedPageIndex = selectedPageIndex,
                    showPageIndicator = showPageIndicator,
                    onPageSelected = { pageIndex ->
                        layout.pages.getOrNull(pageIndex)?.let { page ->
                            actions.onAction(LauncherShellAction.SelectHomePage(page.id))
                        }
                    },
                    actions = actions,
                )

            is HomeEditMode.EditingPage ->
                PageEditControls(
                    pageCount = layout.pages.size,
                    selectedPageIndex = layout.selectedPageIndex,
                    onAction = actions.onAction,
                )

            HomeEditMode.ManagingPages ->
                PageOverviewControls(
                    layout = layout,
                    reducedMotion = reducedMotion,
                    appIconLoader = appIconLoader,
                    widgetViewFactory = widgetViewFactory,
                    onAction = actions.onAction,
                )
        }
    }
}

@Composable
private fun HomeBottomSearchArea(
    pageCount: Int,
    selectedPageIndex: Int,
    showPageIndicator: Boolean,
    onPageSelected: (Int) -> Unit,
    actions: HomeWorkspaceActions,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(HOME_SEARCH_AREA_HEIGHT_DP.dp),
        contentAlignment = Alignment.Center,
    ) {
        HomeBackgroundContextMenu(
            haptics = actions.haptics,
            onAction = actions.onAction,
            modifier = Modifier.matchParentSize(),
            onClick = actions.onBackgroundClick,
        )
        if (showPageIndicator) {
            PageIndicator(
                pageCount = pageCount,
                selectedPageIndex = selectedPageIndex,
                onPageSelected = onPageSelected,
                modifier =
                    Modifier
                        .height(HOME_PAGE_INDICATOR_TOUCH_TARGET_HEIGHT_DP.dp)
                        .clip(RoundedCornerShape(HOME_SEARCH_PILL_HEIGHT_DP.dp))
                        .padding(horizontal = HOME_SEARCH_HORIZONTAL_PADDING_DP.dp),
            )
        } else {
            Surface(
                modifier =
                    Modifier
                        .height(HOME_SEARCH_PILL_HEIGHT_DP.dp)
                        .clip(RoundedCornerShape(HOME_SEARCH_PILL_HEIGHT_DP.dp))
                        .clickable(onClick = { actions.onAction(LauncherShellAction.OpenSearch) }),
                shape = RoundedCornerShape(HOME_SEARCH_PILL_HEIGHT_DP.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = HOME_SEARCH_SURFACE_ALPHA),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = HOME_SEARCH_BORDER_ALPHA)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .padding(horizontal = HOME_SEARCH_HORIZONTAL_PADDING_DP.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Search",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImmediateHomePagerState.rememberPageIndicatorVisible(): Boolean {
    val isVisible = remember { mutableStateOf(false) }

    LaunchedEffect(isPageGestureActive, visualSelectedPageIndex) {
        if (isPageGestureActive) {
            isVisible.value = true
        } else {
            delay(PAGE_INDICATOR_SETTLED_VISIBLE_MS)
            isVisible.value = false
        }
    }

    return isVisible.value
}

@Composable
fun BoxScope.RemoveShortcutButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer)
                .clickable(onClick = onClick)
                .semantics { contentDescription = "Remove $label" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "X",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

private data class StandardHomeContentState(
    val layout: HomeLayout,
    val visibleLayout: HomeLayout,
    val dragSession: HomeDragSession?,
    val widgetPickerDragPreview: WidgetPickerDragPlacementPreview?,
    val widgetPickerDockPreview: WidgetPickerDockPlacementPreview?,
    val presentation: StandardHomePresentation,
)

private data class DockShelfController(
    val isExpanded: Boolean,
    val dismiss: () -> Unit,
    val onExpandedChange: (Boolean) -> Unit,
)

private fun StandardHomeContentState.homeGridPresentation(actions: HomeWorkspaceActions): HomeGridPresentation =
    HomeGridPresentation(
        notificationGroupsByApp = presentation.notificationGroupsByApp,
        appShortcutsByApp = presentation.appShortcutsByApp,
        labelSettings = layout.settings.labels,
        reducedMotion = presentation.reducedMotion,
        widgetViewFactory = presentation.widgetViewFactory,
        generatedPage =
            GeneratedPagePresentation(
                notificationGroupsByApp = presentation.notificationGroupsByApp,
                notificationAccessStatus = presentation.notificationAccessStatus,
                installedApps = presentation.installedApps,
                onAction = actions.onAction,
                timeScapeAppearance = presentation.timeScapeAppearance,
            ),
    )

internal data class HomeDragSession(
    val item: LauncherItem,
    val originPageId: com.riffle.core.domain.launcher.home.LauncherPageId =
        com.riffle.core.domain.launcher.home.LauncherPageId("home"),
    val originCell: GridCell,
    val targetPageId: com.riffle.core.domain.launcher.home.LauncherPageId = originPageId,
    val dragOffsetX: Float = 0f,
    val dragOffsetY: Float = 0f,
    val projectedCell: GridCell,
)

internal data class StandardHomeInteractions(
    val haptics: LauncherHaptics = NoopLauncherHaptics,
    val onDockInteractionHeightChanged: (Int) -> Unit = {},
)

internal data class StandardHomePresentation(
    val notificationGroupsByApp: List<AppNotificationGroup> = emptyList(),
    val notificationAccessStatus: NotificationAccessStatus = NotificationAccessStatus.UNKNOWN,
    val installedApps: List<InstalledApp> = emptyList(),
    val appShortcutsByApp: AppShortcutsByApp,
    val homeGestures: HomeGestureSettings = HomeGestureSettings(),
    val dockGestures: DockGestureSettings = DockGestureSettings(),
    val reducedMotion: Boolean = false,
    val motionPerformanceTargetFps: MotionPerformanceTargetFps = MotionPerformanceTargetFps.FPS_120,
    val widgetViewFactory: HomeWidgetViewFactory = EmptyHomeWidgetViewFactory,
    val widgetPicker: StandardHomeWidgetPickerState = StandardHomeWidgetPickerState(),
    val homeInsetPolicy: HomeInsetPolicy = HomeInsetPolicy(),
    val timeScapeAppearance: TimeScapeAppearanceSettings = TimeScapeAppearanceSettings.modern(),
)

internal data class StandardHomeWidgetPickerState(
    val providers: List<InstalledWidgetProvider> = emptyList(),
    val profileContentVisibility: Map<AppProfileId, AppProfileContentVisibility> = emptyMap(),
    val catalogStatus: WidgetProviderCatalogStatus = WidgetProviderCatalogStatus.READY,
    val isOpen: Boolean = false,
)

internal data class HomeInsetPolicy(
    val reserveStatusBar: Boolean = true,
    val reserveNavigationBar: Boolean = true,
)

internal fun homeInsetPolicy(appearance: AppearanceSettings): HomeInsetPolicy =
    HomeInsetPolicy(
        reserveStatusBar = !appearance.homeSystemBars.statusBarHidden,
        reserveNavigationBar = !appearance.homeSystemBars.navigationBarHidden,
    )

@Composable
private fun HomeInsetPolicy.safeDrawingInsets(): WindowInsets {
    var insets = WindowInsets.safeDrawing
    if (!reserveStatusBar) {
        insets = insets.exclude(WindowInsets.statusBars)
    }
    if (!reserveNavigationBar) {
        insets = insets.exclude(WindowInsets.navigationBars)
    }
    return insets
}

internal data class HomeGridPresentation(
    val notificationGroupsByApp: List<AppNotificationGroup>,
    val appShortcutsByApp: AppShortcutsByApp,
    val labelSettings: HomeLabelSettings,
    val reducedMotion: Boolean = false,
    val widgetViewFactory: HomeWidgetViewFactory,
    val generatedPage: GeneratedPagePresentation = GeneratedPagePresentation(),
)

internal data class GeneratedPagePresentation(
    val notificationGroupsByApp: List<AppNotificationGroup> = emptyList(),
    val notificationAccessStatus: NotificationAccessStatus = NotificationAccessStatus.UNKNOWN,
    val installedApps: List<InstalledApp> = emptyList(),
    val onAction: (LauncherShellAction) -> Unit = {},
    val timeScapeAppearance: TimeScapeAppearanceSettings = TimeScapeAppearanceSettings.modern(),
)

internal data class HomeItemDragState(
    val pageId: com.riffle.core.domain.launcher.home.LauncherPageId,
    val cell: GridCell,
    val cellSizePx: Float,
    val grid: GridDimensions,
    val pageItems: List<LauncherItem>,
)

internal data class HomeWorkspaceActions(
    val onFolderOpen: (FolderItem) -> Unit,
    val onDragSessionChanged: (HomeDragSession?) -> Unit,
    val currentDragSession: () -> HomeDragSession? = { null },
    val haptics: LauncherHaptics,
    val onDockInteractionHeightChanged: (Int) -> Unit = {},
    val onWorkspaceGridBoundsChanged: (LauncherPageId, Rect) -> Unit = { _, _ -> },
    val onDockBoundsChanged: (Rect) -> Unit = {},
    val onBackgroundClick: () -> Unit = {},
    val onAction: (LauncherShellAction) -> Unit,
)

private const val HOME_SEARCH_AREA_HEIGHT_DP = 48
private const val HOME_SEARCH_PILL_HEIGHT_DP = 30
private const val HOME_PAGE_INDICATOR_TOUCH_TARGET_HEIGHT_DP = 48
private const val HOME_SEARCH_HORIZONTAL_PADDING_DP = 14
private const val HOME_SEARCH_SURFACE_ALPHA = 0.82f
private const val HOME_SEARCH_BORDER_ALPHA = 0.38f
private const val PAGE_INDICATOR_SETTLED_VISIBLE_MS = 250L
private const val WIDGET_PICKER_EDGE_HOVER_ZONE_DP = 40
private const val WIDGET_PICKER_EDGE_HOVER_DELAY_MILLIS = 650L

internal fun widgetPickerDropCell(
    position: Offset,
    gridBounds: Rect,
    grid: GridDimensions,
): GridCell =
    GridCell(
        column = (((position.x - gridBounds.left) / gridBounds.width.coerceAtLeast(1f)) * grid.columns).toInt(),
        row = (((position.y - gridBounds.top) / gridBounds.height.coerceAtLeast(1f)) * grid.rows).toInt(),
    ).let { cell ->
        GridCell(
            column = cell.column.coerceIn(0, grid.columns - 1),
            row = cell.row.coerceIn(0, grid.rows - 1),
        )
    }

internal fun widgetPickerDropTarget(
    position: Offset,
    workspaceBounds: Rect,
    dockBounds: Rect?,
): WidgetAddTarget? =
    when {
        workspaceBounds.contains(position) -> WidgetAddTarget.HOME
        dockBounds?.contains(position) == true -> WidgetAddTarget.DOCK
        else -> null
    }
