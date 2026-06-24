package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageType

fun HomeLayout.withoutTrailingEmptyAllAppsPages(): HomeLayout {
    val keptPages =
        pages
            .dropLastWhile { page -> page.isEmptyAllAppsPage }
            .ifEmpty { pages.take(1) }
    val safeSelectedPageId =
        keptPages.firstOrNull { page -> page.id == selectedPageId }?.id
            ?: keptPages.last().id

    return copy(
        pages = keptPages,
        selectedPageId = safeSelectedPageId,
    )
}

private val LauncherPage.isEmptyAllAppsPage: Boolean
    get() = type == LauncherPageType.AllApps && items.isEmpty()
