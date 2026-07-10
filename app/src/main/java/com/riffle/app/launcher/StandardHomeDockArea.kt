package com.riffle.app.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.HomeEditMode
import com.riffle.core.domain.launcher.home.HomeLayout

@Composable
internal fun StandardHomeDockArea(
    layout: HomeLayout,
    presentation: StandardHomePresentation,
    isDockShelfExpanded: Boolean,
    onDockShelfExpandedChange: (Boolean) -> Unit,
    appIconLoader: AppIconLoader,
    actions: HomeWorkspaceActions,
) {
    if (layout.editMode != HomeEditMode.Browsing || !layout.shouldShowDock()) {
        return
    }

    val hasDockOverflow = dockHasOverflow(capacity = layout.dock.capacity, itemCount = layout.dock.items.size)
    val showDockShelf = isDockShelfExpanded && hasDockOverflow
    val notificationShelfState =
        dockNotificationShelfState(
            showNotificationCards = layout.dock.showNotificationCards,
            groups = presentation.notificationGroupsByApp,
            notificationAccessStatus = presentation.notificationAccessStatus,
            apps = presentation.installedApps,
        )
    val dockInteractions =
        DockInteractions(
            haptics = actions.haptics,
            onFolderOpen = actions.onFolderOpen,
            isShelfExpanded = showDockShelf,
            onShelfExpandedChange = onDockShelfExpandedChange.takeIf { hasDockOverflow },
            reducedMotion = presentation.reducedMotion,
            homeInsetPolicy = presentation.homeInsetPolicy,
            onAction = actions.onAction,
        )

    Column(
        modifier = Modifier.dockShelfMotion(dockShelfMotionPolicy(presentation.reducedMotion)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(HOME_DOCK_TOP_SPACING_DP.dp))
        if (showDockShelf) {
            ExpandedDockSurface(
                dock = layout.dock,
                notificationShelfState = notificationShelfState,
                notificationGroupsByApp = presentation.notificationGroupsByApp,
                appShortcutsByApp = presentation.appShortcutsByApp,
                appIconLoader = appIconLoader,
                widgetViewFactory = presentation.widgetViewFactory,
                interactions = dockInteractions,
            )
        } else {
            Dock(
                dock = layout.dock.primaryDock(showShelf = false),
                isEditing = false,
                notificationGroupsByApp = presentation.notificationGroupsByApp,
                appShortcutsByApp = presentation.appShortcutsByApp,
                appIconLoader = appIconLoader,
                widgetViewFactory = presentation.widgetViewFactory,
                interactions = dockInteractions,
            )
        }
    }
}

private fun HomeLayout.shouldShowDock(): Boolean =
    dock.isEnabled &&
        dockBackgroundVisible(
            capacity = dock.capacity,
            itemCount = dock.items.size,
            isEditing = false,
            backgroundSizing = dock.backgroundSizing,
        )

private const val HOME_DOCK_TOP_SPACING_DP = 10
