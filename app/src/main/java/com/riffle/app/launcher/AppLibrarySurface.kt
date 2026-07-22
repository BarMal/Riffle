package com.riffle.app.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.containsHomeApp
import com.riffle.core.domain.launcher.home.containsHomeAppShortcut
import com.riffle.core.domain.launcher.home.dockShortcutIdFor
import com.riffle.core.domain.launcher.settings.AppDrawerPresentation
import com.riffle.core.domain.launcher.settings.MAX_APP_DRAWER_ICON_GRID_COLUMNS
import com.riffle.core.domain.launcher.settings.MIN_APP_DRAWER_ICON_GRID_COLUMNS
import androidx.compose.foundation.lazy.grid.items as gridItems

@Composable
internal fun AppIconGrid(
    apps: List<InstalledApp>,
    columns: Int,
    emptyText: String,
    context: AppListContext,
    modifier: Modifier = Modifier,
) {
    if (apps.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = emptyText, style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = modifier.fillMaxSize().testTag(APP_DRAWER_ICON_GRID_TEST_TAG),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            gridItems(items = apps, key = { app -> app.drawerKey }) { app ->
                AppDrawerIcon(
                    state = app.drawerRowState(context),
                    appIconLoader = context.appIconLoader,
                    onAction = context.onAction,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AppDrawerIcon(
    state: AppDrawerRowState,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    val isMenuExpanded = remember(state.app.identity) { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onAction(LauncherShellAction.LaunchApp(state.app.identity)) },
                    onLongClick = {
                        state.haptics.longPress()
                        isMenuExpanded.value = true
                    },
                    onLongClickLabel = "Show ${state.app.label} actions",
                )
                .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            LauncherAppIcon(
                identity = state.app.identity,
                label = state.app.label,
                iconLoader = appIconLoader,
                modifier = Modifier.launcherIconSize(),
                shape = CircleShape,
            )
            if (state.notificationCount > 0) {
                Text(
                    text = state.notificationCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = state.app.label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AppDrawerRowOverflowMenu(
            state = state,
            isExpanded = isMenuExpanded.value,
            onExpandedChange = { expanded -> isMenuExpanded.value = expanded },
            showButton = false,
            onAction = onAction,
        )
    }
}

internal const val APP_DRAWER_ICON_GRID_TEST_TAG = "app-drawer-icon-grid"
internal const val APP_DRAWER_PRESENTATION_TEST_TAG_PREFIX = "app-drawer-presentation-"
internal const val APP_DRAWER_GRID_COLUMNS_TEST_TAG_PREFIX = "app-drawer-grid-columns-"

@Composable
internal fun SettingsAppDrawerSection(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = "App drawer") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsTextColumn(
                title = "App presentation",
                subtitle = "Choose a detailed list or a compact icon grid",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppDrawerPresentation.entries.forEach { presentation ->
                    FilterChip(
                        modifier = Modifier.testTag("$APP_DRAWER_PRESENTATION_TEST_TAG_PREFIX${presentation.name}"),
                        selected = presentation == state.settings.appDrawer.presentation,
                        onClick = { onAction(LauncherShellAction.SelectAppDrawerPresentation(presentation)) },
                        label = { Text(if (presentation == AppDrawerPresentation.ICONS) "Icons" else "List") },
                    )
                }
            }
            if (state.settings.appDrawer.presentation == AppDrawerPresentation.ICONS) {
                SettingsTextColumn(
                    title = "Icon grid",
                    subtitle = "Choose how many app icons appear in each row",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (columns in MIN_APP_DRAWER_ICON_GRID_COLUMNS..MAX_APP_DRAWER_ICON_GRID_COLUMNS) {
                        FilterChip(
                            modifier = Modifier.testTag("$APP_DRAWER_GRID_COLUMNS_TEST_TAG_PREFIX$columns"),
                            selected = columns == state.settings.appDrawer.iconGridColumns,
                            onClick = { onAction(LauncherShellAction.SelectAppDrawerIconGridColumns(columns)) },
                            label = { Text("$columns") },
                        )
                    }
                }
            }
        }
    }
}

internal fun InstalledApp.drawerRowState(
    context: AppListContext,
    showInlineActions: Boolean = false,
): AppDrawerRowState =
    AppDrawerRowState(
        app = this,
        isOnHome = context.homeLayout.containsHomeApp(identity),
        dockItemId = context.homeLayout.dock.dockShortcutIdFor(identity),
        floatingDockItemId =
            context.overlayDock.items
                .firstOrNull { item -> item.appIdentity == identity && item.appShortcutId == null }
                ?.id,
        notificationCount = context.notificationCountFor(identity),
        shortcutItems =
            context.appShortcutsByApp[identity]
                .orEmpty()
                .map { shortcut ->
                    AppDrawerShortcutMenuItem(
                        shortcut = shortcut,
                        isOnHome = context.homeLayout.containsHomeAppShortcut(shortcut.appIdentity, shortcut.id),
                        floatingDockItemId =
                            context.overlayDock.items
                                .firstOrNull { item ->
                                    item.appIdentity == shortcut.appIdentity && item.appShortcutId == shortcut.id
                                }
                                ?.id,
                    )
                },
        showInlineActions = showInlineActions,
        haptics = context.haptics,
    )
