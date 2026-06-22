package com.riffle.app.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherItem

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun HomeFolder(
    folder: FolderItem,
    isEditing: Boolean,
    notificationCount: Int,
    appIconLoader: AppIconLoader,
    onFolderOpen: (FolderItem) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .combinedClickable(
                        enabled = !isEditing,
                        onClick = { onFolderOpen(folder) },
                        onLongClick = { onAction(LauncherShellAction.EnterHomeEditMode) },
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FolderPreviewIcon(
                folder = folder,
                appIconLoader = appIconLoader,
            )
            Text(
                modifier = Modifier.widthIn(max = 72.dp),
                text = folder.label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }

        if (isEditing) {
            MoveItemControls(
                item = folder,
                label = folder.label,
                onAction = onAction,
            )
            RemoveShortcutButton(
                label = folder.label,
                onClick = { onAction(LauncherShellAction.RemoveHomeShortcut(folder.id)) },
            )
        } else {
            NotificationCountBadge(
                count = notificationCount,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
fun FolderDialog(
    folder: FolderItem,
    installedApps: List<InstalledApp>,
    appIconLoader: AppIconLoader,
    onDismiss: () -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    val folderName = remember(folder.id, folder.label) { mutableStateOf(folder.label) }
    val trimmedFolderName = folderName.value.trim()
    val addableApps =
        installedApps.filterNot { app ->
            folder.items.any { shortcut -> shortcut.appIdentity == app.identity }
        }

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
                FolderContentRows(
                    folder = folder,
                    addableApps = addableApps,
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
                    onDismiss()
                },
            ) {
                Text(text = "Save")
            }
        },
    )
}

@Composable
fun HomeFolderEditControls(
    layout: HomeLayout,
    onAction: (LauncherShellAction) -> Unit,
) {
    val shortcuts = layout.selectedPage.items.filterIsInstance<AppShortcutItem>()
    val folderItemIds = shortcuts.take(MIN_FOLDER_SHORTCUT_COUNT).map { shortcut -> shortcut.id }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            enabled = shortcuts.size >= MIN_FOLDER_SHORTCUT_COUNT,
            onClick = {
                onAction(
                    LauncherShellAction.CreateHomeFolder(
                        itemIds = folderItemIds,
                        label = "Folder",
                    ),
                )
            },
        ) {
            Text(text = "Create folder")
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
    }

@Composable
private fun FolderPreviewIcon(
    folder: FolderItem,
    appIconLoader: AppIconLoader,
) {
    Column(
        modifier =
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        folder.items.take(FOLDER_PREVIEW_ICON_COUNT).chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                rowItems.forEach { shortcut ->
                    LauncherAppIcon(
                        identity = shortcut.appIdentity,
                        label = shortcut.label,
                        iconLoader = appIconLoader,
                        modifier = Modifier.size(17.dp),
                        shape = RoundedCornerShape(5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderAppRow(
    shortcut: AppShortcutItem,
    appIconLoader: AppIconLoader,
    trailingContent: @Composable () -> Unit,
    onClick: () -> Unit,
) {
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
        Text(text = shortcut.label)
        trailingContent()
    }
}

@Composable
private fun FolderContentRows(
    folder: FolderItem,
    addableApps: List<InstalledApp>,
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
                trailingContent = {
                    TextButton(
                        onClick = {
                            onAction(
                                LauncherShellAction.RemoveAppFromFolder(
                                    folderId = folder.id,
                                    itemId = shortcut.id,
                                ),
                            )
                            onDismiss()
                        },
                    ) {
                        Text(text = "Remove")
                    }
                },
                onClick = {
                    onAction(LauncherShellAction.LaunchApp(shortcut.appIdentity))
                    onDismiss()
                },
            )
        }
        items(
            items = addableApps,
            key = { app -> "${app.identity.packageName.value}/${app.identity.activityName.value}" },
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
                    onDismiss()
                },
            )
        }
    }
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
        Text(
            modifier = Modifier.weight(1f),
            text = app.label,
        )
        TextButton(onClick = onClick) {
            Text(text = "Add")
        }
    }
}

private const val FOLDER_PREVIEW_ICON_COUNT = 4
private const val MIN_FOLDER_SHORTCUT_COUNT = 2
private const val FOLDER_CONTENT_MAX_HEIGHT_DP = 360
