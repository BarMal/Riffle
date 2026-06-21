package com.riffle.core.domain.launcher.home

class FolderEngine(
    private val gridPlacementEngine: GridPlacementEngine = GridPlacementEngine(),
) {
    fun createFolderOnSelectedPage(
        layout: HomeLayout,
        folderId: LauncherItemId,
        label: String,
        itemIds: List<LauncherItemId>,
    ): FolderEditResult =
        when (
            val selection =
                selectFolderShortcuts(
                    page = layout.selectedPage,
                    itemIds = itemIds,
                )
        ) {
            is FolderShortcutSelection.Selected ->
                createFolderFromShortcuts(
                    layout = layout,
                    folderId = folderId,
                    label = label,
                    itemIds = itemIds,
                    shortcuts = selection.shortcuts,
                )

            is FolderShortcutSelection.Rejected -> FolderEditResult.Rejected(selection.reason)
        }

    private fun createFolderFromShortcuts(
        layout: HomeLayout,
        folderId: LauncherItemId,
        label: String,
        itemIds: List<LauncherItemId>,
        shortcuts: List<AppShortcutItem>,
    ): FolderEditResult =
        shortcuts.first().placement
            ?.let { folderPlacement ->
                placeFolder(
                    layout = layout,
                    itemIds = itemIds,
                    folder =
                        FolderItem(
                            id = folderId,
                            label = label,
                            items = shortcuts.map { shortcut -> shortcut.copy(placement = null) },
                            placement = folderPlacement,
                        ),
                )
            }
            ?: FolderEditResult.Rejected(FolderEditRejectionReason.MISSING_PLACEMENT)

    private fun placeFolder(
        layout: HomeLayout,
        itemIds: List<LauncherItemId>,
        folder: FolderItem,
    ): FolderEditResult {
        val pageWithoutShortcuts =
            layout.selectedPage.copy(
                items = layout.selectedPage.items.filterNot { item -> item.id in itemIds },
            )

        return when (val result = gridPlacementEngine.placeItem(page = pageWithoutShortcuts, item = folder)) {
            is PlaceLauncherItemResult.Placed -> FolderEditResult.Updated(layout.withUpdatedSelectedPage(result.page))
            is PlaceLauncherItemResult.Rejected -> FolderEditResult.Rejected(result.reason.toFolderRejectionReason())
        }
    }

    private fun selectFolderShortcuts(
        page: LauncherPage,
        itemIds: List<LauncherItemId>,
    ): FolderShortcutSelection =
        when {
            itemIds.distinct().size < MIN_FOLDER_ITEM_COUNT ->
                FolderShortcutSelection.Rejected(FolderEditRejectionReason.NOT_ENOUGH_ITEMS)

            itemIds.any { itemId -> page.items.none { item -> item.id == itemId } } ->
                FolderShortcutSelection.Rejected(FolderEditRejectionReason.ITEM_NOT_FOUND)

            else -> page.selectShortcuts(itemIds)
        }

    private fun LauncherPage.selectShortcuts(itemIds: List<LauncherItemId>): FolderShortcutSelection =
        itemIds.mapNotNull { itemId -> items.firstOrNull { item -> item.id == itemId } as? AppShortcutItem }
            .takeIf { shortcuts -> shortcuts.size == itemIds.size }
            ?.let { shortcuts -> FolderShortcutSelection.Selected(shortcuts) }
            ?: FolderShortcutSelection.Rejected(FolderEditRejectionReason.UNSUPPORTED_ITEM)

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
            PlacementRejectionReason.OUT_OF_BOUNDS -> FolderEditRejectionReason.OUT_OF_BOUNDS
            PlacementRejectionReason.COLLISION -> FolderEditRejectionReason.COLLISION
            PlacementRejectionReason.NO_AVAILABLE_CELL -> FolderEditRejectionReason.NO_AVAILABLE_CELL
        }

    private companion object {
        private const val MIN_FOLDER_ITEM_COUNT = 2
    }
}

private sealed interface FolderShortcutSelection {
    data class Selected(val shortcuts: List<AppShortcutItem>) : FolderShortcutSelection

    data class Rejected(val reason: FolderEditRejectionReason) : FolderShortcutSelection
}

sealed interface FolderEditResult {
    data class Updated(val layout: HomeLayout) : FolderEditResult

    data class Rejected(val reason: FolderEditRejectionReason) : FolderEditResult
}

enum class FolderEditRejectionReason {
    NOT_ENOUGH_ITEMS,
    ITEM_NOT_FOUND,
    UNSUPPORTED_ITEM,
    MISSING_PLACEMENT,
    OUT_OF_BOUNDS,
    COLLISION,
    NO_AVAILABLE_CELL,
}
