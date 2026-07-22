package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.InstalledApp

class HomeShortcutEngine(
    private val gridPlacementEngine: GridPlacementEngine = GridPlacementEngine(),
) {
    fun addAppToSelectedPage(
        layout: HomeLayout,
        app: InstalledApp,
    ): HomeShortcutResult =
        when {
            layout.selectedPage.type is LauncherPageType.Generated ->
                HomeShortcutResult.Rejected(PlacementRejectionReason.GENERATED_PAGE)

            layout.containsHomeApp(app.identity) ->
                HomeShortcutResult.Rejected(PlacementRejectionReason.DUPLICATE_APP)

            else ->
                appShortcutFor(app = app, layout = layout).let { shortcut ->
                    when (
                        val result =
                            gridPlacementEngine.placeItemInFirstAvailableCell(
                                page = layout.selectedPage,
                                item = shortcut,
                            )
                    ) {
                        is PlaceLauncherItemResult.Placed ->
                            HomeShortcutResult.Updated(layout.withUpdatedSelectedPage(result.page))

                        is PlaceLauncherItemResult.Rejected ->
                            HomeShortcutResult.Rejected(result.reason)
                    }
                }
        }

    fun addAppShortcutToSelectedPage(
        layout: HomeLayout,
        shortcut: AppShortcut,
    ): HomeShortcutResult =
        when {
            layout.selectedPage.type is LauncherPageType.Generated ->
                HomeShortcutResult.Rejected(PlacementRejectionReason.GENERATED_PAGE)

            layout.containsHomeAppShortcut(identity = shortcut.appIdentity, shortcutId = shortcut.id) ->
                HomeShortcutResult.Rejected(PlacementRejectionReason.DUPLICATE_APP_SHORTCUT)

            else ->
                homeShortcutFor(shortcut = shortcut, layout = layout).let { item ->
                    when (
                        val result =
                            gridPlacementEngine.placeItemInFirstAvailableCell(
                                page = layout.selectedPage,
                                item = item,
                            )
                    ) {
                        is PlaceLauncherItemResult.Placed ->
                            HomeShortcutResult.Updated(layout.withUpdatedSelectedPage(result.page))

                        is PlaceLauncherItemResult.Rejected ->
                            HomeShortcutResult.Rejected(result.reason)
                    }
                }
        }

    fun removeShortcutFromSelectedPage(
        layout: HomeLayout,
        itemId: LauncherItemId,
    ): HomeShortcutResult =
        when {
            layout.selectedPage.items.none { item -> item.id == itemId } ->
                HomeShortcutResult.Rejected(PlacementRejectionReason.ITEM_NOT_FOUND)

            else ->
                HomeShortcutResult.Updated(
                    layout.withUpdatedSelectedPage(
                        gridPlacementEngine.removeItem(
                            page = layout.selectedPage,
                            itemId = itemId,
                        ),
                    ),
                )
        }

    fun moveShortcutToCellOnSelectedPage(
        layout: HomeLayout,
        itemId: LauncherItemId,
        cell: GridCell,
    ): HomeShortcutResult =
        layout.selectedPage.items.firstOrNull { item -> item.id == itemId }
            ?.placement
            ?.copy(cell = cell)
            ?.let { placement ->
                when (
                    val result =
                        gridPlacementEngine.moveItemShiftingAnchors(
                            page = layout.selectedPage,
                            itemId = itemId,
                            cell = placement.cell,
                        )
                ) {
                    is PlaceLauncherItemResult.Placed ->
                        HomeShortcutResult.Updated(layout.withUpdatedSelectedPage(result.page))

                    is PlaceLauncherItemResult.Rejected ->
                        HomeShortcutResult.Rejected(result.reason)
                }
            }
            ?: HomeShortcutResult.Rejected(
                when {
                    layout.selectedPage.items.any { item -> item.id == itemId } ->
                        PlacementRejectionReason.MISSING_PLACEMENT

                    else ->
                        PlacementRejectionReason.ITEM_NOT_FOUND
                },
            )

    /** Moves any placed Home item between editable pages without relying on transient UI state. */
    @Suppress("ReturnCount")
    fun moveItemToPage(
        layout: HomeLayout,
        itemId: LauncherItemId,
        sourcePageId: LauncherPageId,
        targetPageId: LauncherPageId,
        cell: GridCell,
    ): HomeShortcutResult {
        val missingItem = HomeShortcutResult.Rejected(PlacementRejectionReason.ITEM_NOT_FOUND)
        val sourcePage = layout.pages.firstOrNull { it.id == sourcePageId } ?: return missingItem
        val targetPage = layout.pages.firstOrNull { it.id == targetPageId } ?: return missingItem
        if (sourcePage.type is LauncherPageType.Generated || targetPage.type is LauncherPageType.Generated) {
            return HomeShortcutResult.Rejected(PlacementRejectionReason.GENERATED_PAGE)
        }
        val item = sourcePage.items.firstOrNull { it.id == itemId } ?: return missingItem
        val placement = item.placement ?: return HomeShortcutResult.Rejected(PlacementRejectionReason.MISSING_PLACEMENT)

        val sourceWithoutItem = gridPlacementEngine.removeItem(sourcePage, itemId)
        val destination = if (sourcePageId == targetPageId) sourceWithoutItem else targetPage
        return when (
            val result =
                gridPlacementEngine.placeItem(
                    page = destination,
                    item = item.withPlacement(placement.copy(cell = cell)),
                )
        ) {
            is PlaceLauncherItemResult.Placed ->
                HomeShortcutResult.Updated(
                    layout.copy(
                        pages =
                            layout.pages.map { page ->
                                when (page.id) {
                                    sourcePageId ->
                                        if (sourcePageId == targetPageId) result.page else sourceWithoutItem
                                    targetPageId -> result.page
                                    else -> page
                                }
                            },
                    ),
                )

            is PlaceLauncherItemResult.Rejected -> HomeShortcutResult.Rejected(result.reason)
        }
    }

    private fun appShortcutFor(
        app: InstalledApp,
        layout: HomeLayout,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId("app:${app.identity.shortcutKey}:${layout.nextShortcutOrdinal(app)}"),
            appIdentity = app.identity,
            label = app.label,
        )

    private fun homeShortcutFor(
        shortcut: AppShortcut,
        layout: HomeLayout,
    ): AppShortcutItem =
        AppShortcutItem(
            id =
                LauncherItemId(
                    "app-shortcut:${shortcut.appIdentity.shortcutKey}:${shortcut.id.value}:" +
                        layout.nextShortcutOrdinal(shortcut),
                ),
            appIdentity = shortcut.appIdentity,
            label = shortcut.longLabel ?: shortcut.shortLabel,
            appShortcutId = shortcut.id,
        )

    private fun HomeLayout.nextShortcutOrdinal(app: InstalledApp): Int =
        pages
            .flatMap { page -> page.items }
            .filterIsInstance<AppShortcutItem>()
            .count { item -> item.appIdentity == app.identity && item.appShortcutId == null } + 1

    private fun HomeLayout.nextShortcutOrdinal(shortcut: AppShortcut): Int =
        pages
            .flatMap { page -> page.items }
            .filterIsInstance<AppShortcutItem>()
            .count { item -> item.appIdentity == shortcut.appIdentity && item.appShortcutId == shortcut.id } + 1

    private val AppIdentity.shortcutKey: String
        get() = "${profile.id.value}:${packageName.value}/${activityName.value}"
}

sealed interface HomeShortcutResult {
    data class Updated(val layout: HomeLayout) : HomeShortcutResult

    data class Rejected(val reason: PlacementRejectionReason) : HomeShortcutResult
}
