package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.FolderEditResult
import com.riffle.core.domain.launcher.home.FolderEngine
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherItemId

fun FolderEngine.applyEdit(
    action: LauncherShellAction.CreateHomeFolder,
    layout: HomeLayout,
): FolderEditResult =
    createFolderOnSelectedPage(
        layout = layout,
        folderId = layout.nextFolderId(),
        label = action.label,
        itemIds = action.itemIds,
    )

private fun HomeLayout.nextFolderId(): LauncherItemId {
    val id = "folder:${selectedPageId.value}:${nextFolderOrdinal()}"

    return LauncherItemId(id)
}

private fun HomeLayout.nextFolderOrdinal(): Int =
    pages
        .flatMap { page -> page.items }
        .filterIsInstance<FolderItem>()
        .count() + 1
