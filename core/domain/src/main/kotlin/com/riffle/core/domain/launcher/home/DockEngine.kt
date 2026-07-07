package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.InstalledApp

class DockEngine {
    fun addAppToDock(
        layout: HomeLayout,
        app: InstalledApp,
    ): DockEditResult =
        when {
            layout.dock.containsDockApp(app.identity) ->
                DockEditResult.Rejected(DockEditRejectionReason.DUPLICATE_APP)

            else ->
                DockEditResult.Updated(
                    layout.copy(
                        dock =
                            layout.dock.copy(
                                capacity = layout.dock.capacity.coerceAtLeast(layout.dock.items.size + 1),
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
            layout.dock.containsHostedWidget(hostedWidgetId) ->
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

    private fun DockModel.containsHostedWidget(hostedWidgetId: HostedWidgetId): Boolean =
        items
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
    INDEX_OUT_OF_BOUNDS,
    INVALID_CAPACITY,
    CAPACITY_BELOW_ITEM_COUNT,
    INVALID_ICON_SIZE,
    INVALID_BACKGROUND_ALPHA,
    INVALID_ITEM_SPACING,
}

enum class DockItemMoveDirection(
    val indexDelta: Int,
) {
    LEFT(indexDelta = -1),
    RIGHT(indexDelta = 1),
}
