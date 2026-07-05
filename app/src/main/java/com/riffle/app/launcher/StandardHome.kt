package com.riffle.app.launcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import com.riffle.core.domain.launcher.settings.HomeSwipeGestureSettings
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
        FolderDialog(
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
    val pagerState = rememberImmediateHomePagerState(layout = state.visibleLayout, actions = actions)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .homeSwipeGestureInput(
                    enabled = state.visibleLayout.editMode == HomeEditMode.Browsing,
                    settings = state.presentation.homeSwipeGestures,
                    onAction = actions.onAction,
                )
                .windowInsetsPadding(WindowInsets.safeDrawing)
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
            onAction = actions.onAction,
        )
        if (state.visibleLayout.editMode == HomeEditMode.Browsing && state.visibleLayout.shouldShowDock()) {
            Spacer(modifier = Modifier.height(HOME_DOCK_TOP_SPACING_DP.dp))
            Dock(
                dock = state.visibleLayout.dock,
                isEditing = false,
                notificationCountsByPackage = state.presentation.notificationCountsByPackage,
                appShortcutsByApp = state.presentation.appShortcutsByApp,
                appIconLoader = appIconLoader,
                haptics = actions.haptics,
                onAction = actions.onAction,
            )
        }
    }
}

@Composable
private fun HomeBottomControls(
    layout: HomeLayout,
    selectedPageIndex: Int,
    showPageIndicator: Boolean,
    appIconLoader: AppIconLoader,
    widgetViewFactory: HomeWidgetViewFactory,
    onAction: (LauncherShellAction) -> Unit,
) {
    when (layout.editMode) {
        HomeEditMode.Browsing ->
            HomeBottomSearchArea(
                pageCount = layout.pages.size,
                selectedPageIndex = selectedPageIndex,
                showPageIndicator = showPageIndicator,
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
    onAction: (LauncherShellAction) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(HOME_SEARCH_AREA_HEIGHT_DP.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (showPageIndicator) {
            PageIndicator(
                pageCount = pageCount,
                selectedPageIndex = selectedPageIndex,
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
        widgetViewFactory = presentation.widgetViewFactory,
    )

private fun HomeLayout.shouldShowDock(): Boolean =
    dock.isEnabled &&
        dockBackgroundVisible(
            capacity = dock.capacity,
            itemCount = dock.items.size,
            isEditing = false,
            backgroundSizing = dock.backgroundSizing,
        )

private fun Modifier.homeSwipeGestureInput(
    enabled: Boolean,
    settings: HomeSwipeGestureSettings,
    onAction: (LauncherShellAction) -> Unit,
): Modifier =
    if (!enabled) {
        this
    } else {
        pointerInput(settings) {
            val interpreter = HomeSwipeGestureInterpreter(thresholdPx = HOME_SWIPE_GESTURE_THRESHOLD_PX)
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                val startPosition = down.position
                var pointerIsDown = true
                var handled = false

                while (pointerIsDown && !handled) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { pointer -> pointer.id == down.id }
                    if (change == null || !change.pressed) {
                        pointerIsDown = false
                    } else {
                        val drag = change.position - startPosition
                        val isVerticalIntent = kotlin.math.abs(drag.y) > kotlin.math.abs(drag.x)
                        val action =
                            if (isVerticalIntent) {
                                homeSwipeActionForDrag(
                                    horizontalDragPx = drag.x,
                                    verticalDragPx = drag.y,
                                    settings = settings,
                                    interpreter = interpreter,
                                )
                            } else {
                                null
                            }

                        if (action != null) {
                            handled = true
                            change.consume()
                            onAction(action)
                        }
                    }
                }
            }
        }
    }

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
    val homeSwipeGestures: HomeSwipeGestureSettings = HomeSwipeGestureSettings(),
    val widgetViewFactory: HomeWidgetViewFactory = EmptyHomeWidgetViewFactory,
    val widgetPicker: StandardHomeWidgetPickerState = StandardHomeWidgetPickerState(),
)

internal data class StandardHomeWidgetPickerState(
    val providers: List<InstalledWidgetProvider> = emptyList(),
    val isOpen: Boolean = false,
)

internal data class HomeGridPresentation(
    val notificationCountsByPackage: Map<AppPackageName, Int>,
    val appShortcutsByApp: AppShortcutsByApp,
    val labelSettings: HomeLabelSettings,
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
private const val HOME_DOCK_TOP_SPACING_DP = 10
private const val PAGE_INDICATOR_SETTLED_VISIBLE_MS = 1200L
private const val HOME_SWIPE_GESTURE_THRESHOLD_PX = 80f
