package com.riffle.app.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLabelSettings
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.WidgetItem

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun HomeFolder(
    folder: FolderItem,
    dragState: HomeItemDragState,
    isEditing: Boolean,
    notificationCount: Int,
    labelSettings: HomeLabelSettings,
    appIconLoader: AppIconLoader,
    actions: HomeWorkspaceActions,
) {
    val metrics = HomeGridLayoutMetrics()
    val isContextMenuExpanded = remember(folder.id) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .heightIn(min = metrics.homeItemContentHeightDp(labelSettings).dp)
                    .combinedClickable(
                        enabled = true,
                        onClick = {
                            if (isEditing) {
                                isContextMenuExpanded.value = true
                            } else {
                                actions.onFolderOpen(folder)
                            }
                        },
                    )
                    .homeItemDrag(
                        enabled = true,
                        enterEditModeOnStart = !isEditing,
                        item = folder,
                        dragState = dragState,
                        actions = actions,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(modifier = Modifier.size(HOME_ICON_SIZE_DP.dp)) {
                FolderPreviewIcon(
                    folder = folder,
                    appIconLoader = appIconLoader,
                )
                if (!isEditing) {
                    NotificationCountBadge(
                        count = notificationCount,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
            WallpaperReadableLabel(
                text = folder.label,
                settings = labelSettings,
            )
        }

        DropdownMenu(
            expanded = isContextMenuExpanded.value,
            onDismissRequest = { isContextMenuExpanded.value = false },
        ) {
            DropdownMenuItem(
                text = { Text(text = "Edit folder") },
                onClick = {
                    isContextMenuExpanded.value = false
                    actions.onFolderOpen(folder)
                },
            )
            DropdownMenuItem(
                text = { Text(text = "Remove from home") },
                onClick = {
                    isContextMenuExpanded.value = false
                    actions.onAction(LauncherShellAction.RemoveHomeShortcut(folder.id))
                },
            )
        }
    }
}

@Composable
fun FolderDialog(
    folder: FolderItem,
    layout: HomeLayout,
    installedApps: List<InstalledApp>,
    appIconLoader: AppIconLoader,
    onDismiss: () -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    val folderName = remember(folder.id, folder.label) { mutableStateOf(folder.label) }
    val addAppQuery = remember(folder.id) { mutableStateOf("") }
    val addAppProfileFilter = remember(folder.id) { mutableStateOf(AppDrawerProfileFilter.ALL) }
    val trimmedFolderName = folderName.value.trim()
    val addableApps = installedApps.filterFolderAddCandidates(layout)
    val visibleAddableApps =
        addableApps
            .filterFolderAddCandidates(addAppQuery.value)
            .filterFolderAddCandidates(addAppProfileFilter.value)
    val addableAppsEmptyText =
        addableApps.folderAddEmptyText(
            query = addAppQuery.value,
            profileFilter = addAppProfileFilter.value,
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = folder.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = folderName.value,
                    onValueChange = { value -> folderName.value = value },
                    singleLine = true,
                    label = { Text(text = "Name") },
                )
                AppSearchField(
                    query = addAppQuery.value,
                    onQueryChanged = { value -> addAppQuery.value = value },
                    label = "Add app",
                )
                AppProfileFilterChips(
                    selectedFilter = addAppProfileFilter.value,
                    onFilterSelected = { filter -> addAppProfileFilter.value = filter },
                )
                FolderContentRows(
                    folder = folder,
                    addableApps = visibleAddableApps,
                    addableAppsEmptyText = addableAppsEmptyText,
                    appIconLoader = appIconLoader,
                    onDismiss = onDismiss,
                    onAction = onAction,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Close")
            }
            TextButton(
                enabled = trimmedFolderName.isNotEmpty() && trimmedFolderName != folder.label,
                onClick = {
                    onAction(
                        LauncherShellAction.RenameHomeFolder(
                            itemId = folder.id,
                            label = trimmedFolderName,
                        ),
                    )
                },
            ) {
                Text(text = "Save")
            }
        },
    )
}

