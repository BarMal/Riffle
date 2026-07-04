package com.riffle.app.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.containsHomeApp
import com.riffle.core.domain.launcher.home.containsHomeAppShortcut
import com.riffle.core.domain.launcher.home.dockShortcutIdFor

@Composable
fun AppList(
    apps: List<InstalledApp>,
    emptyText: String,
    context: AppListContext,
    modifier: Modifier = Modifier,
    showSections: Boolean = false,
    showInlineActions: Boolean = true,
) {
    if (apps.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showSections) {
                AppDrawerSections.from(apps).forEach { section ->
                    item(key = "section:${section.title}") {
                        AppDrawerSectionHeader(title = section.displayTitle)
                    }
                    appRows(
                        apps = section.apps,
                        context = context,
                        showInlineActions = showInlineActions,
                    )
                }
            } else {
                appRows(
                    apps = apps,
                    context = context,
                    showInlineActions = showInlineActions,
                )
            }
        }
    }
}

private fun LazyListScope.appRows(
    apps: List<InstalledApp>,
    context: AppListContext,
    showInlineActions: Boolean,
) {
    items(
        items = apps,
        key = { app -> app.drawerKey },
    ) { app ->
        val shortcutItems =
            context.appShortcutsByApp[app.identity]
                .orEmpty()
                .map { shortcut ->
                    AppDrawerShortcutMenuItem(
                        shortcut = shortcut,
                        isOnHome =
                            context.homeLayout.containsHomeAppShortcut(
                                identity = shortcut.appIdentity,
                                shortcutId = shortcut.id,
                            ),
                        floatingDockItemId =
                            context.overlayDock.items
                                .firstOrNull { item ->
                                    item.appIdentity == shortcut.appIdentity && item.appShortcutId == shortcut.id
                                }
                                ?.id,
                    )
                }
        AppDrawerRow(
            state =
                AppDrawerRowState(
                    app = app,
                    isOnHome = context.homeLayout.containsHomeApp(app.identity),
                    dockItemId = context.homeLayout.dock.dockShortcutIdFor(app.identity),
                    floatingDockItemId =
                        context.overlayDock.items
                            .firstOrNull { item -> item.appIdentity == app.identity && item.appShortcutId == null }
                            ?.id,
                    notificationCount = context.notificationCountsByPackage[app.identity.packageName] ?: 0,
                    shortcutItems = shortcutItems,
                    showInlineActions = showInlineActions,
                    haptics = context.haptics,
                ),
            appIconLoader = context.appIconLoader,
            onAction = context.onAction,
        )
    }
}

