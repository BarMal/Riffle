package com.riffle.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.riffle.core.domain.launcher.home.HomeLabelSettings
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider

@Composable
internal fun StandardHome(
    layout: HomeLayout,
    installedApps: List<InstalledApp>,
    interactions: StandardHomeInteractions,
    presentation: StandardHomePresentation,
    appIconLoader: AppIconLoader,
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
        WidgetPickerDialog(
            providers = presentation.widgetPicker.providers,
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
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(
                    horizontal = HOME_SURFACE_HORIZONTAL_PADDING_DP.dp,
                    vertical = HOME_SURFACE_VERTICAL_PADDING_DP.dp,
                ),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HomeToolbar(
            onAction = actions.onAction,
        )
        Spacer(modifier = Modifier.height(HOME_TOOLBAR_WORKSPACE_SPACING_DP.dp))
        ImmediateWorkspacePager(
            layout = state.visibleLayout,
            pagerState = pagerState,
            gridState =
                HomeGridState(
                    isEditing = false,
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
        Spacer(modifier = Modifier.height(HOME_PAGE_INDICATOR_TOP_SPACING_DP.dp))
        PageIndicator(
            pageCount = state.visibleLayout.pages.size,
            selectedPageIndex = pagerState.visualSelectedPageIndex,
        )
        if (state.visibleLayout.shouldShowDock()) {
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
private fun HomeToolbar(onAction: (LauncherShellAction) -> Unit) {
    Row(
        modifier =
            Modifier
                .widthIn(max = HOME_TOOLBAR_MAX_WIDTH_DP.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = HOME_TOOLBAR_SURFACE_ALPHA))
                .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = { onAction(LauncherShellAction.OpenSearch) },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(text = "Search")
        }
        TextButton(onClick = { onAction(LauncherShellAction.OpenAppDrawer) }) {
            Text(text = "Apps")
        }
        TextButton(onClick = { onAction(LauncherShellAction.OpenSettings) }) {
            Text(text = "Settings")
        }
    }
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

private const val HOME_SURFACE_HORIZONTAL_PADDING_DP = 24
private const val HOME_SURFACE_VERTICAL_PADDING_DP = 24
private const val HOME_TOOLBAR_WORKSPACE_SPACING_DP = 16
private const val HOME_TOOLBAR_MAX_WIDTH_DP = 560
private const val HOME_TOOLBAR_SURFACE_ALPHA = 0.88f
private const val HOME_PAGE_INDICATOR_TOP_SPACING_DP = 12
private const val HOME_DOCK_TOP_SPACING_DP = 16
