package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.InstalledApp

@Suppress("TooManyFunctions")
class DockEngine {
    private val gridPlacementEngine = GridPlacementEngine()

    fun addAppToDock(
        layout: HomeLayout,
        app: InstalledApp,
    ): DockEditResult =
        when {
            layout.dock.containsDockApp(app.identity) ->
                DockEditResult.Rejected(DockEditRejectionReason.DUPLICATE_APP)

            layout.dock.isEnabled && layout.dock.capacity == 0 ->
                DockEditResult.Rejected(DockEditRejectionReason.NO_AVAILABLE_SLOT)

            else ->
                DockEditResult.Updated(
                    layout.copy(
                        dock =
                            layout.dock.copy(
                                capacity = layout.dock.capacityAfterAddingAppShortcut(),
                                isEnabled = true,
                                items = layout.dock.items + appShortcutFor(app = app, layout = layout),
                            ),
                    ),
                )
        }

    fun addWidgetToDock(
        layout: HomeLayout,
        hostedWidgetId: HostedWidgetId,
        label: String,
        targetIndex: Int? = null,
    ): DockEditResult =
        when {
            layout.containsHostedWidget(hostedWidgetId) ->
                DockEditResult.Rejected(DockEditRejectionReason.DUPLICATE_WIDGET)

            targetIndex != null && targetIndex !in 0..layout.dock.items.size ->
                DockEditResult.Rejected(DockEditRejectionReason.INDEX_OUT_OF_BOUNDS)

            else ->
                DockEditResult.Updated(
                    layout.copy(
                        dock =
                            layout.dock.copy(
                                capacity = layout.dock.capacity.coerceAtLeast(layout.dock.items.size + 1),
                                isEnabled = true,
                                items =
                                    layout.dock.items.toMutableList().apply {
                                        add(
                                            index = targetIndex ?: size,
                                            element =
                                                WidgetItem(
                                                    id = LauncherItemId("dock-widget:${hostedWidgetId.value}"),
                                                    appWidgetId = hostedWidgetId,
                                                    label = label.ifBlank { DEFAULT_WIDGET_LABEL },
                                                ),
                                        )
                                    },
                            ),
                    ),
                )
        }

    fun removeDockItem(
        layout: HomeLayout,
        itemId: LauncherItemId,
    ): DockEditResult =
        when {
            layout.dock.items.none { item -> item.id == itemId } ->
                DockEditResult.Rejected(DockEditRejectionReason.ITEM_NOT_FOUND)

            else ->
                DockEditResult.Updated(
                    layout.copy(
                        dock =
                            layout.dock.copy(
                                items = layout.dock.items.filterNot { item -> item.id == itemId },
                            ),
                    ),
                )
        }

    @Suppress("CyclomaticComplexMethod")
    fun moveDockItem(
        layout: HomeLayout,
        itemId: LauncherItemId,
        direction: DockItemMoveDirection,
    ): DockEditResult =
        when {
            layout.dock.items.hasDuplicateIds() ->
                DockEditResult.Rejected(DockEditRejectionReason.DUPLICATE_ITEM_ID)

            else ->
                layout.dock.items.indexOfFirst { item -> item.id == itemId }.let { currentIndex ->
                    val targetIndex = currentIndex + direction.indexDelta
                    when {
                        currentIndex < 0 ->
                            DockEditResult.Rejected(DockEditRejectionReason.ITEM_NOT_FOUND)

                        targetIndex !in layout.dock.items.indices ->
                            DockEditResult.Rejected(DockEditRejectionReason.INDEX_OUT_OF_BOUNDS)

                        else ->
                            moveDockItemToIndex(layout = layout, itemId = itemId, targetIndex = targetIndex)
                    }
                }
        }

    /** Moves a dock item to its final index without mutating the supplied layout. */
    fun moveDockItemToIndex(
        layout: HomeLayout,
        itemId: LauncherItemId,
        targetIndex: Int,
    ): DockEditResult =
        when {
            layout.dock.items.hasDuplicateIds() ->
                DockEditResult.Rejected(DockEditRejectionReason.DUPLICATE_ITEM_ID)

            layout.dock.items.none { item -> item.id == itemId } ->
                DockEditResult.Rejected(DockEditRejectionReason.ITEM_NOT_FOUND)

            targetIndex !in layout.dock.items.indices ->
                DockEditResult.Rejected(DockEditRejectionReason.INDEX_OUT_OF_BOUNDS)

            else ->
                DockEditResult.Updated(
                    layout.copy(
                        dock =
                            layout.dock.copy(
                                items = layout.dock.items.moveItem(itemId = itemId, targetIndex = targetIndex),
                            ),
                    ),
                )
        }

