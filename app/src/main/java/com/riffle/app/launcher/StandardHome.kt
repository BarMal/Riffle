package com.riffle.app.launcher

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
import com.riffle.core.domain.launcher.apps.AppPackageName
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
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
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
    val isDockShelfExpanded = remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .homeGestureInput(
                    enabled = state.visibleLayout.editMode == HomeEditMode.Browsing,
                    settings = state.presentation.homeGestures,
                    onAction = actions.onAction,
                )
                .windowInsetsPadding(state.presentation.homeInsetPolicy.safeDrawingInsets())
                .padding(
                    horizontal = HOME_SURFACE_HORIZONTAL_PADDING_DP.dp,
                    vertical = HOME_SURFACE_VERTICAL_PADDING_DP.dp,
                ),
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
            actions = actions,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(HOME_BOTTOM_CONTROLS_TOP_SPACING_DP.dp))
        HomeBottomControls(
            layout = state.visibleLayout,
            selectedPageIndex = pagerState.visualSelectedPageIndex,
            showPageIndicator = pagerState.rememberPageIndicatorVisible(),
            appIconLoader = appIconLoader,
            widgetViewFactory = state.presentation.widgetViewFactory,
            haptics = actions.haptics,
            onAction = actions.onAction,
        )
        StandardHomeDockArea(
            layout = state.visibleLayout,
            presentation = state.presentation,
            isDockShelfExpanded = isDockShelfExpanded.value,
            onDockShelfExpandedChange = { expanded -> isDockShelfExpanded.value = expanded },
            appIconLoader = appIconLoader,
            actions = actions,
        )
    }
}

@Composable
private fun HomeBottomControls(
    layout: HomeLayout,
    selectedPageIndex: Int,
    showPageIndicator: Boolean,
    appIconLoader: AppIconLoader,
    widgetViewFactory: HomeWidgetViewFactory,
    haptics: LauncherHaptics,
    onAction: (LauncherShellAction) -> Unit,
) {
    when (layout.editMode) {
        HomeEditMode.Browsing ->
            HomeBottomSearchArea(
                pageCount = layout.pages.size,
                selectedPageIndex = selectedPageIndex,
                showPageIndicator = showPageIndicator,
                haptics = haptics,
                onAction = onAction,
            )

        is HomeEditMode.EditingPage ->
            PageEditControls(
                pageCount = layout.pages.size,
                selectedPageIndex = layout.selectedPageIndex,
                onAction = onAction,
            )

        HomeEditMode.ManagingPages ->
            PageOverviewControls(
                layout = layout,
                appIconLoader = appIconLoader,
                widgetViewFactory = widgetViewFactory,
                onAction = onAction,
            )
    }
}

@Composable
private fun HomeBottomSearchArea(
    pageCount: Int,
    selectedPageIndex: Int,
    showPageIndicator: Boolean,
    haptics: LauncherHaptics,
    onAction: (LauncherShellAction) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(HOME_SEARCH_AREA_HEIGHT_DP.dp),
        contentAlignment = Alignment.Center,
    ) {
        HomeBackgroundContextMenu(
            haptics = haptics,
            onAction = onAction,
            modifier = Modifier.matchParentSize(),
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
                        .clickable(onClick = { onAction(LauncherShellAction.OpenSearch) })
                        .padding(horizontal = HOME_SEARCH_HORIZONTAL_PADDING_DP.dp),
            )
        } else {
            Surface(
                modifier =
                    Modifier
                        .height(HOME_SEARCH_PILL_HEIGHT_DP.dp)
                        .clip(RoundedCornerShape(HOME_SEARCH_PILL_HEIGHT_DP.dp))
                        .clickable(onClick = { onAction(LauncherShellAction.OpenSearch) }),
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

private fun StandardHomeContentState.homeGridPresentation(): HomeGridPresentation =
    HomeGridPresentation(
        notificationCountsByPackage = presentation.notificationCountsByPackage,
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
    val notificationCountsByPackage: Map<AppPackageName, Int>,
    val appShortcutsByApp: AppShortcutsByApp,
    val homeGestures: HomeGestureSettings = HomeGestureSettings(),
    val reducedMotion: Boolean = false,
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
        reserveStatusBar = !(appearance.fullscreenHome || appearance.hideStatusBarOnHome),
        reserveNavigationBar = !(appearance.fullscreenHome || appearance.hideNavigationBarOnHome),
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
    val notificationCountsByPackage: Map<AppPackageName, Int>,
    val appShortcutsByApp: AppShortcutsByApp,
    val labelSettings: HomeLabelSettings,
    val reducedMotion: Boolean = false,
    val widgetViewFactory: HomeWidgetViewFactory,
)

internal data class HomeItemDragState(
    val cell: GridCell,
    val cellSizePx: Float,
    val grid: GridDimensions,
)

internal data class HomeWorkspaceActions(
    val onFolderOpen: (FolderItem) -> Unit,
    val onDragSessionChanged: (HomeDragSession?) -> Unit,
    val haptics: LauncherHaptics,
    val onAction: (LauncherShellAction) -> Unit,
)

private const val HOME_SURFACE_HORIZONTAL_PADDING_DP = 12
private const val HOME_SURFACE_VERTICAL_PADDING_DP = 16
private const val HOME_BOTTOM_CONTROLS_TOP_SPACING_DP = 8
private const val HOME_SEARCH_AREA_HEIGHT_DP = 36
private const val HOME_SEARCH_PILL_HEIGHT_DP = 30
private const val HOME_SEARCH_HORIZONTAL_PADDING_DP = 14
private const val HOME_SEARCH_SURFACE_ALPHA = 0.82f
private const val HOME_SEARCH_BORDER_ALPHA = 0.38f
private const val PAGE_INDICATOR_SETTLED_VISIBLE_MS = 250L
