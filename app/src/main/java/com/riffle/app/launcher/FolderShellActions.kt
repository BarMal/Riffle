package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderEditRejectionReason
import com.riffle.core.domain.launcher.home.FolderEditResult
import com.riffle.core.domain.launcher.home.FolderEngine
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.FolderMoveEngine
import com.riffle.core.domain.launcher.home.GridPlacementEngine
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.PlaceLauncherItemResult
import com.riffle.core.domain.launcher.home.PlacementRejectionReason

fun FolderEngine.applyEdit(
    action: LauncherShellAction,
    layout: HomeLayout,
): FolderEditResult =
    when (action) {
        is LauncherShellAction.CreateEmptyHomeFolder ->
            layout.createEmptyFolderOnSelectedPage(
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

        is LauncherShellAction.MoveAppInFolder ->
            FolderMoveEngine().moveShortcutInFolderOnSelectedPage(
                layout = layout,
                folderId = action.folderId,
                shortcutId = action.itemId,
                direction = action.direction,
            )

        is LauncherShellAction.MoveAppOutOfFolder ->
            FolderMoveEngine().moveShortcutOutOfFolderToSelectedPage(
                layout = layout,
                folderId = action.folderId,
                shortcutId = action.itemId,
            )

        else -> FolderEditResult.Rejected(FolderEditRejectionReason.ITEM_NOT_FOUND)
    }

private fun HomeLayout.createEmptyFolderOnSelectedPage(
    folderId: LauncherItemId,
    label: String,
): FolderEditResult =
    label.trim()
        .takeIf { trimmedLabel -> trimmedLabel.isNotEmpty() }
        ?.let { trimmedLabel ->
            when (val result = placeEmptyFolder(page = selectedPage, folderId = folderId, label = trimmedLabel)) {
                is PlaceLauncherItemResult.Placed ->
                    FolderEditResult.Updated(withUpdatedSelectedPage(result.page))

                is PlaceLauncherItemResult.Rejected ->
                    when (result.reason) {
                        PlacementRejectionReason.NO_AVAILABLE_CELL ->
                            createEmptyFolderOnNewSelectedPage(folderId = folderId, label = trimmedLabel)

                        else -> FolderEditResult.Rejected(result.reason.toFolderRejectionReason())
                    }
            }
        }
        ?: FolderEditResult.Rejected(FolderEditRejectionReason.INVALID_LABEL)

private fun HomeLayout.createEmptyFolderOnNewSelectedPage(
    folderId: LauncherItemId,
    label: String,
): FolderEditResult =
    when (val result = placeEmptyFolder(page = newHomePage(), folderId = folderId, label = label)) {
        is PlaceLauncherItemResult.Placed ->
            FolderEditResult.Updated(
                copy(
                    pages = pages + result.page,
                    selectedPageId = result.page.id,
                ),
            )

        is PlaceLauncherItemResult.Rejected ->
            FolderEditResult.Rejected(result.reason.toFolderRejectionReason())
    }

private fun placeEmptyFolder(
    page: LauncherPage,
    folderId: LauncherItemId,
    label: String,
): PlaceLauncherItemResult =
    GridPlacementEngine().placeItemInFirstAvailableCell(
        page = page,
        item =
            FolderItem(
                id = folderId,
                label = label,
                items = emptyList(),
            ),
    )

private fun HomeLayout.withUpdatedSelectedPage(page: LauncherPage): HomeLayout =
    copy(
        pages =
            pages.map { existingPage ->
                when (existingPage.id) {
                    page.id -> page
                    else -> existingPage
                }
            },
    )

private fun PlacementRejectionReason.toFolderRejectionReason(): FolderEditRejectionReason =
    when (this) {
        PlacementRejectionReason.MISSING_PLACEMENT -> FolderEditRejectionReason.MISSING_PLACEMENT
        PlacementRejectionReason.ITEM_NOT_FOUND -> FolderEditRejectionReason.ITEM_NOT_FOUND
        PlacementRejectionReason.DUPLICATE_APP -> FolderEditRejectionReason.DUPLICATE_ITEM
        PlacementRejectionReason.DUPLICATE_APP_SHORTCUT -> FolderEditRejectionReason.DUPLICATE_ITEM
        PlacementRejectionReason.OUT_OF_BOUNDS -> FolderEditRejectionReason.OUT_OF_BOUNDS
        PlacementRejectionReason.COLLISION -> FolderEditRejectionReason.COLLISION
        PlacementRejectionReason.NO_AVAILABLE_CELL -> FolderEditRejectionReason.NO_AVAILABLE_CELL
    }

internal fun HomeLayout.nextFolderId(): LauncherItemId {
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
