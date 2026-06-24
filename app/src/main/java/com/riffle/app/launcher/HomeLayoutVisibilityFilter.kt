package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridPlacementEngine
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.PlaceLauncherItemResult
import com.riffle.core.domain.launcher.home.WidgetItem

fun HomeLayout.visibleTo(apps: List<InstalledApp>): HomeLayout =
    visibleTo(
        visibleAppIdentities = apps.map { app -> app.identity }.toSet(),
    )

private fun HomeLayout.visibleTo(visibleAppIdentities: Set<AppIdentity>): HomeLayout =
    copy(
        pages =
            pages.map { page ->
                page.visibleTo(visibleAppIdentities)
            },
        dock =
            dock.copy(
                items = dock.items.mapNotNull { item -> item.visibleTo(visibleAppIdentities) },
            ),
    ).withoutTrailingEmptyLibraryPages()

private fun LauncherPage.visibleTo(visibleAppIdentities: Set<AppIdentity>): LauncherPage =
    copy(items = emptyList())
        .packIntoFirstAvailableCells(
            items =
                items
                    .mapNotNull { item -> item.visibleTo(visibleAppIdentities) }
                    .sortedForPacking(),
        )

private fun LauncherItem.visibleTo(visibleAppIdentities: Set<AppIdentity>): LauncherItem? =
    when (this) {
        is AppShortcutItem -> takeIf { item -> item.appIdentity in visibleAppIdentities }
        is FolderItem ->
            copy(items = items.filter { item -> item.appIdentity in visibleAppIdentities })
                .takeIf { folder -> items.isEmpty() || folder.items.isNotEmpty() }
        is WidgetItem -> this
    }

private fun LauncherPage.packIntoFirstAvailableCells(items: List<LauncherItem>): LauncherPage =
    items.fold(this) { page, item ->
        when (
            val result =
                GridPlacementEngine().placeItemInFirstAvailableCell(
                    page = page,
                    item = item,
                    span = item.placement?.span ?: GridSpan(),
                )
        ) {
            is PlaceLauncherItemResult.Placed -> result.page
            is PlaceLauncherItemResult.Rejected -> page
        }
    }

private fun List<LauncherItem>.sortedForPacking(): List<LauncherItem> =
    sortedWith(
        compareBy<LauncherItem> { item -> item.placement?.cell?.row ?: Int.MAX_VALUE }
            .thenBy { item -> item.placement?.cell?.column ?: Int.MAX_VALUE }
            .thenBy { item -> item.id.value },
    )
