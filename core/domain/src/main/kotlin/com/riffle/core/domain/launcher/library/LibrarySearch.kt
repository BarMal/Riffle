package com.riffle.core.domain.launcher.library

import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.search.LauncherSearchResult

/** One row of the Library's search results. */
sealed interface LibraryHit {
    data class App(
        val app: InstalledApp,
    ) : LibraryHit

    data class Shortcut(
        val shortcut: AppShortcut,
    ) : LibraryHit

    data class Setting(
        val result: LauncherSearchResult.Setting,
    ) : LibraryHit
}

/**
 * The order of the Library's search results: apps first, then app shortcuts, then settings, each kind in the order the
 * search source ranked it. The first one is the top hit that Enter launches. Pure, so the ordering is tested.
 */
object LibrarySearch {
    fun order(
        apps: List<InstalledApp>,
        shortcuts: List<AppShortcut> = emptyList(),
        settings: List<LauncherSearchResult.Setting> = emptyList(),
    ): List<LibraryHit> =
        apps.map { LibraryHit.App(it) } +
            shortcuts.map { LibraryHit.Shortcut(it) } +
            settings.map { LibraryHit.Setting(it) }

    /** The hit Enter acts on, or null when nothing matched. */
    fun topHit(hits: List<LibraryHit>): LibraryHit? = hits.firstOrNull()
}
