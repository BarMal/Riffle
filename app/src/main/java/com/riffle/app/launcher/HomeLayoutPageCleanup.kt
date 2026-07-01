package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode

fun HomeLayout.withoutTrailingEmptyLibraryPages(): HomeLayout {
    if (viewMode != LauncherViewMode.HOME_SCREEN_LIBRARY) {
        return this
    }

    val keptPages =
        pages
            .dropLastWhile { page -> page.isEmptyGeneratedLibraryPage }
            .ifEmpty { pages.take(1) }
    val safeSelectedPageId =
        keptPages.firstOrNull { page -> page.id == selectedPageId }?.id
            ?: keptPages.last().id

    return copy(
        pages = keptPages,
        selectedPageId = safeSelectedPageId,
    )
}

internal val LauncherPage.isGeneratedLibraryPage: Boolean
    get() = type == LauncherPageType.AllApps || id.value.startsWith(LIBRARY_PAGE_ID_PREFIX)

private val LauncherPage.isEmptyGeneratedLibraryPage: Boolean
    get() = isGeneratedLibraryPage && items.isEmpty()

private const val LIBRARY_PAGE_ID_PREFIX = "library:"
