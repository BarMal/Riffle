package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.InstalledApp

class HomeShortcutEngine(
    private val gridPlacementEngine: GridPlacementEngine = GridPlacementEngine(),
) {
    fun addAppToSelectedPage(
        layout: HomeLayout,
        app: InstalledApp,
    ): HomeShortcutResult =
        appShortcutFor(app = app, layout = layout).let { shortcut ->
            when (val result = gridPlacementEngine.placeItemInFirstAvailableCell(layout.selectedPage, shortcut)) {
                is PlaceLauncherItemResult.Placed ->
                    HomeShortcutResult.Updated(layout.withUpdatedSelectedPage(result.page))

                is PlaceLauncherItemResult.Rejected ->
                    HomeShortcutResult.Rejected(result.reason)
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

    private fun appShortcutFor(
        app: InstalledApp,
        layout: HomeLayout,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId("app:${app.identity.shortcutKey}:${layout.nextShortcutOrdinal(app)}"),
            appIdentity = app.identity,
            label = app.label,
        )

    private fun HomeLayout.nextShortcutOrdinal(app: InstalledApp): Int =
        pages
            .flatMap { page -> page.items }
            .filterIsInstance<AppShortcutItem>()
            .count { item -> item.appIdentity == app.identity } + 1

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

    private val AppIdentity.shortcutKey: String
        get() = "${profile.id.value}:${packageName.value}/${activityName.value}"
}

sealed interface HomeShortcutResult {
    data class Updated(val layout: HomeLayout) : HomeShortcutResult

    data class Rejected(val reason: PlacementRejectionReason) : HomeShortcutResult
}
