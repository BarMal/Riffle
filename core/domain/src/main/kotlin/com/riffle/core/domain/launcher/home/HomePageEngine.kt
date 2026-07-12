package com.riffle.core.domain.launcher.home

@Suppress("TooManyFunctions")
class HomePageEngine {
    fun addPage(
        layout: HomeLayout,
        page: LauncherPage,
    ): HomePageEditResult =
        when {
            layout.pages.any { existingPage -> existingPage.id == page.id } ->
                HomePageEditResult.Rejected(HomePageEditRejectionReason.DUPLICATE_PAGE_ID)

            !page.grid.isValid ->
                HomePageEditResult.Rejected(HomePageEditRejectionReason.INVALID_GRID_DIMENSIONS)

            page.hasItemsOutsideGrid() ->
                HomePageEditResult.Rejected(HomePageEditRejectionReason.GRID_ITEMS_OUT_OF_BOUNDS)

            page.hasInvalidItemIdsOrCollisions() ->
                HomePageEditResult.Rejected(HomePageEditRejectionReason.INVALID_PAGE_ITEMS)

            else ->
                HomePageEditResult.Updated(layout.copy(pages = layout.pages + page))
        }

    fun selectPage(
        layout: HomeLayout,
        pageId: LauncherPageId,
    ): HomePageEditResult =
        when {
            layout.pages.none { page -> page.id == pageId } ->
                HomePageEditResult.Rejected(HomePageEditRejectionReason.PAGE_NOT_FOUND)

            else ->
                HomePageEditResult.Updated(layout.copy(selectedPageId = pageId))
        }

    fun deletePage(
        layout: HomeLayout,
        pageId: LauncherPageId,
    ): HomePageEditResult =
        when {
            layout.pages.none { page -> page.id == pageId } ->
                HomePageEditResult.Rejected(HomePageEditRejectionReason.PAGE_NOT_FOUND)

            layout.pages.size == 1 ->
                HomePageEditResult.Rejected(HomePageEditRejectionReason.CANNOT_DELETE_LAST_PAGE)

            else -> {
                val deletedIndex = layout.pages.indexOfFirst { page -> page.id == pageId }
                val remainingPages = layout.pages.filterNot { page -> page.id == pageId }
                val selectedPageId =
                    when (layout.selectedPageId) {
                        pageId -> remainingPages[deletedIndex.coerceAtMost(remainingPages.lastIndex)].id
                        else -> layout.selectedPageId
                    }

                HomePageEditResult.Updated(
                    layout.copy(
                        pages = remainingPages,
                        selectedPageId = selectedPageId,
                        editMode = layout.editMode.afterPageDeleted(pageId = pageId, selectedPageId = selectedPageId),
                    ),
                )
            }
        }

    fun duplicatePage(
        layout: HomeLayout,
        pageId: LauncherPageId,
        duplicatedPageId: LauncherPageId,
        itemIdProvider: () -> LauncherItemId,
    ): HomePageEditResult {
        val sourcePage = layout.pages.firstOrNull { page -> page.id == pageId }

        return when {
            sourcePage == null ->
                HomePageEditResult.Rejected(HomePageEditRejectionReason.PAGE_NOT_FOUND)

            layout.pages.any { page -> page.id == duplicatedPageId } ->
                HomePageEditResult.Rejected(HomePageEditRejectionReason.DUPLICATE_PAGE_ID)

            sourcePage.items.any { item -> item is WidgetItem } ->
                HomePageEditResult.Rejected(HomePageEditRejectionReason.CANNOT_DUPLICATE_PAGE_WITH_WIDGETS)

            else -> {
                val sourceIndex = layout.pages.indexOfFirst { page -> page.id == pageId }
                val duplicatedPage =
                    sourcePage.copy(
                        id = duplicatedPageId,
                        items = sourcePage.items.map { item -> item.duplicate(itemIdProvider) },
                    )

                HomePageEditResult.Updated(
                    layout.copy(
                        pages = layout.pages.withPageInsertedAt(sourceIndex + 1, duplicatedPage),
                        selectedPageId = duplicatedPageId,
                        editMode =
                            layout.editMode.afterPageDuplicated(
                                pageId = pageId,
                                duplicatedPageId = duplicatedPageId,
                            ),
                    ),
                )
            }
        }
    }

