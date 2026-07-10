package com.riffle.core.domain.launcher.home

class FolderMoveEngine(
    private val gridPlacementEngine: GridPlacementEngine = GridPlacementEngine(),
) {
    fun moveShortcutInFolderOnSelectedPage(
        layout: HomeLayout,
        folderId: LauncherItemId,
        shortcutId: LauncherItemId,
        direction: FolderItemMoveDirection,
    ): FolderEditResult =
        layout.selectedPage.folder(folderId)
            ?.let { folder ->
                val currentIndex = folder.items.indexOfFirst { item -> item.id == shortcutId }
                val targetIndex =
                    when (direction) {
                        FolderItemMoveDirection.UP -> currentIndex - 1
                        FolderItemMoveDirection.DOWN -> currentIndex + 1
                    }

                when {
                    currentIndex == -1 ->
                        FolderEditResult.Rejected(FolderEditRejectionReason.FOLDER_ITEM_NOT_FOUND)

                    targetIndex !in folder.items.indices ->
                        FolderEditResult.Rejected(FolderEditRejectionReason.OUT_OF_BOUNDS)

                    else ->
                        FolderEditResult.Updated(
                            layout.withUpdatedSelectedPage(
                                layout.selectedPage.replaceFolder(
                                    folder.copy(items = folder.items.moved(currentIndex, targetIndex)),
                                ),
                            ),
                        )
                }
            }
            ?: FolderEditResult.Rejected(FolderEditRejectionReason.ITEM_NOT_FOUND)

    fun moveShortcutOutOfFolderToSelectedPage(
        layout: HomeLayout,
        folderId: LauncherItemId,
        shortcutId: LauncherItemId,
    ): FolderEditResult =
        layout.selectedPage.folder(folderId)
            ?.let { folder ->
                folder.items.firstOrNull { item -> item.id == shortcutId }
                    ?.let { shortcut ->
                        val pageWithUpdatedFolder =
                            layout.selectedPage.replaceFolder(
                                folder.copy(items = folder.items.filterNot { item -> item.id == shortcutId }),
                            )

                        when (
                            val result =
                                gridPlacementEngine.placeItemInFirstAvailableCell(
                                    page = pageWithUpdatedFolder,
                                    item = shortcut.copy(placement = null),
                                )
                        ) {
                            is PlaceLauncherItemResult.Placed ->
                                FolderEditResult.Updated(layout.withUpdatedSelectedPage(result.page))

                            is PlaceLauncherItemResult.Rejected ->
                                FolderEditResult.Rejected(result.reason.toFolderRejectionReason())
                        }
                    }
                    ?: FolderEditResult.Rejected(FolderEditRejectionReason.FOLDER_ITEM_NOT_FOUND)
            }
            ?: FolderEditResult.Rejected(FolderEditRejectionReason.ITEM_NOT_FOUND)
}

private fun LauncherPage.folder(folderId: LauncherItemId): FolderItem? {
    return items.firstOrNull { item -> item.id == folderId } as? FolderItem
}

private fun LauncherPage.replaceFolder(folder: FolderItem): LauncherPage =
    copy(
        items =
            items.map { item ->
                when (item.id) {
                    folder.id -> folder
                    else -> item
                }
            },
    )

private fun <T> List<T>.moved(
    fromIndex: Int,
    toIndex: Int,
): List<T> =
    toMutableList()
        .also { items ->
            val item = items.removeAt(fromIndex)
            items.add(toIndex, item)
        }

private fun PlacementRejectionReason.toFolderRejectionReason(): FolderEditRejectionReason =
    when (this) {
        PlacementRejectionReason.MISSING_PLACEMENT -> FolderEditRejectionReason.MISSING_PLACEMENT
        PlacementRejectionReason.ITEM_NOT_FOUND -> FolderEditRejectionReason.ITEM_NOT_FOUND
        PlacementRejectionReason.DUPLICATE_ITEM_ID -> FolderEditRejectionReason.DUPLICATE_ITEM
        PlacementRejectionReason.DUPLICATE_APP -> FolderEditRejectionReason.DUPLICATE_ITEM
        PlacementRejectionReason.DUPLICATE_APP_SHORTCUT -> FolderEditRejectionReason.DUPLICATE_ITEM
        PlacementRejectionReason.OUT_OF_BOUNDS -> FolderEditRejectionReason.OUT_OF_BOUNDS
        PlacementRejectionReason.COLLISION -> FolderEditRejectionReason.COLLISION
        PlacementRejectionReason.NO_AVAILABLE_CELL -> FolderEditRejectionReason.NO_AVAILABLE_CELL
    }
