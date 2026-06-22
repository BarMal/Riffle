package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.InstalledApp

class DockEngine {
    fun addAppToDock(
        layout: HomeLayout,
        app: InstalledApp,
    ): DockEditResult =
        when {
            layout.dock.availableSlots <= 0 ->
                DockEditResult.Rejected(DockEditRejectionReason.NO_AVAILABLE_SLOT)

            layout.dock.containsDockApp(app.identity) ->
                DockEditResult.Rejected(DockEditRejectionReason.DUPLICATE_APP)

            else ->
                DockEditResult.Updated(
                    layout.copy(
                        dock =
                            layout.dock.copy(
                                items = layout.dock.items + appShortcutFor(app = app, layout = layout),
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

    fun moveDockItem(
        layout: HomeLayout,
        itemId: LauncherItemId,
        direction: DockItemMoveDirection,
    ): DockEditResult =
        layout.dock.items.indexOfFirst { item -> item.id == itemId }
            .takeIf { index -> index >= 0 }
            ?.let { currentIndex ->
                currentIndex + direction.indexDelta
            }
            ?.takeIf { targetIndex -> targetIndex in layout.dock.items.indices }
            ?.let { targetIndex ->
                DockEditResult.Updated(
                    layout.copy(
                        dock =
                            layout.dock.copy(
                                items =
                                    layout.dock.items.moveItem(
                                        itemId = itemId,
                                        targetIndex = targetIndex,
                                    ),
                            ),
                    ),
                )
            }
            ?: DockEditResult.Rejected(
                when {
                    layout.dock.items.any { item -> item.id == itemId } ->
                        DockEditRejectionReason.INDEX_OUT_OF_BOUNDS

                    else ->
                        DockEditRejectionReason.ITEM_NOT_FOUND
                },
            )

    private fun appShortcutFor(
        app: InstalledApp,
        layout: HomeLayout,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId("dock:${app.identity.shortcutKey}:${layout.nextDockShortcutOrdinal(app)}"),
            appIdentity = app.identity,
            label = app.label,
        )

    private fun HomeLayout.nextDockShortcutOrdinal(app: InstalledApp): Int =
        dock.items
            .filterIsInstance<AppShortcutItem>()
            .count { item -> item.appIdentity == app.identity } + 1

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
}

sealed interface DockEditResult {
    data class Updated(val layout: HomeLayout) : DockEditResult

    data class Rejected(val reason: DockEditRejectionReason) : DockEditResult
}

enum class DockEditRejectionReason {
    NO_AVAILABLE_SLOT,
    DUPLICATE_APP,
    ITEM_NOT_FOUND,
    INDEX_OUT_OF_BOUNDS,
}

enum class DockItemMoveDirection(
    val indexDelta: Int,
) {
    LEFT(indexDelta = -1),
    RIGHT(indexDelta = 1),
}