@Composable
private fun AppDrawerSectionHeader(title: String) {
    Text(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 2.dp),
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AppDrawerRow(
    state: AppDrawerRowState,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    val app = state.app
    val isMenuExpanded = remember(app.identity) { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .combinedClickable(
                    onClick = { onAction(LauncherShellAction.LaunchApp(app.identity)) },
                    onLongClick = {
                        state.haptics.longPress()
                        isMenuExpanded.value = true
                    },
                    onLongClickLabel = "Show ${app.label} actions",
                )
                .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LauncherAppIcon(
            identity = app.identity,
            label = app.label,
            iconLoader = appIconLoader,
            modifier = Modifier.launcherIconSize(),
            shape = CircleShape,
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
            Text(
                text = app.drawerSubtitle(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        }
        if (state.notificationCount > 0) {
            Text(
                text = state.notificationCount.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (state.showInlineActions) {
            AppDrawerRowActions(
                state = state,
                isMenuExpanded = isMenuExpanded.value,
                onMenuExpandedChange = { isExpanded -> isMenuExpanded.value = isExpanded },
                onAction = onAction,
            )
        } else {
            AppDrawerRowOverflowMenu(
                state = state,
                isExpanded = isMenuExpanded.value,
                onExpandedChange = { isExpanded -> isMenuExpanded.value = isExpanded },
                showButton = false,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun AppDrawerRowActions(
    state: AppDrawerRowState,
    isMenuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    TextButton(
        enabled = !state.isOnHome,
        onClick = { onAction(LauncherShellAction.AddAppToHome(state.app)) },
    ) {
        Text(text = if (state.isOnHome) "Added" else "Add")
    }
    TextButton(
        onClick = {
            when (state.dockItemId) {
                null -> onAction(LauncherShellAction.AddAppToDock(state.app))
                else -> onAction(LauncherShellAction.RemoveDockShortcut(state.dockItemId))
            }
        },
    ) {
        Text(text = if (state.dockItemId == null) "Dock" else "Undock")
    }
    TextButton(
        onClick = {
            when (state.floatingDockItemId) {
                null -> onAction(LauncherShellAction.AddAppToFloatingDock(state.app))
                else -> onAction(LauncherShellAction.RemoveFloatingDockShortcut(state.floatingDockItemId))
            }
        },
    ) {
        Text(text = if (state.floatingDockItemId == null) "Float" else "Unfloat")
    }
    AppDrawerRowOverflowMenu(
        state = state,
        isExpanded = isMenuExpanded,
        onExpandedChange = onMenuExpandedChange,
        showButton = true,
        onAction = onAction,
    )
}

@Composable
private fun AppDrawerRowOverflowMenu(
    state: AppDrawerRowState,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    showButton: Boolean,
    onAction: (LauncherShellAction) -> Unit,
) {
    Box {
        if (showButton) {
            IconButton(onClick = { onExpandedChange(true) }) {
                Text(text = "...")
            }
        }
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            AppDrawerRowMenuItem(
                text = if (state.isOnHome) "Added to home" else "Add to home",
                enabled = !state.isOnHome,
                onClick = { onAction(LauncherShellAction.AddAppToHome(state.app)) },
                onExpandedChange = onExpandedChange,
            )
            AppDrawerRowMenuItem(
                text = if (state.dockItemId == null) "Add to dock" else "Remove from dock",
                onClick = {
                    when (state.dockItemId) {
                        null -> onAction(LauncherShellAction.AddAppToDock(state.app))
                        else -> onAction(LauncherShellAction.RemoveDockShortcut(state.dockItemId))
                    }
                },
                onExpandedChange = onExpandedChange,
            )
            AppDrawerRowMenuItem(
                text =
                    if (state.floatingDockItemId == null) {
                        "Add to floating dock"
                    } else {
                        "Remove from floating dock"
                    },
                onClick = {
                    when (state.floatingDockItemId) {
                        null -> onAction(LauncherShellAction.AddAppToFloatingDock(state.app))
                        else -> onAction(LauncherShellAction.RemoveFloatingDockShortcut(state.floatingDockItemId))
                    }
                },
                onExpandedChange = onExpandedChange,
            )
            state.shortcutItems.forEach { item ->
                AppDrawerShortcutMenuItems(
                    item = item,
                    onAction = onAction,
                    onExpandedChange = onExpandedChange,
                )
            }
            AppDrawerRowMenuItem(
                text = "App info",
                onClick = { onAction(LauncherShellAction.OpenAppInfo(state.app.identity)) },
                onExpandedChange = onExpandedChange,
            )
            AppDrawerRowMenuItem(
                text = "Hide",
                onClick = { onAction(LauncherShellAction.HideApp(state.app.identity)) },
                onExpandedChange = onExpandedChange,
            )
            AppDrawerRowMenuItem(
                text = "Uninstall",
                onClick = { onAction(LauncherShellAction.UninstallApp(state.app.identity)) },
                onExpandedChange = onExpandedChange,
            )
        }
    }
}

@Composable
private fun AppDrawerShortcutMenuItems(
    item: AppDrawerShortcutMenuItem,
    onAction: (LauncherShellAction) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
) {
    AppDrawerRowMenuItem(
        text = item.shortcut.menuLabel,
        enabled = item.shortcut.enabled,
        onClick = { onAction(LauncherShellAction.LaunchAppShortcut(item.shortcut)) },
        onExpandedChange = onExpandedChange,
    )
    AppDrawerRowMenuItem(
        text = item.addLabel,
        enabled = item.shortcut.enabled && !item.isOnHome,
        onClick = { onAction(LauncherShellAction.AddAppShortcutToHome(item.shortcut)) },
        onExpandedChange = onExpandedChange,
    )
    AppDrawerRowMenuItem(
        text = item.floatingDockLabel,
        enabled = item.shortcut.enabled,
        onClick = {
            when (item.floatingDockItemId) {
                null -> onAction(LauncherShellAction.AddAppShortcutToFloatingDock(item.shortcut))
                else -> onAction(LauncherShellAction.RemoveFloatingDockShortcut(item.floatingDockItemId))
            }
        },
        onExpandedChange = onExpandedChange,
    )
}

@Composable
private fun AppDrawerRowMenuItem(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text = text) },
        enabled = enabled,
        onClick = {
            onExpandedChange(false)
            onClick()
        },
    )
}

private val InstalledApp.drawerKey: String
    get() = "${identity.profile.id.value}:${identity.packageName.value}/${identity.activityName.value}"

private val AppShortcut.menuLabel: String
    get() = longLabel ?: shortLabel

private data class AppDrawerShortcutMenuItem(
    val shortcut: AppShortcut,
    val isOnHome: Boolean,
    val floatingDockItemId: LauncherItemId?,
) {
    val addLabel: String
        get() = if (isOnHome) "Added ${shortcut.menuLabel}" else "Add ${shortcut.menuLabel}"

    val floatingDockLabel: String
        get() =
            if (floatingDockItemId == null) {
                "Float ${shortcut.menuLabel}"
            } else {
                "Unfloat ${shortcut.menuLabel}"
            }
}

private data class AppDrawerRowState(
    val app: InstalledApp,
    val isOnHome: Boolean,
    val dockItemId: LauncherItemId?,
    val floatingDockItemId: LauncherItemId?,
    val notificationCount: Int,
    val shortcutItems: List<AppDrawerShortcutMenuItem>,
    val showInlineActions: Boolean,
    val haptics: LauncherHaptics,
)
