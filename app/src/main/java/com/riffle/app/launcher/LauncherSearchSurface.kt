package com.riffle.app.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.AppSearchContentFilter
import com.riffle.core.domain.launcher.apps.AppSearchFilters
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.HomeLabelSettings
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.search.LauncherSearchResult

@Composable
fun SearchSurface(
    state: SearchSurfaceState,
    appListContext: AppListContext,
    onAction: (LauncherShellAction) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    BackHandler {
        if (isKeyboardVisible) {
            keyboardController?.hide()
        } else {
            onAction(LauncherShellAction.OpenHome)
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SearchTopControls(
            state = state,
            focusRequester = focusRequester,
            onQueryChanged = { value -> onAction(LauncherShellAction.SearchQueryChanged(value)) },
            onContentFilterToggled = { filter -> onAction(LauncherShellAction.ToggleSearchContentFilter(filter)) },
            onProfileFilterToggled = { profileType ->
                onAction(LauncherShellAction.ToggleSearchProfileFilter(profileType))
            },
            onResetFilters = onAction,
        )
        Spacer(modifier = Modifier.height(10.dp))
        searchWebPreview(state.query)?.let { preview ->
            SearchWebPanel(
                preview = preview,
                onAction = onAction,
                modifier =
                    Modifier
                        .widthIn(max = SEARCH_GRID_MAX_WIDTH_DP.dp)
                        .fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        SearchIconGrid(
            results =
                searchGridResults(
                    apps = state.results,
                    shortcuts = state.shortcutResults,
                    settings = state.settingsResults,
                ),
            homeLayout = state.homeLayout,
            appListContext = appListContext,
            emptyText =
                searchEmptyText(
                    query = state.query,
                    filters = state.filters,
                ),
            modifier = Modifier.weight(1f),
        )
    }
}

data class SearchSurfaceState(
    val query: String,
    val filters: AppSearchFilters,
    val installedApps: List<InstalledApp>,
    val results: List<InstalledApp>,
    val shortcutResults: List<AppShortcut> = emptyList(),
    val settingsResults: List<LauncherSearchResult.Setting> = emptyList(),
    val homeLayout: HomeLayout,
)

@Composable
private fun SearchTopControls(
    state: SearchSurfaceState,
    focusRequester: FocusRequester,
    onQueryChanged: (String) -> Unit,
    onContentFilterToggled: (AppSearchContentFilter) -> Unit,
    onProfileFilterToggled: (AppProfileType) -> Unit,
    onResetFilters: (LauncherShellAction) -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .widthIn(max = SEARCH_CONTROLS_MAX_WIDTH_DP.dp)
                .fillMaxWidth(),
        shape = RoundedCornerShape(SEARCH_CONTROLS_CORNER_DP.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = SEARCH_CONTROLS_ALPHA),
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppSearchField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    query = state.query,
                    onQueryChanged = onQueryChanged,
                )
            }
            SearchFilterChips(
                filters = state.filters,
                installedApps = state.installedApps,
                onContentFilterToggled = onContentFilterToggled,
                onProfileFilterToggled = onProfileFilterToggled,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = searchFilterSummaryText(state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (shouldShowSearchFilterReset(state.filters)) {
                TextButton(onClick = { onResetFilters(searchFilterResetAction()) }) {
                    Text(text = "Reset filters")
                }
            }
        }
    }
}

@Composable
private fun SearchIconGrid(
    results: List<SearchGridResult>,
    homeLayout: HomeLayout,
    appListContext: AppListContext,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    if (results.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(homeLayout.selectedPage.grid.columns.coerceAtLeast(1)),
        modifier =
            modifier
                .widthIn(max = SEARCH_GRID_MAX_WIDTH_DP.dp)
                .fillMaxWidth(),
        contentPadding =
            PaddingValues(
                start = 4.dp,
                top = 8.dp,
                end = 4.dp,
                bottom = SEARCH_GRID_KEYBOARD_OVERLAY_SCROLL_PADDING_DP.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(
            items = results,
            key = { result -> result.key },
        ) { result ->
            SearchIconGridItem(
                result = result,
                labelSettings = homeLayout.settings.labels,
                appListContext = appListContext,
            )
        }
    }
}

@Composable
private fun SearchIconGridItem(
    result: SearchGridResult,
    labelSettings: HomeLabelSettings,
    appListContext: AppListContext,
) {
    if (result is SearchGridResult.Setting) {
        SearchSettingGridItem(
            result = result,
            labelSettings = labelSettings,
            onAction = appListContext.onAction,
        )
        return
    }

    val appIdentity =
        when (result) {
            is SearchGridResult.App -> result.app.identity
            is SearchGridResult.Shortcut -> result.shortcut.appIdentity
            is SearchGridResult.Setting -> error("Settings results are handled above")
        }
    val metrics = HomeGridLayoutMetrics()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = metrics.homeItemContentHeightDp(labelSettings).dp)
                .clickable { appListContext.onAction(result.action) }
                .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(HOME_ICON_SIZE_DP.dp)) {
            LauncherAppIcon(
                identity = appIdentity,
                label = result.label,
                iconLoader = appListContext.appIconLoader,
                modifier = Modifier.size(HOME_ICON_SIZE_DP.dp),
            )
            NotificationCountBadge(
                count = appListContext.notificationCountFor(appIdentity),
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        WallpaperReadableLabel(
            text = result.label,
            settings = labelSettings,
        )
    }
}

internal fun searchFilterSummaryText(state: SearchSurfaceState): String {
    val resultCount =
        state.results.size +
            state.shortcutResults.size +
            state.settingsResults.size
    val profileLabel =
        when {
            state.filters.profiles.isEmpty() -> "no profiles"
            state.filters.profiles.size == 1 -> state.filters.profiles.single().label.lowercase()
            else -> "selected profiles"
        }
    val contentLabel =
        when {
            state.filters.content.isEmpty() -> "no result types"
            state.filters.content.size == 1 -> state.filters.content.single().label.lowercase()
            else -> "apps and shortcuts"
        }

    return "$resultCount ${"result".pluralized(resultCount)} in $profileLabel $contentLabel"
}

internal fun searchEmptyText(
    query: String,
    filters: AppSearchFilters,
): String =
    when {
        filters.content.isEmpty() -> "Enable apps or shortcuts to search"
        filters.profiles.isEmpty() -> "Enable a profile to search"
        query.isBlank() -> "No ${filters.searchResultTypeNoun()} match the selected filters"
        else -> "No ${filters.searchResultTypeNoun()} found for \"${query.trim()}\""
    }

internal fun shouldShowSearchFilterReset(filters: AppSearchFilters): Boolean = filters != AppSearchFilters()

internal fun searchFilterResetAction(): LauncherShellAction = LauncherShellAction.ResetSearchFilters

private fun AppSearchFilters.searchResultTypeNoun(): String =
    when (content) {
        setOf(AppSearchContentFilter.APPS) -> "apps"
        setOf(AppSearchContentFilter.SHORTCUTS) -> "shortcuts"
        else -> "results"
    }

private fun String.pluralized(count: Int): String = if (count == 1) this else "${this}s"

private const val SEARCH_CONTROLS_MAX_WIDTH_DP = 840
private const val SEARCH_CONTROLS_CORNER_DP = 28
private const val SEARCH_CONTROLS_ALPHA = 0.96f
private const val SEARCH_GRID_MAX_WIDTH_DP = 840
private const val SEARCH_GRID_KEYBOARD_OVERLAY_SCROLL_PADDING_DP = 220