    fun movePage(
        layout: HomeLayout,
        pageId: LauncherPageId,
        targetIndex: Int,
    ): HomePageEditResult =
        when {
            layout.pages.none { page -> page.id == pageId } ->
                HomePageEditResult.Rejected(HomePageEditRejectionReason.PAGE_NOT_FOUND)

            targetIndex !in layout.pages.indices ->
                HomePageEditResult.Rejected(HomePageEditRejectionReason.INDEX_OUT_OF_BOUNDS)

            else ->
                HomePageEditResult.Updated(
                    layout.copy(pages = layout.pages.moveItem(pageId = pageId, targetIndex = targetIndex)),
                )
        }

    fun updatePageType(
        layout: HomeLayout,
        pageId: LauncherPageId,
        type: LauncherPageType,
    ): HomePageEditResult =
        when {
            layout.pages.none { page -> page.id == pageId } ->
                HomePageEditResult.Rejected(HomePageEditRejectionReason.PAGE_NOT_FOUND)

            layout.pages.any { page ->
                page.id == pageId && type is LauncherPageType.Generated && page.items.isNotEmpty()
            } ->
                HomePageEditResult.Rejected(HomePageEditRejectionReason.CANNOT_ASSIGN_GENERATED_PAGE_TYPE_WITH_ITEMS)

            else ->
                HomePageEditResult.Updated(
                    layout.copy(
                        pages =
                            layout.pages.map { page ->
                                if (page.id == pageId) {
                                    page.copy(type = type)
                                } else {
                                    page
                                }
                            },
                    ),
                )
        }

    fun updatePageGridDimensions(
        layout: HomeLayout,
        pageId: LauncherPageId,
        dimensions: GridDimensions,
    ): HomePageEditResult {
        val page = layout.pages.firstOrNull { existingPage -> existingPage.id == pageId }

        return when {
            page == null -> HomePageEditResult.Rejected(HomePageEditRejectionReason.PAGE_NOT_FOUND)
            !dimensions.isValid -> HomePageEditResult.Rejected(HomePageEditRejectionReason.INVALID_GRID_DIMENSIONS)
            page.items.any { item -> !dimensions.contains(item.placement) } ->
                HomePageEditResult.Rejected(HomePageEditRejectionReason.GRID_ITEMS_OUT_OF_BOUNDS)

            else ->
                HomePageEditResult.Updated(
                    layout.copy(
                        pages =
                            layout.pages.map { existingPage ->
                                if (existingPage.id == pageId) {
                                    existingPage.copy(grid = dimensions)
                                } else {
                                    existingPage
                                }
                            },
                    ),
                )
        }
    }

    fun togglePagePinned(
        layout: HomeLayout,
        pageId: LauncherPageId,
    ): HomePageEditResult =
        layout.pages.firstOrNull { it.id == pageId }
            ?.let { page ->
                HomePageEditResult.Updated(
                    layout.withUpdatedSelectedPage(page.copy(isPinned = !page.isPinned)),
                )
            }
            ?: HomePageEditResult.Rejected(HomePageEditRejectionReason.PAGE_NOT_FOUND)

    fun enterPageEditMode(
        layout: HomeLayout,
        pageId: LauncherPageId,
    ): HomePageEditResult =
        when {
            layout.pages.none { page -> page.id == pageId } ->
                HomePageEditResult.Rejected(HomePageEditRejectionReason.PAGE_NOT_FOUND)

            else ->
                HomePageEditResult.Updated(
                    layout.copy(
                        selectedPageId = pageId,
                        editMode = HomeEditMode.EditingPage(pageId = pageId),
                    ),
                )
        }

    fun enterPageOverview(layout: HomeLayout): HomePageEditResult =
        HomePageEditResult.Updated(layout.copy(editMode = HomeEditMode.ManagingPages))

    fun exitEditMode(layout: HomeLayout): HomePageEditResult =
        HomePageEditResult.Updated(
            layout.copy(editMode = HomeEditMode.Browsing),
        )

    fun updateGridDimensions(
        layout: HomeLayout,
        dimensions: GridDimensions,
    ): HomePageEditResult =
        when {
            !dimensions.isValid -> HomePageEditResult.Rejected(HomePageEditRejectionReason.INVALID_GRID_DIMENSIONS)
            layout.pages.any { page -> page.items.any { item -> !dimensions.contains(item.placement) } } ->
                HomePageEditResult.Rejected(HomePageEditRejectionReason.GRID_ITEMS_OUT_OF_BOUNDS)

            else ->
                HomePageEditResult.Updated(
                    layout.copy(
                        pages = layout.pages.map { page -> page.copy(grid = dimensions) },
                        settings =
                            layout.settings.copy(
                                grid = layout.settings.grid.copy(dimensions = dimensions),
                            ),
                    ),
                )
        }
}

