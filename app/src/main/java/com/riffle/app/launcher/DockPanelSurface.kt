package com.riffle.app.launcher

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.HomeLabelSettings
import com.riffle.core.domain.launcher.home.LauncherPage

/**
 * The dock's panel: the user's own widgets and shortcuts on a short grid, drawn on the expanded
 * shelf.
 *
 * This is [WorkspaceGrid] with a smaller page, not a second grid implementation. Everything a home
 * page can hold, the panel holds -- which is the whole reason the panel is a [LauncherPage] rather
 * than a purpose-built set of tiles. A media widget, a clock, a set of toggles: whatever the user
 * has, placed where they want it, with no component here per kind of content.
 *
 * Read-only for now. Placing items on it comes with the editing pass; until then a panel only has
 * contents if something else put them there.
 */
@Composable
internal fun DockPanel(
    panel: LauncherPage,
    presentation: DockPresentation,
    appIconLoader: AppIconLoader,
    interactions: DockInteractions,
    modifier: Modifier = Modifier,
) {
    WorkspaceGrid(
        page = panel,
        gridState =
            HomeGridState(
                isEditing = false,
                pageCount = 1,
                selectedPageIndex = 0,
                dragSession = null,
            ),
        presentation =
            HomeGridPresentation(
                notificationGroupsByApp = presentation.notificationGroupsByApp,
                appShortcutsByApp = presentation.appShortcutsByApp,
                labelSettings = interactions.homeLayout?.settings?.labels ?: HomeLabelSettings.standard(),
                reducedMotion = interactions.reducedMotion,
                widgetViewFactory = presentation.widgetViewFactory,
                // Without this the panel's items offer "Remove from home", which acts on the
                // selected page and so matches nothing here.
                contextSurface = ShortcutContextSurface.DOCK_PANEL,
            ),
        appIconLoader = appIconLoader,
        actions =
            HomeWorkspaceActions(
                onFolderOpen = interactions.onFolderOpen,
                onDragSessionChanged = {},
                haptics = interactions.haptics,
                onAction = interactions.onAction,
            ),
        modifier = modifier.fillMaxWidth().height(dockPanelHeightDp(panel).dp),
        // Its own tag: the home workspace grid keeps HOME_WORKSPACE_GRID_TEST_TAG, and two nodes
        // sharing one tag would break every test that looks up the home grid by it.
        testTag = DOCK_PANEL_TEST_TAG,
    )
}

/**
 * The panel is given a height rather than left to fill, because the shelf grows into the screen and
 * an unbounded grid would take all of it. A row's worth of height per row is enough for an icon
 * with its label, which is the same thing a home grid cell holds.
 */
internal fun dockPanelHeightDp(panel: LauncherPage): Int = panel.grid.rows.coerceAtLeast(1) * DOCK_PANEL_ROW_HEIGHT_DP

internal const val DOCK_PANEL_TEST_TAG = "dock-panel"

private const val DOCK_PANEL_ROW_HEIGHT_DP = 76
