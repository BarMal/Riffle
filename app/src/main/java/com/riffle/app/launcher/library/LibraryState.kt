package com.riffle.app.launcher.library

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppSearchFilters
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.matches
import com.riffle.core.domain.launcher.library.LibraryCategories
import com.riffle.core.domain.launcher.library.LibraryHit
import com.riffle.core.domain.launcher.library.LibrarySearch
import com.riffle.core.domain.launcher.library.LibraryView
import com.riffle.core.domain.launcher.settings.SearchResultPresentation

/**
 * Everything the Library sheet draws, read from the shell state: the view the active device class stores, its apps
 * (profile filter applied, A-Z) and, while the search field has text, the results in [LibrarySearch] order.
 */
internal data class LibraryState(
    val view: LibraryView,
    val iconGridColumns: Int,
    /** The apps of the selected profile, A-Z. Hidden apps are never in the shell's list, so they are not here. */
    val apps: List<InstalledApp>,
    /** Every visible app, for the profile chips' counts. */
    val installedApps: List<InstalledApp>,
    val profileFilter: AppDrawerProfileFilter,
    val query: String,
    val hits: List<LibraryHit>,
    val filters: AppSearchFilters,
    val searchPresentation: SearchResultPresentation,
    val searchGridColumns: Int,
    /** Opened by swipe down or `OPEN_SEARCH`: the field takes focus and the keyboard comes up. */
    val focusSearch: Boolean,
    /** Opened over the Stream: closing returns there. */
    val overStream: Boolean,
) {
    val isSearching: Boolean get() = query.isNotBlank()
}

internal fun LauncherShellState.libraryState(): LibraryState =
    LibraryState(
        view = launcherSettings.appDrawer.libraryViewFor(homeLayoutSet.activeKey.deviceClass),
        iconGridColumns = launcherSettings.appDrawer.iconGridColumns,
        apps =
            installedApps
                .filter { app -> appDrawerProfileFilter.matches(app) }
                .sortedWith(LibraryCategories.appOrder),
        installedApps = installedApps,
        profileFilter = appDrawerProfileFilter,
        query = searchQuery,
        hits =
            LibrarySearch.order(
                apps = searchResults,
                shortcuts = searchShortcutResults,
                settings = searchSettingsResults,
            ),
        filters = searchFilters,
        searchPresentation = launcherSettings.search.resultPresentation,
        searchGridColumns = homeLayout.selectedPage.grid.columns,
        focusSearch = destination == ShellDestination.SEARCH,
        overStream = libraryOverStream,
    )