    /** Transfers an app shortcut or folder from a Home page into a Dock slot atomically. */
    @Suppress("CyclomaticComplexMethod")
    fun moveHomeItemToDock(
        layout: HomeLayout,
        itemId: LauncherItemId,
        targetIndex: Int? = null,
        pageId: LauncherPageId? = null,
    ): DockEditResult {
        val sourcePage = pageId?.let { requestedPageId -> layout.pageForId(requestedPageId) } ?: layout.selectedPage
        val item = sourcePage.items.firstOrNull { it.id == itemId }

        return when {
            item == null -> DockEditResult.Rejected(DockEditRejectionReason.ITEM_NOT_FOUND)
            pageId != null && sourcePage.id != pageId ->
                DockEditResult.Rejected(DockEditRejectionReason.HOME_PAGE_NOT_FOUND)
            item !is AppShortcutItem && item !is FolderItem ->
                DockEditResult.Rejected(DockEditRejectionReason.UNSUPPORTED_ITEM)
            !layout.dock.isEnabled -> DockEditResult.Rejected(DockEditRejectionReason.DOCK_DISABLED)
            layout.dock.items.size >= layout.dock.capacity ->
                DockEditResult.Rejected(DockEditRejectionReason.NO_AVAILABLE_SLOT)
            layout.dock.items.any { it.id == itemId } ->
                DockEditResult.Rejected(DockEditRejectionReason.DUPLICATE_ITEM_ID)
            targetIndex != null && targetIndex !in 0..layout.dock.items.size ->
                DockEditResult.Rejected(DockEditRejectionReason.INDEX_OUT_OF_BOUNDS)
            else -> {
                val dockItem = item.withoutHomePlacement()
                val dockItems = layout.dock.items.toMutableList().apply { add(targetIndex ?: size, dockItem) }
                val updatedPage = gridPlacementEngine.removeItem(sourcePage, itemId)
                DockEditResult.Updated(
                    layout.copy(
                        dock = layout.dock.copy(items = dockItems),
                        pages = layout.pages.map { page -> if (page.id == updatedPage.id) updatedPage else page },
                    ),
                )
            }
        }
    }

    /** Transfers an app shortcut or folder from the Dock to a selected Home cell atomically. */
    @Suppress("CyclomaticComplexMethod")
    fun moveDockItemToHome(
        layout: HomeLayout,
        itemId: LauncherItemId,
        cell: GridCell? = null,
        pageId: LauncherPageId? = null,
    ): DockEditResult {
        val item = layout.dock.items.firstOrNull { it.id == itemId }
        val destinationPage =
            pageId?.let { targetPageId -> layout.pages.firstOrNull { it.id == targetPageId } } ?: layout.selectedPage

        return when {
            item == null -> DockEditResult.Rejected(DockEditRejectionReason.ITEM_NOT_FOUND)
            item !is AppShortcutItem && item !is FolderItem ->
                DockEditResult.Rejected(DockEditRejectionReason.UNSUPPORTED_ITEM)
            destinationPage.type is LauncherPageType.Generated ->
                DockEditResult.Rejected(DockEditRejectionReason.GENERATED_HOME_PAGE)
            pageId != null && destinationPage.id != pageId ->
                DockEditResult.Rejected(DockEditRejectionReason.HOME_PAGE_NOT_FOUND)
            else -> {
                val homeItem = item.withoutHomePlacement()
                val placement = cell?.let { GridPlacement(cell = it) }
                val placementResult =
                    if (placement == null) {
                        gridPlacementEngine.placeItemInFirstAvailableCell(destinationPage, homeItem)
                    } else {
                        gridPlacementEngine.placeItem(destinationPage, homeItem.withPlacement(placement))
                    }

                when (placementResult) {
                    is PlaceLauncherItemResult.Placed ->
                        DockEditResult.Updated(
                            layout.copy(
                                dock = layout.dock.copy(items = layout.dock.items.filterNot { it.id == itemId }),
                                pages =
                                    layout.pages.map { page ->
                                        if (page.id == placementResult.page.id) placementResult.page else page
                                    },
                            ),
                        )

                    is PlaceLauncherItemResult.Rejected ->
                        DockEditResult.Rejected(
                            when (placementResult.reason) {
                                PlacementRejectionReason.NO_AVAILABLE_CELL ->
                                    DockEditRejectionReason.NO_AVAILABLE_HOME_CELL
                                else -> DockEditRejectionReason.INVALID_HOME_PLACEMENT
                            },
                        )
                }
            }
        }
    }

