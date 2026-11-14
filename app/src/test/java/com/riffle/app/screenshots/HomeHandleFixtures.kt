package com.riffle.app.screenshots

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.settings.LayoutSettings
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
import com.riffle.core.domain.launcher.settings.SearchResultPresentation
import com.riffle.core.domain.launcher.settings.SearchSettings

/** The fixture Home with [count] pages (the first holds the apps), so the handle has dots to show. */
internal fun LauncherShellState.withHomePages(count: Int): LauncherShellState {
    val first = homeLayout.pages.first()
    val extra = (2..count).map { index -> LauncherPage(id = LauncherPageId("page-$index"), grid = first.grid) }
    val layout = homeLayout.copy(pages = homeLayout.pages + extra)
    val key = homeLayoutSet.activeKey
    return copy(homeLayout = layout, homeLayoutSet = homeLayoutSet.copy(layouts = mapOf(key to layout)))
}

/** Search open (the shell's SEARCH destination) with [query] and the fixture's Calendar and Camera as its hits. */
internal fun LauncherShellState.searching(
    query: String = "ca",
    presentation: SearchResultPresentation = SearchResultPresentation.LIST,
): LauncherShellState =
    copy(
        destination = ShellDestination.SEARCH,
        searchQuery = query,
        searchResults =
            if (query.isBlank()) emptyList() else listOf(ScreenshotFixtures.calendar, ScreenshotFixtures.camera),
        launcherSettings = launcherSettings.copy(search = SearchSettings(resultPresentation = presentation)),
    )

internal fun LauncherShellState.withReducedMotion(): LauncherShellState =
    copy(
        launcherSettings =
            launcherSettings.copy(
                motion = launcherSettings.motion.copy(reducedMotionPreference = ReducedMotionPreference.ON),
            ),
    )

/** The fixture shell with [layout] as its layout per posture (ADR 0003, Amendment 2). */
internal fun LauncherShellState.withLayoutSettings(layout: LayoutSettings): LauncherShellState =
    copy(launcherSettings = launcherSettings.copy(layout = layout))