@Composable
fun HomeFolderEditControls(onAction: (LauncherShellAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = {
                onAction(
                    LauncherShellAction.CreateEmptyHomeFolder(
                        label = DEFAULT_FOLDER_LABEL,
                    ),
                )
            },
        ) {
            Text(text = "Create folder")
        }
        TextButton(
            onClick = { onAction(LauncherShellAction.OpenWidgetPicker) },
        ) {
            Text(text = "Widgets")
        }
    }
}

fun Map<AppPackageName, Int>.notificationCountFor(item: LauncherItem): Int =
    when (item) {
        is AppShortcutItem -> this[item.appIdentity.packageName] ?: 0
        is FolderItem ->
            item.items.sumOf { shortcut ->
                this[shortcut.appIdentity.packageName] ?: 0
            }
        is WidgetItem -> 0
    }

@Composable
private fun FolderAppRow(
    shortcut: AppShortcutItem,
    appIconLoader: AppIconLoader,
    menuItems: List<ShortcutContextMenuItem>,
    onClick: () -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    val isMenuExpanded = remember(shortcut.id) { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LauncherAppIcon(
            identity = shortcut.appIdentity,
            label = shortcut.label,
            iconLoader = appIconLoader,
            modifier = Modifier.size(36.dp),
        )
        Text(
            modifier = Modifier.weight(1f),
            text = shortcut.label,
        )
        Box {
            IconButton(onClick = { isMenuExpanded.value = true }) {
                Text(text = "...")
            }
            ShortcutContextMenu(
                expanded = isMenuExpanded.value,
                items = menuItems,
                onDismissRequest = { isMenuExpanded.value = false },
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun FolderContentRows(
    folder: FolderItem,
    addableApps: List<InstalledApp>,
    addableAppsEmptyText: String,
    appIconLoader: AppIconLoader,
    onDismiss: () -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = FOLDER_CONTENT_MAX_HEIGHT_DP.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(
            items = folder.items,
            key = { shortcut -> shortcut.id.value },
        ) { shortcut ->
            FolderAppRow(
                shortcut = shortcut,
                appIconLoader = appIconLoader,
                menuItems = folderShortcutContextMenuItems(folder = folder, shortcut = shortcut),
                onClick = {
                    onAction(shortcut.launchAction())
                    onDismiss()
                },
                onAction = onAction,
            )
        }
        if (addableApps.isEmpty()) {
            item(key = "folder-add-empty") {
                FolderAddEmptyRow(text = addableAppsEmptyText)
            }
        }
        items(
            items = addableApps,
            key = { app -> app.folderAddCandidateKey() },
        ) { app ->
            FolderAddAppRow(
                app = app,
                appIconLoader = appIconLoader,
                onClick = {
                    onAction(
                        LauncherShellAction.AddAppToFolder(
                            folderId = folder.id,
                            app = app,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun FolderAddEmptyRow(text: String) {
    Text(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FolderAddAppRow(
    app: InstalledApp,
    appIconLoader: AppIconLoader,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LauncherAppIcon(
            identity = app.identity,
            label = app.label,
            iconLoader = appIconLoader,
            modifier = Modifier.size(36.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = app.label)
            Text(
                text = app.drawerSubtitle(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClick) {
            Text(text = "Add")
        }
    }
}

private const val FOLDER_CONTENT_MAX_HEIGHT_DP = 360
private const val DEFAULT_FOLDER_LABEL = "Folder"

internal fun folderShortcutContextMenuItems(
    folder: FolderItem,
    shortcut: AppShortcutItem,
): List<ShortcutContextMenuItem> =
    listOf(
        ShortcutContextMenuItem(
            label = "App info",
            action = shortcut.openAppInfoAction(),
        ),
        ShortcutContextMenuItem(
            label = "Remove from folder",
            action =
                LauncherShellAction.RemoveAppFromFolder(
                    folderId = folder.id,
                    itemId = shortcut.id,
                ),
        ),
    )
