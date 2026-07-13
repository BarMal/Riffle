package com.riffle.app.launcher

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.app.launcher.widgets.HomeWidgetViewFactory
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
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import com.riffle.core.domain.launcher.settings.MotionPerformanceTargetFps
import com.riffle.core.domain.launcher.settings.homeSystemBars
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import kotlinx.coroutines.delay

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
    val actions =
        HomeWorkspaceActions(
            onFolderOpen = { folder -> openedFolderId.value = folder.id },
            onDragSessionChanged = { session -> homeDragSession.value = session },
            haptics = interactions.haptics,
            onBackgroundClick = {},
            onAction = onAction,
        )

    StandardHomeColumn(
        state =
            StandardHomeContentState(
                layout = layout,
                visibleLayout = visibleLayout,
                dragSession = homeDragSession.value,
                presentation = presentation,
            ),
        appIconLoader = appIconLoader,
        actions = actions,
    )
    if (presentation.widgetPicker.isOpen) {
        WidgetPickerSurface(
            providers = presentation.widgetPicker.providers,
            previewImageLoader = widgetPreviewImageLoader,
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
    val margins = state.visibleLayout.settings.grid.margin

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
                ),
            presentation = state.homeGridPresentation(),
            appIconLoader = appIconLoader,
            actions = homeActions,
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
        Spacer(modifier = Modifier.height(HOME_BOTTOM_CONTROLS_TOP_SPACING_DP.dp))
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
                modifier =
                    Modifier
                        .height(HOME_SEARCH_PILL_HEIGHT_DP.dp)
                        .clip(RoundedCornerShape(HOME_SEARCH_PILL_HEIGHT_DP.dp))
                        .semantics { contentDescription = "Search" }
                        .clickable(onClick = { actions.onAction(LauncherShellAction.OpenSearch) })
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
    val presentation: StandardHomePresentation,
)

private data class DockShelfController(
    val isExpanded: Boolean,
    val dismiss: () -> Unit,
    val onExpandedChange: (Boolean) -> Unit,
)

private fun StandardHomeContentState.homeGridPresentation(): HomeGridPresentation =
    HomeGridPresentation(
        notificationGroupsByApp = presentation.notificationGroupsByApp,
        appShortcutsByApp = presentation.appShortcutsByApp,
        labelSettings = layout.settings.labels,
        reducedMotion = presentation.reducedMotion,
        widgetViewFactory = presentation.widgetViewFactory,
    )

internal data class HomeDragSession(
    val item: LauncherItem,
    val originCell: GridCell,
    val dragOffsetX: Float = 0f,
    val dragOffsetY: Float = 0f,
    val projectedCell: GridCell,
)

internal data class StandardHomeInteractions(
    val haptics: LauncherHaptics = NoopLauncherHaptics,
)

internal data class StandardHomePresentation(
    val notificationGroupsByApp: List<AppNotificationGroup> = emptyList(),
    val notificationAccessStatus: NotificationAccessStatus = NotificationAccessStatus.UNKNOWN,
    val installedApps: List<InstalledApp> = emptyList(),
    val appShortcutsByApp: AppShortcutsByApp,
    val homeGestures: HomeGestureSettings = HomeGestureSettings(),
    val reducedMotion: Boolean = false,
    val motionPerformanceTargetFps: MotionPerformanceTargetFps = MotionPerformanceTargetFps.FPS_120,
    val widgetViewFactory: HomeWidgetViewFactory = EmptyHomeWidgetViewFactory,
    val widgetPicker: StandardHomeWidgetPickerState = StandardHomeWidgetPickerState(),
    val homeInsetPolicy: HomeInsetPolicy = HomeInsetPolicy(),
)

internal data class StandardHomeWidgetPickerState(
    val providers: List<InstalledWidgetProvider> = emptyList(),
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
)

internal data class HomeItemDragState(
    val cell: GridCell,
    val cellSizePx: Float,
    val grid: GridDimensions,
    val pageItems: List<LauncherItem>,
)

internal data class HomeWorkspaceActions(
    val onFolderOpen: (FolderItem) -> Unit,
    val onDragSessionChanged: (HomeDragSession?) -> Unit,
    val haptics: LauncherHaptics,
    val onBackgroundClick: () -> Unit = {},
    val onAction: (LauncherShellAction) -> Unit,
)

private const val HOME_BOTTOM_CONTROLS_TOP_SPACING_DP = 8
private const val HOME_SEARCH_AREA_HEIGHT_DP = 36
private const val HOME_SEARCH_PILL_HEIGHT_DP = 30
private const val HOME_SEARCH_HORIZONTAL_PADDING_DP = 14
private const val HOME_SEARCH_SURFACE_ALPHA = 0.82f
private const val HOME_SEARCH_BORDER_ALPHA = 0.38f
private const val PAGE_INDICATOR_SETTLED_VISIBLE_MS = 250L
