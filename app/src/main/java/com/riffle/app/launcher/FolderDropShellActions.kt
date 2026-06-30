package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderEditResult
import com.riffle.core.domain.launcher.home.FolderEngine
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage

internal fun FolderEngine.createFolderFromDropOnSelectedPage(
    layout: HomeLayout,
    sourceItemId: LauncherItemId,
    targetCell: GridCell,
): FolderEditResult? =
    layout.selectedPage
        .folderDropShortcutPair(sourceItemId = sourceItemId, targetCell = targetCell)
        ?.let { pair ->
            createFolderOnSelectedPage(
                layout = layout,
                folderId = layout.nextFolderId(),
                label = DROP_FOLDER_LABEL,
                itemIds = listOf(pair.target.id, pair.source.id),
            )
        }

private fun LauncherPage.folderDropShortcutPair(
    sourceItemId: LauncherItemId,
    targetCell: GridCell,
): FolderDropShortcutPair? =
    appShortcut(sourceItemId)
        ?.let { source ->
            appShortcutAt(targetCell)
                ?.takeUnless { target -> target.id == source.id }
                ?.let { target -> FolderDropShortcutPair(source = source, target = target) }
        }

private data class FolderDropShortcutPair(
    val source: AppShortcutItem,
    val target: AppShortcutItem,
)

private fun LauncherPage.appShortcut(itemId: LauncherItemId): AppShortcutItem? =
    items.firstOrNull { item -> item.id == itemId } as? AppShortcutItem

private fun LauncherPage.appShortcutAt(cell: GridCell): AppShortcutItem? =
    items.firstOrNull { item -> item.placement?.cell == cell } as? AppShortcutItem

private const val DROP_FOLDER_LABEL = "Folder"
