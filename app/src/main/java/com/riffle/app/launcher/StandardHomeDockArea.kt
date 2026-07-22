package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.DockAlignment
import com.riffle.core.domain.launcher.home.GridInsets
import com.riffle.core.domain.launcher.home.HomeEditMode
import com.riffle.core.domain.launcher.home.HomeLayout

@Composable
internal fun StandardHomeDockArea(
    layout: HomeLayout,
    presentation: StandardHomePresentation,
    notificationShelfState: DockNotificationShelfState,
    isDockShelfExpanded: Boolean,
    onDockShelfExpandedChange: (Boolean) -> Unit,
    appIconLoader: AppIconLoader,
    actions: HomeWorkspaceActions,
) {
    if (!layout.shouldShowDock()) {
        return
    }

    val hasExpandedContent =
        dockHasExpandedContent(
            hasOverflow = dockHasOverflow(capacity = layout.dock.capacity, itemCount = layout.dock.items.size),
            notificationShelfState = notificationShelfState,
        )
    val showDockShelf =
        layout.editMode == HomeEditMode.Browsing && isDockShelfExpanded && hasExpandedContent
    val dockInteractions =
        DockInteractions(
            haptics = actions.haptics,
            onFolderOpen = actions.onFolderOpen,
            isShelfExpanded = showDockShelf,
            onShelfExpandedChange =
                onDockShelfExpandedChange.takeIf { hasExpandedContent && layout.editMode == HomeEditMode.Browsing },
            reducedMotion = presentation.reducedMotion,
            homeInsetPolicy = presentation.homeInsetPolicy,
            homeLayout = layout,
            onAction = actions.onAction,
        )
    val margins = layout.settings.grid.margin.nonNegative()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = margins.start.dp,
                    end = margins.end.dp,
                    bottom = margins.bottom.dp,
                )
                .dockShelfMotion(dockShelfMotionPolicy(presentation.reducedMotion))
                .dockShelfFrameRatePreference(presentation.motionPerformanceTargetFps),
        horizontalAlignment = layout.dock.alignment.toHorizontalAlignment(),
    ) {
        Spacer(modifier = Modifier.height(HOME_DOCK_TOP_SPACING_DP.dp))
        Box(modifier = Modifier.testTag(HOME_DOCK_TEST_TAG)) {
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
                    isEditing = layout.editMode is HomeEditMode.EditingPage,
                    notificationGroupsByApp = presentation.notificationGroupsByApp,
                    appShortcutsByApp = presentation.appShortcutsByApp,
                    appIconLoader = appIconLoader,
                    widgetViewFactory = presentation.widgetViewFactory,
                    interactions = dockInteractions,
                )
            }
        }
    }
}

private fun DockAlignment.toHorizontalAlignment(): Alignment.Horizontal =
    when (this) {
        DockAlignment.START -> Alignment.Start
        DockAlignment.CENTER -> Alignment.CenterHorizontally
        DockAlignment.END -> Alignment.End
    }

internal fun GridInsets.nonNegative(): GridInsets =
    GridInsets(
        start = start.coerceAtLeast(0),
        top = top.coerceAtLeast(0),
        end = end.coerceAtLeast(0),
        bottom = bottom.coerceAtLeast(0),
    )

internal fun HomeLayout.shouldShowDock(): Boolean =
    dock.isEnabled &&
        dockBackgroundVisible(
            capacity = dock.capacity,
            itemCount = dock.items.size,
            isEditing = false,
            backgroundSizing = dock.backgroundSizing,
        )

/** Bottom region that Cards mode leaves to the standard dock for physical input. */
internal fun HomeLayout.dockInteractionRegionHeightDp(): Int =
    if (!shouldShowDock()) {
        0
    } else {
        settings.grid.margin.nonNegative().bottom + HOME_DOCK_TOP_SPACING_DP + dockHeightDp(dock.iconSizeDp)
    }

private const val HOME_DOCK_TOP_SPACING_DP = 10
internal const val HOME_DOCK_TEST_TAG = "home-dock"
