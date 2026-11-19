package com.riffle.core.domain.launcher.home

/**
 * Multi-select in "Edit page" (the Home drag, "Multi-select"). Pure and never throws.
 *
 * Apps and folders can be ticked; widgets and stacks cannot. The selection lives in [HomeEditMode.EditingPage] and
 * only what stands on the selected page counts, so nothing hidden on another page is ever acted on. A selection acts
 * as one: it is removed together, made into a folder together (apps only), and dragged together, landing from the
 * drop cell onward in reading order ([HomeSpill.dropGroup]) or in a folder it is dropped on.
 */
@Suppress("TooManyFunctions", "ReturnCount") // One small rule per way a selection is changed or used.
object HomeSelection {
    private val folders = FolderEngine()

    /** The ids that can be ticked on [page]: its apps and folders. */
    fun selectableIds(page: LauncherPage): List<LauncherItemId> =
        page.items.filter { it is AppShortcutItem || it is FolderItem }.map { it.id }

    /** The ticked items of the selected page, in reading order. */
    fun selectedItems(layout: HomeLayout): List<LauncherItem> {
        val ticked = (layout.editMode as? HomeEditMode.EditingPage)?.selected.orEmpty()
        val page = layout.selectedPage
        return page.items
            .filter { it.id in ticked && (it is AppShortcutItem || it is FolderItem) }
            .sortedBy { item -> item.placement?.let { it.cell.row * page.grid.columns + it.cell.column } ?: 0 }
    }

    fun selectedIds(layout: HomeLayout): Set<LauncherItemId> = selectedItems(layout).map { it.id }.toSet()

    fun toggle(
        layout: HomeLayout,
        itemId: LauncherItemId,
    ): HomeLayout {
        val mode = layout.editMode as? HomeEditMode.EditingPage ?: return layout
        if (itemId !in selectableIds(layout.selectedPage)) return layout
        val next = if (itemId in mode.selected) mode.selected - itemId else mode.selected + itemId
        return layout.copy(editMode = mode.copy(selected = next))
    }

    fun selectAll(layout: HomeLayout): HomeLayout {
        val mode = layout.editMode as? HomeEditMode.EditingPage ?: return layout
        return layout.copy(editMode = mode.copy(selected = selectableIds(layout.selectedPage).toSet()))
    }

    fun clear(layout: HomeLayout): HomeLayout {
        val mode = layout.editMode as? HomeEditMode.EditingPage ?: return layout
        return if (mode.selected.isEmpty()) layout else layout.copy(editMode = mode.copy(selected = emptySet()))
    }

    /** "Make a folder": two or more ticked items, every one an app. */
    fun canMakeFolder(layout: HomeLayout): Boolean =
        selectedItems(layout).let { items -> items.size >= 2 && items.all { it is AppShortcutItem } }

    /** The ticked items off Home, the selection cleared. */
    fun removeSelected(layout: HomeLayout): HomeLayout {
        val ticked = selectedIds(layout)
        if (ticked.isEmpty()) return layout
        val page = layout.selectedPage
        val without = page.copy(items = page.items.filterNot { it.id in ticked })
        return clear(layout.copy(pages = layout.pages.map { if (it.id == page.id) without else it }))
    }

    /** The ticked apps made one folder named [label] with id [folderId], where the first of them stands. */
    fun makeFolder(
        layout: HomeLayout,
        folderId: LauncherItemId,
        label: String,
    ): HomeLayout? {
        if (!canMakeFolder(layout)) return null
        val ids = selectedItems(layout).map { it.id }
        return (folders.createFolderOnSelectedPage(layout, folderId, label, ids) as? FolderEditResult.Updated)
            ?.layout
            ?.let { clear(it) }
    }

    /**
     * The ticked items dropped at [cell] of [targetPageId]: they leave the selected page and land from that cell onward
     * in reading order, spilling to the next pages when it is full. The selection is cleared.
     */
    fun dropGroup(
        layout: HomeLayout,
        targetPageId: LauncherPageId,
        cell: GridCell,
    ): HomeShortcutResult {
        val items = selectedItems(layout)
        if (items.isEmpty()) return HomeShortcutResult.Rejected(PlacementRejectionReason.ITEM_NOT_FOUND)
        val removed = removeSelected(layout)
        return when (val result = HomeSpill.dropGroup(removed, items, targetPageId, cell)) {
            is HomeShortcutResult.Updated -> HomeShortcutResult.Updated(result.layout)
            is HomeShortcutResult.Rejected -> result
        }
    }

    /** The ticked apps added to the folder [folderId] of [targetPageId]; rejected unless every one is an app. */
    fun dropGroupOnFolder(
        layout: HomeLayout,
        targetPageId: LauncherPageId,
        folderId: LauncherItemId,
    ): HomeShortcutResult {
        val items = selectedItems(layout)
        val apps = items.filterIsInstance<AppShortcutItem>()
        if (items.isEmpty() || apps.size != items.size) {
            return HomeShortcutResult.Rejected(PlacementRejectionReason.ITEM_NOT_FOUND)
        }
        if (layout.pages.firstOrNull { it.id == targetPageId }?.items?.any { it.id == folderId } != true) {
            return HomeShortcutResult.Rejected(PlacementRejectionReason.ITEM_NOT_FOUND)
        }
        var working = removeSelected(layout).copy(selectedPageId = targetPageId)
        for (app in apps) {
            working =
                when (val added = folders.addShortcutToFolderOnSelectedPage(working, folderId, app)) {
                    is FolderEditResult.Updated -> added.layout
                    // An app that cannot join stays where it was: nothing is lost.
                    is FolderEditResult.Rejected ->
                        return HomeShortcutResult.Rejected(PlacementRejectionReason.COLLISION)
                }
        }
        return HomeShortcutResult.Updated(working.copy(selectedPageId = layout.selectedPageId))
    }

    /**
     * The ticked apps dropped on the app [appId] of [targetPageId]: that app and the group become one folder named
     * [label] with id [folderId], standing where the app stood, the app first. Rejected when the group holds a folder
     * (a folder does not join a folder), when [appId] is not an app of that page, or when nothing would join it. The
     * target may itself be ticked (the group then makes the folder around it). The selection is cleared.
     */
    fun dropGroupOnApp(
        layout: HomeLayout,
        targetPageId: LauncherPageId,
        appId: LauncherItemId,
        folderId: LauncherItemId,
        label: String,
    ): HomeShortcutResult {
        val items = selectedItems(layout)
        val apps = items.filterIsInstance<AppShortcutItem>()
        val target =
            layout.pages.firstOrNull { it.id == targetPageId }?.items?.firstOrNull { it.id == appId }
                as? AppShortcutItem
        val joining = apps.filter { it.id != appId }
        val folderPlacement = target?.placement
        val onlyApps = items.isNotEmpty() && apps.size == items.size
        if (!onlyApps || folderPlacement == null || joining.isEmpty()) {
            return HomeShortcutResult.Rejected(PlacementRejectionReason.ITEM_NOT_FOUND)
        }
        val leaving = joining.map { it.id }.toSet()
        val folder =
            FolderItem(
                id = folderId,
                label = label,
                items = (listOf(target) + joining).map { it.copy(placement = null) },
                placement = folderPlacement,
            )
        val pages =
            layout.pages.map { page ->
                page.copy(
                    items =
                        page.items.filterNot { it.id in leaving }
                            .map { if (page.id == targetPageId && it.id == appId) folder else it },
                )
            }
        return HomeShortcutResult.Updated(clear(layout.copy(pages = pages)))
    }
}