    /**
     * Drops the pinned app [draggedId] onto the pinned app [targetId]: the two items become one [AppPairItem] at the
     * target's position, the target's app first. A pair is never nested and nothing is dropped onto one; an app is
     * never paired with itself.
     */
    fun pairDockApps(
        layout: HomeLayout,
        targetId: LauncherItemId,
        draggedId: LauncherItemId,
    ): DockEditResult {
        val items = layout.dock.items
        val target = items.firstOrNull { it.id == targetId }
        val dragged = items.firstOrNull { it.id == draggedId }
        return when {
            items.hasDuplicateIds() -> DockEditResult.Rejected(DockEditRejectionReason.DUPLICATE_ITEM_ID)
            target == null || dragged == null -> DockEditResult.Rejected(DockEditRejectionReason.ITEM_NOT_FOUND)
            targetId == draggedId -> DockEditResult.Rejected(DockEditRejectionReason.SAME_APP)
            !target.isPairablePin() || !dragged.isPairablePin() ->
                DockEditResult.Rejected(DockEditRejectionReason.UNSUPPORTED_ITEM)
            (target as AppShortcutItem).appIdentity == (dragged as AppShortcutItem).appIdentity ->
                DockEditResult.Rejected(DockEditRejectionReason.SAME_APP)
            else ->
                DockEditResult.Updated(
                    layout.withDockItems(
                        items.filterNot { it.id == draggedId }.map { item ->
                            if (item.id == targetId) pairOf(items, target as AppShortcutItem, dragged as AppShortcutItem) else item
                        },
                    ),
                )
        }
    }

    /** "Pair with...": the pinned app [itemId] and [app] become one pair at the pinned app's position. */
    fun pairDockAppWith(
        layout: HomeLayout,
        itemId: LauncherItemId,
        app: InstalledApp,
    ): DockEditResult {
        val items = layout.dock.items
        val pinned = items.firstOrNull { it.id == itemId }
        return when {
            pinned == null -> DockEditResult.Rejected(DockEditRejectionReason.ITEM_NOT_FOUND)
            !pinned.isPairablePin() -> DockEditResult.Rejected(DockEditRejectionReason.UNSUPPORTED_ITEM)
            (pinned as AppShortcutItem).appIdentity == app.identity ->
                DockEditResult.Rejected(DockEditRejectionReason.SAME_APP)
            else -> {
                val second =
                    AppShortcutItem(
                        id = LauncherItemId("dock:${app.identity.shortcutKey}:${layout.nextDockShortcutOrdinal(app)}"),
                        appIdentity = app.identity,
                        label = app.label,
                    ).let { candidate -> candidate.copy(id = candidate.id.uniqueAmong(items)) }
                DockEditResult.Updated(
                    layout.withDockItems(
                        items.map { item -> if (item.id == itemId) pairOf(items, pinned as AppShortcutItem, second) else item },
                    ),
                )
            }
        }
    }

    /** Unpair: the pair becomes its two apps, first then second, at the pair's position. */
    fun unpairDockItem(
        layout: HomeLayout,
        pairId: LauncherItemId,
    ): DockEditResult {
        val items = layout.dock.items
        val pair = items.firstOrNull { it.id == pairId } as? AppPairItem
        return when {
            pair == null -> DockEditResult.Rejected(DockEditRejectionReason.ITEM_NOT_FOUND)
            else -> {
                val others = items.filterNot { it.id == pairId }
                val first = pair.first.copy(id = pair.first.id.uniqueAmong(others))
                val second = pair.second.copy(id = pair.second.id.uniqueAmong(others + first))
                // Like pinning an app, an extra item scrolls in the strip rather than being refused at the capacity.
                DockEditResult.Updated(
                    layout.withDockItems(
                        items.flatMap { item -> if (item.id == pairId) listOf(first, second) else listOf(item) },
                    ),
                )
            }
        }
    }

    /** Swap sides: the pair's apps trade places, so the tile and the launch order both reverse. */
    fun swapDockPair(
        layout: HomeLayout,
        pairId: LauncherItemId,
    ): DockEditResult {
        val pair = layout.dock.items.firstOrNull { it.id == pairId } as? AppPairItem
        return if (pair == null) {
            DockEditResult.Rejected(DockEditRejectionReason.ITEM_NOT_FOUND)
        } else {
            DockEditResult.Updated(
                layout.withDockItems(
                    layout.dock.items.map { item ->
                        if (item.id == pairId) pair.copy(first = pair.second, second = pair.first) else item
                    },
                ),
            )
        }
    }

    private fun LauncherItem.isPairablePin(): Boolean = this is AppShortcutItem && appShortcutId == null

