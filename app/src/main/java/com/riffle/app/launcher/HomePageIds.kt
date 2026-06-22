package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId

fun HomeLayout.newHomePage(): LauncherPage =
    LauncherPage(
        id = nextHomePageId(),
        grid = settings.grid.dimensions,
    )

fun HomeLayout.duplicatedItemIdProvider(pageId: LauncherPageId): () -> LauncherItemId {
    var ordinal = 0
    val existingItemIds = itemIds()

    return {
        generateSequence {
            ordinal += 1
            LauncherItemId("copy:${pageId.value}:$ordinal")
        }.first { itemId -> itemId !in existingItemIds }
    }
}

private fun HomeLayout.nextHomePageId(): LauncherPageId =
    generateSequence(2) { pageNumber -> pageNumber + 1 }
        .map { pageNumber -> LauncherPageId("home-$pageNumber") }
        .first { candidate -> pages.none { page -> page.id == candidate } }

private fun HomeLayout.itemIds(): Set<LauncherItemId> =
    pages
        .flatMap { page -> page.items.flatMap { item -> item.itemIds() } }
        .toSet()

private fun LauncherItem.itemIds(): List<LauncherItemId> =
    when (this) {
        is AppShortcutItem -> listOf(id)
        is FolderItem -> listOf(id) + items.map { shortcut -> shortcut.id }
    }
