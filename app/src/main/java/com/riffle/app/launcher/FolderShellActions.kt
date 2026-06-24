package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderEditRejectionReason
import com.riffle.core.domain.launcher.home.FolderEditResult
import com.riffle.core.domain.launcher.home.FolderEngine
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherItemId

fun FolderEngine.applyEdit(
    action: LauncherShellAction,
    layout: HomeLayout,
): FolderEditResult =
    when (action) {
        is LauncherShellAction.CreateEmptyHomeFolder ->
            createEmptyFolderOnSelectedPage(
                layout = layout,
                folderId = layout.nextFolderId(),
                label = action.label,
            )

        is LauncherShellAction.CreateHomeFolder ->
            createFolderOnSelectedPage(
                layout = layout,
                folderId = layout.nextFolderId(),
                label = action.label,
                itemIds = action.itemIds,
            )

        is LauncherShellAction.RenameHomeFolder ->
            renameFolderOnSelectedPage(
                layout = layout,
                itemId = action.itemId,
                label = action.label,
            )

        is LauncherShellAction.AddAppToFolder ->
            addShortcutToFolderOnSelectedPage(
                layout = layout,
                folderId = action.folderId,
                shortcut = layout.folderShortcutFor(folderId = action.folderId, app = action.app),
            )

        is LauncherShellAction.RemoveAppFromFolder ->
            removeShortcutFromFolderOnSelectedPage(
                layout = layout,
                folderId = action.folderId,
                shortcutId = action.itemId,
            )

        else -> FolderEditResult.Rejected(FolderEditRejectionReason.ITEM_NOT_FOUND)
    }

private fun HomeLayout.nextFolderId(): LauncherItemId {
    val id = "folder:${selectedPageId.value}:${nextFolderOrdinal()}"

    return LauncherItemId(id)
}

private fun HomeLayout.nextFolderOrdinal(): Int =
    pages
        .flatMap { page -> page.items }
        .filterIsInstance<FolderItem>()
        .count() + 1

private fun HomeLayout.folderShortcutFor(
    folderId: LauncherItemId,
    app: InstalledApp,
): AppShortcutItem =
    AppShortcutItem(
        id =
            LauncherItemId(
                "folder-app:${folderId.value}:${app.identity.shortcutKey}:${nextFolderShortcutOrdinal(app)}",
            ),
        appIdentity = app.identity,
        label = app.label,
    )

private fun HomeLayout.nextFolderShortcutOrdinal(app: InstalledApp): Int =
    pages
        .flatMap { page -> page.items }
        .filterIsInstance<FolderItem>()
        .flatMap { folder -> folder.items }
        .count { item -> item.appIdentity == app.identity } + 1

private val AppIdentity.shortcutKey: String
    get() = "${profile.id.value}:${packageName.value}/${activityName.value}"