    private fun HomeLayout.withDockItems(items: List<LauncherItem>): HomeLayout =
        copy(dock = dock.copy(items = items))

    private fun pairOf(
        items: List<LauncherItem>,
        first: AppShortcutItem,
        second: AppShortcutItem,
    ): AppPairItem =
        AppPairItem(
            id =
                generateSequence(1) { it + 1 }
                    .map { LauncherItemId("dock-pair:$it") }
                    .first { candidate -> items.none { it.id == candidate } },
            first = first.copy(placement = null),
            second = second.copy(placement = null),
        )

    private fun LauncherItemId.uniqueAmong(items: List<LauncherItem>): LauncherItemId {
        val used = items.flatMap { item -> listOf(item.id) + item.memberIds() }.map { it.value }.toSet()
        return generateSequence(0) { it + 1 }
            .map { n -> if (n == 0) this else LauncherItemId("${value}#$n") }
            .first { it.value !in used }
    }

    private fun LauncherItem.memberIds(): List<LauncherItemId> =
        when (this) {
            is AppPairItem -> listOf(first.id, second.id)
            else -> emptyList()
        }

    private fun appShortcutFor(
        app: InstalledApp,
        layout: HomeLayout,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId("dock:${app.identity.shortcutKey}:${layout.nextDockShortcutOrdinal(app)}"),
            appIdentity = app.identity,
            label = app.label,
        )

    private fun LauncherItem.withoutHomePlacement(): LauncherItem =
        when (this) {
            is AppShortcutItem -> copy(placement = null)
            is FolderItem -> copy(placement = null)
            is WidgetItem -> copy(placement = null)
            is AppPairItem -> copy(placement = null)
        }

    private fun HomeLayout.nextDockShortcutOrdinal(app: InstalledApp): Int =
        dock.items
            .filterIsInstance<AppShortcutItem>()
            .count { item -> item.appIdentity == app.identity } + 1

    @Suppress("MaxLineLength")
    private fun HomeLayout.pageForId(pageId: LauncherPageId): LauncherPage? =
        pages.firstOrNull { page -> page.id == pageId }

    private fun DockModel.capacityAfterAddingAppShortcut(): Int =
        if (isEnabled) {
            capacity
        } else {
            capacity.coerceAtLeast(1)
        }

    private val AppIdentity.shortcutKey: String
        get() = "${profile.id.value}:${packageName.value}/${activityName.value}"

    private fun List<LauncherItem>.moveItem(
        itemId: LauncherItemId,
        targetIndex: Int,
    ): List<LauncherItem> =
        first { item -> item.id == itemId }.let { movingItem ->
            filterNot { item -> item.id == itemId }.toMutableList()
                .apply { add(targetIndex, movingItem) }
                .toList()
        }

    private fun List<LauncherItem>.hasDuplicateIds(): Boolean = map(LauncherItem::id).toSet().size != size

    private fun HomeLayout.containsHostedWidget(hostedWidgetId: HostedWidgetId): Boolean =
        (
            pages
                .flatMap { page -> page.items } + dock.items
        )
            .filterIsInstance<WidgetItem>()
            .any { widget -> widget.appWidgetId == hostedWidgetId }
}

private const val DEFAULT_WIDGET_LABEL = "Widget"

sealed interface DockEditResult {
    data class Updated(val layout: HomeLayout) : DockEditResult

    data class Rejected(val reason: DockEditRejectionReason) : DockEditResult
}

enum class DockEditRejectionReason {
    NO_AVAILABLE_SLOT,
    DUPLICATE_APP,
    DUPLICATE_WIDGET,
    ITEM_NOT_FOUND,
    DUPLICATE_ITEM_ID,
    INDEX_OUT_OF_BOUNDS,
    INVALID_CAPACITY,
    CAPACITY_BELOW_ITEM_COUNT,
    INVALID_ICON_SIZE,
    INVALID_BACKGROUND_ALPHA,
    INVALID_ITEM_SPACING,
    INVALID_LIVE_APP_SLOTS,
    DOCK_DISABLED,
    UNSUPPORTED_ITEM,
    GENERATED_HOME_PAGE,
    HOME_PAGE_NOT_FOUND,
    NO_AVAILABLE_HOME_CELL,
    INVALID_HOME_PLACEMENT,

    /** An app cannot be paired with itself. */
    SAME_APP,

    /** A side dock would take a grid column the workspace has nowhere to put the contents of. */
    NO_ROOM_FOR_GRID,
}

enum class DockItemMoveDirection(
    val indexDelta: Int,
) {
    LEFT(indexDelta = -1),
    RIGHT(indexDelta = 1),
}