private fun LauncherItem.duplicate(itemIdProvider: () -> LauncherItemId): LauncherItem =
    when (this) {
        is AppShortcutItem -> copy(id = itemIdProvider())
        is FolderItem ->
            copy(
                id = itemIdProvider(),
                items = items.map { shortcut -> shortcut.copy(id = itemIdProvider()) },
            )
        is WidgetItem -> error("Widget items cannot be duplicated with page copies.")
    }

private fun List<LauncherPage>.withPageInsertedAt(
    index: Int,
    page: LauncherPage,
): List<LauncherPage> =
    toMutableList()
        .apply { add(index, page) }
        .toList()

private fun List<LauncherPage>.moveItem(
    pageId: LauncherPageId,
    targetIndex: Int,
): List<LauncherPage> =
    first { page -> page.id == pageId }.let { movingPage ->
        filterNot { page -> page.id == pageId }.toMutableList()
            .apply { add(targetIndex, movingPage) }
            .toList()
    }

private fun HomeEditMode.afterPageDuplicated(
    pageId: LauncherPageId,
    duplicatedPageId: LauncherPageId,
): HomeEditMode =
    when (this) {
        HomeEditMode.Browsing -> this
        HomeEditMode.ManagingPages -> this
        is HomeEditMode.EditingPage ->
            if (this.pageId == pageId) {
                HomeEditMode.EditingPage(pageId = duplicatedPageId)
            } else {
                this
            }
    }

private fun HomeEditMode.afterPageDeleted(
    pageId: LauncherPageId,
    selectedPageId: LauncherPageId,
): HomeEditMode =
    when (this) {
        HomeEditMode.Browsing -> this
        HomeEditMode.ManagingPages -> this
        is HomeEditMode.EditingPage ->
            if (this.pageId == pageId) {
                HomeEditMode.EditingPage(pageId = selectedPageId)
            } else {
                this
            }
    }

sealed interface HomePageEditResult {
    data class Updated(val layout: HomeLayout) : HomePageEditResult

    data class Rejected(val reason: HomePageEditRejectionReason) : HomePageEditResult
}

enum class HomePageEditRejectionReason {
    PAGE_NOT_FOUND,
    DUPLICATE_PAGE_ID,
    CANNOT_DELETE_LAST_PAGE,
    INDEX_OUT_OF_BOUNDS,
    INVALID_GRID_DIMENSIONS,
    GRID_ITEMS_OUT_OF_BOUNDS,
    INVALID_PAGE_ITEMS,
    CANNOT_DUPLICATE_PAGE_WITH_WIDGETS,
    CANNOT_ASSIGN_GENERATED_PAGE_TYPE_WITH_ITEMS,
    INVALID_LABEL_SETTING,
}

private val GridDimensions.isValid: Boolean
    get() = columns >= MIN_GRID_DIMENSION && rows >= MIN_GRID_DIMENSION

private fun LauncherPage.hasItemsOutsideGrid(): Boolean = items.any { item -> !grid.contains(item.placement) }

private fun LauncherPage.hasInvalidItemIdsOrCollisions(): Boolean =
    items.map { item -> item.id }.toSet().size != items.size ||
        items.indices.any { index ->
            val item = items[index]
            items.drop(index + 1).any { otherItem -> item.collidesWith(otherItem) }
        }

private fun GridDimensions.contains(placement: GridPlacement?): Boolean =
    placement?.let { existingPlacement ->
        existingPlacement.cell.column >= 0 &&
            existingPlacement.cell.row >= 0 &&
            existingPlacement.cell.column + existingPlacement.span.columns <= columns &&
            existingPlacement.cell.row + existingPlacement.span.rows <= rows
    } ?: false

private fun LauncherItem.collidesWith(other: LauncherItem): Boolean =
    placement?.occupiedCells.orEmpty().intersect(other.placement?.occupiedCells.orEmpty()).isNotEmpty()

private val GridPlacement.occupiedCells: Set<GridCell>
    get() =
        (cell.column until cell.column + span.columns)
            .flatMap { column ->
                (cell.row until cell.row + span.rows).map { row ->
                    GridCell(column = column, row = row)
                }
            }
            .toSet()

private const val MIN_GRID_DIMENSION = 1
