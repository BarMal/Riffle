package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherViewMode

fun HomeLayout.withoutTrailingEmptyLibraryPages(): HomeLayout {
    if (viewMode != LauncherViewMode.HOME_SCREEN_LIBRARY) {
        return this
    }

    val keptPages =
        pages
            .dropLastWhile { page -> page.isEmpty }
            .ifEmpty { pages.take(1) }
    val safeSelectedPageId =
        keptPages.firstOrNull { page -> page.id == selectedPageId }?.id
            ?: keptPages.last().id

    return copy(
        pages = keptPages,
        selectedPageId = safeSelectedPageId,
    )
}

private val LauncherPage.isEmpty: Boolean
    get() = items.isEmpty()
