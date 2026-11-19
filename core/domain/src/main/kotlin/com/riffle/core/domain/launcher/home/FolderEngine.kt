package com.riffle.core.domain.launcher.home

@Suppress("TooManyFunctions")
class FolderEngine(
    private val gridPlacementEngine: GridPlacementEngine = GridPlacementEngine(),
) {
    fun createFolderOnSelectedPage(
        layout: HomeLayout,
        folderId: LauncherItemId,
        label: String,
        itemIds: List<LauncherItemId>,
    ): FolderEditResult =
        when {
            layout.selectedPage.type is LauncherPageType.Generated ->
                FolderEditResult.Rejected(FolderEditRejectionReason.GENERATED_PAGE)

            else -> createFolder(layout, folderId, label, itemIds)
        }

    fun renameFolderOnSelectedPage(
        layout: HomeLayout,
        itemId: LauncherItemId,
        label: String,
    ): FolderEditResult =
        label.sanitizedFolderLabel()
            ?.let { trimmedLabel ->
                layout.selectedPage.items.firstOrNull { item -> item.id == itemId }
                    ?.let { item ->
                        when (item) {
                            is FolderItem ->
                                FolderEditResult.Updated(
                                    layout.withUpdatedSelectedPage(
                                        layout.selectedPage.copy(
                                            items =
                                                layout.selectedPage.items.map { existingItem ->
                                                    when (existingItem.id) {
                                                        itemId -> item.copy(label = trimmedLabel)
                                                        else -> existingItem
                                                    }
                                                },
                                        ),
                                    ),
                                )

                            is AppShortcutItem -> FolderEditResult.Rejected(FolderEditRejectionReason.UNSUPPORTED_ITEM)
                            is WidgetItem -> FolderEditResult.Rejected(FolderEditRejectionReason.UNSUPPORTED_ITEM)
                        }
                    }
                    ?: FolderEditResult.Rejected(FolderEditRejectionReason.ITEM_NOT_FOUND)
            }
            ?: FolderEditResult.Rejected(FolderEditRejectionReason.INVALID_LABEL)

    fun addShortcutToFolderOnSelectedPage(
        layout: HomeLayout,
        folderId: LauncherItemId,
        shortcut: AppShortcutItem,
    ): FolderEditResult =
        when {
            layout.selectedPage.type is LauncherPageType.Generated ->
                FolderEditResult.Rejected(FolderEditRejectionReason.GENERATED_PAGE)

            else -> addShortcutToFolder(layout, folderId, shortcut)
        }

    fun removeShortcutFromFolderOnSelectedPage(
        layout: HomeLayout,
        folderId: LauncherItemId,
        shortcutId: LauncherItemId,
    ): FolderEditResult =
        layout.selectedPage.folder(folderId)
            ?.let { folder ->
                when {
                    folder.items.none { item -> item.id == shortcutId } ->
                        FolderEditResult.Rejected(FolderEditRejectionReason.FOLDER_ITEM_NOT_FOUND)

                    else ->
                        FolderEditResult.Updated(
                            layout.withUpdatedSelectedPage(
                                layout.selectedPage.replaceFolder(
                                    folder.copy(items = folder.items.filterNot { item -> item.id == shortcutId }),
                                ),
                            ),
                        )
                }
            }
            ?: FolderEditResult.Rejected(FolderEditRejectionReason.ITEM_NOT_FOUND)

    private fun createFolder(
        layout: HomeLayout,
        folderId: LauncherItemId,
        label: String,
        itemIds: List<LauncherItemId>,
    ): FolderEditResult =
        label.sanitizedFolderLabel()
            ?.let { trimmedLabel ->
                when (val selection = selectFolderShortcuts(layout.selectedPage, itemIds)) {
                    is FolderShortcutSelection.Selected ->
                        createFolderFromShortcuts(layout, folderId, trimmedLabel, itemIds, selection.shortcuts)

                    is FolderShortcutSelection.Rejected -> FolderEditResult.Rejected(selection.reason)
                }
            }
            ?: FolderEditResult.Rejected(FolderEditRejectionReason.INVALID_LABEL)

    private fun addShortcutToFolder(
        layout: HomeLayout,
        folderId: LauncherItemId,
        shortcut: AppShortcutItem,
    ): FolderEditResult =
        layout.selectedPage.folder(folderId)
            ?.let { folder ->
                when {
                    layout.containsHomeApp(shortcut.appIdentity) ||
                        folder.items.any { item -> item.appIdentity == shortcut.appIdentity } ->
                        FolderEditResult.Rejected(FolderEditRejectionReason.DUPLICATE_ITEM)

                    else ->
                        FolderEditResult.Updated(
                            layout.withUpdatedSelectedPage(
                                layout.selectedPage.replaceFolder(
                                    folder.copy(items = folder.items + shortcut.copy(placement = null)),
                                ),
                            ),
                        )
                }
            }
            ?: FolderEditResult.Rejected(FolderEditRejectionReason.ITEM_NOT_FOUND)

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

    private fun PlacementRejectionReason.toFolderRejectionReason(): FolderEditRejectionReason =
        when (this) {
            PlacementRejectionReason.MISSING_PLACEMENT -> FolderEditRejectionReason.MISSING_PLACEMENT
            PlacementRejectionReason.GENERATED_PAGE -> FolderEditRejectionReason.GENERATED_PAGE
            PlacementRejectionReason.ITEM_NOT_FOUND -> FolderEditRejectionReason.ITEM_NOT_FOUND
            PlacementRejectionReason.DUPLICATE_ITEM_ID -> FolderEditRejectionReason.DUPLICATE_ITEM
            PlacementRejectionReason.DUPLICATE_APP -> FolderEditRejectionReason.DUPLICATE_ITEM
            PlacementRejectionReason.DUPLICATE_APP_SHORTCUT -> FolderEditRejectionReason.DUPLICATE_ITEM
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

private fun LauncherPage.folder(folderId: LauncherItemId): FolderItem? {
    val matchingItem = items.firstOrNull { item -> item.id == folderId }

    return matchingItem as? FolderItem
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

private fun String.sanitizedFolderLabel(): String? =
    trim()
        .takeIf { trimmedLabel -> trimmedLabel.isNotEmpty() }

sealed interface FolderEditResult {
    data class Updated(val layout: HomeLayout) : FolderEditResult

    data class Rejected(val reason: FolderEditRejectionReason) : FolderEditResult
}

enum class FolderItemMoveDirection {
    UP,
    DOWN,
}

enum class FolderEditRejectionReason {
    GENERATED_PAGE,
    NOT_ENOUGH_ITEMS,
    ITEM_NOT_FOUND,
    UNSUPPORTED_ITEM,
    INVALID_LABEL,
    DUPLICATE_ITEM,
    FOLDER_ITEM_NOT_FOUND,
    MISSING_PLACEMENT,
    OUT_OF_BOUNDS,
    COLLISION,
    NO_AVAILABLE_CELL,
}
