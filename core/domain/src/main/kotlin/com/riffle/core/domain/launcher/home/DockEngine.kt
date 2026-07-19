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
    ): DockEditResult =
        when {
            layout.containsHostedWidget(hostedWidgetId) ->
                DockEditResult.Rejected(DockEditRejectionReason.DUPLICATE_WIDGET)

            else ->
                DockEditResult.Updated(
                    layout.copy(
                        dock =
                            layout.dock.copy(
                                capacity = layout.dock.capacity.coerceAtLeast(layout.dock.items.size + 1),
                                isEnabled = true,
                                items =
                                    layout.dock.items +
                                        WidgetItem(
                                            id = LauncherItemId("dock-widget:${hostedWidgetId.value}"),
                                            appWidgetId = hostedWidgetId,
                                            label = label.ifBlank { DEFAULT_WIDGET_LABEL },
                                        ),
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

    /** Transfers an app shortcut or folder from the selected Home page into a Dock slot atomically. */
    fun moveHomeItemToDock(
        layout: HomeLayout,
        itemId: LauncherItemId,
        targetIndex: Int? = null,
    ): DockEditResult {
        val item = layout.selectedPage.items.firstOrNull { it.id == itemId }

        return when {
            item == null -> DockEditResult.Rejected(DockEditRejectionReason.ITEM_NOT_FOUND)
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
                val updatedPage = gridPlacementEngine.removeItem(layout.selectedPage, itemId)
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
    fun moveDockItemToHome(
        layout: HomeLayout,
        itemId: LauncherItemId,
        cell: GridCell? = null,
    ): DockEditResult {
        val item = layout.dock.items.firstOrNull { it.id == itemId }

        return when {
            item == null -> DockEditResult.Rejected(DockEditRejectionReason.ITEM_NOT_FOUND)
            item !is AppShortcutItem && item !is FolderItem ->
                DockEditResult.Rejected(DockEditRejectionReason.UNSUPPORTED_ITEM)
            layout.selectedPage.type is LauncherPageType.Generated ->
                DockEditResult.Rejected(DockEditRejectionReason.GENERATED_HOME_PAGE)
            else -> {
                val homeItem = item.withoutHomePlacement()
                val placement = cell?.let { GridPlacement(cell = it) }
                val placementResult =
                    if (placement == null) {
                        gridPlacementEngine.placeItemInFirstAvailableCell(layout.selectedPage, homeItem)
                    } else {
                        gridPlacementEngine.placeItem(layout.selectedPage, homeItem.withPlacement(placement))
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
                                PlacementRejectionReason.NO_AVAILABLE_CELL -> DockEditRejectionReason.NO_AVAILABLE_HOME_CELL
                                else -> DockEditRejectionReason.INVALID_HOME_PLACEMENT
                            },
                        )
                }
            }
        }
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
        }

    private fun HomeLayout.nextDockShortcutOrdinal(app: InstalledApp): Int =
        dock.items
            .filterIsInstance<AppShortcutItem>()
            .count { item -> item.appIdentity == app.identity } + 1

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
    DOCK_DISABLED,
    UNSUPPORTED_ITEM,
    GENERATED_HOME_PAGE,
    NO_AVAILABLE_HOME_CELL,
    INVALID_HOME_PLACEMENT,
}

enum class DockItemMoveDirection(
    val indexDelta: Int,
) {
    LEFT(indexDelta = -1),
    RIGHT(indexDelta = 1),
}
