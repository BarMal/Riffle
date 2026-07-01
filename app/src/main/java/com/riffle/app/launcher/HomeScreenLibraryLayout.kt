package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridPlacementEngine
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.PlaceLauncherItemResult
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.containsHomeApp

fun HomeLayout.withHomeScreenLibraryApps(apps: List<InstalledApp>): HomeLayout =
    when (viewMode) {
        LauncherViewMode.HOME_SCREEN_LIBRARY ->
            when {
                settings.grid.compactLibraryPages ->
                    withCompactedLibraryApps(apps)

                else -> withMissingLibraryApps(apps)
            }
                .withoutTrailingEmptyLibraryPages()

        LauncherViewMode.STANDARD_APP_DRAWER,
        LauncherViewMode.CARD_INTERFACE,
        -> this
    }

private fun HomeLayout.withMissingLibraryApps(apps: List<InstalledApp>): HomeLayout =
    apps
        .filterNot { app -> containsHomeApp(app.identity) }
        .fold(this) { layout, app -> layout.withLibraryApp(app) }

fun HomeLayout.withoutHomeScreenLibraryApps(): HomeLayout {
    val pagesWithoutLibraryItems =
        pages
            .filterNot { page -> page.isGeneratedLibraryPage }
            .map { page -> page.copy(items = page.items.mapNotNull { item -> item.withoutLibraryApps() }) }
            .ifEmpty { listOf(HomeLayoutDefaults.standard().selectedPage) }
    val safeSelectedPageId =
        pagesWithoutLibraryItems.firstOrNull { page -> page.id == selectedPageId }?.id
            ?: pagesWithoutLibraryItems.first().id

    return copy(
        pages = pagesWithoutLibraryItems,
        selectedPageId = safeSelectedPageId,
    )
}

private fun HomeLayout.withLibraryApp(app: InstalledApp): HomeLayout =
    app.libraryShortcut()
        .let { shortcut -> placeLibraryShortcut(shortcut) }

internal fun HomeLayout.placeLibraryShortcut(shortcut: AppShortcutItem): HomeLayout =
    pages.fold(this as HomeLayout?) { layout, page ->
        layout?.takeUnless { currentLayout -> currentLayout.containsHomeApp(shortcut.appIdentity) }
            ?.placeLibraryShortcut(page = page, shortcut = shortcut)
            ?: layout
    }
        ?.takeIf { layout -> layout.containsHomeApp(shortcut.appIdentity) }
        ?: withAdditionalLibraryPage(shortcut)

private fun HomeLayout.placeLibraryShortcut(
    page: LauncherPage,
    shortcut: AppShortcutItem,
): HomeLayout =
    when (
        val result =
            GridPlacementEngine().placeItemInFirstAvailableCell(
                page = page,
                item = shortcut,
            )
    ) {
        is PlaceLauncherItemResult.Placed ->
            copy(
                pages =
                    pages.map { existingPage ->
                        when (existingPage.id) {
                            page.id -> result.page
                            else -> existingPage
                        }
                    },
            )

        is PlaceLauncherItemResult.Rejected -> this
    }

private fun HomeLayout.withAdditionalLibraryPage(shortcut: AppShortcutItem): HomeLayout =
    nextLibraryPageId().let { pageId ->
        val page =
            LauncherPage(
                id = pageId,
                type = LauncherPageType.AllApps,
                grid = settings.grid.dimensions,
            )

        copy(pages = pages + page)
            .placeLibraryShortcut(page = page, shortcut = shortcut)
    }

private fun HomeLayout.nextLibraryPageId(): LauncherPageId =
    generateSequence(1) { index -> index + 1 }
        .map { index -> LauncherPageId("library:$index") }
        .first { pageId -> pages.none { page -> page.id == pageId } }

internal fun InstalledApp.libraryShortcut(): AppShortcutItem =
    AppShortcutItem(
        id = LauncherItemId("library-app:${identity.shortcutKey}"),
        appIdentity = identity,
        label = label,
    )

private fun LauncherItem.withoutLibraryApps(): LauncherItem? =
    when (this) {
        is AppShortcutItem -> takeUnless { item -> item.isLibraryApp }
        is FolderItem -> this
        is WidgetItem -> this
    }

internal val AppShortcutItem.isLibraryApp: Boolean
    get() = id.value.startsWith(LIBRARY_APP_ITEM_PREFIX)

private val AppIdentity.shortcutKey: String
    get() = "${profile.id.value}:${packageName.value}/${activityName.value}"

private const val LIBRARY_APP_ITEM_PREFIX = "library-app:"
