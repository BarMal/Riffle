@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.AppSearchContentFilter
import com.riffle.core.domain.launcher.apps.AppSearchFilters
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.containsHomeApp
import com.riffle.core.domain.launcher.home.containsHomeAppShortcut
import com.riffle.core.domain.launcher.search.LauncherSearchResult
import com.riffle.core.domain.launcher.settings.SearchResultPresentation
import androidx.compose.foundation.lazy.grid.items as gridItems

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
        Spacer(modifier = Modifier.height(8.dp))
        SearchResultList(
            results =
                searchGridResults(
                    apps = state.results,
                    shortcuts = state.shortcutResults,
                    settings = state.settingsResults,
                ),
            appListContext = appListContext,
            emptyText =
                searchEmptyText(
                    query = state.query,
                    filters = state.filters,
                ),
            presentation = state.resultPresentation,
            webPreview = searchWebPreview(state.query),
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
    val resultPresentation: SearchResultPresentation = SearchResultPresentation.ICONS,
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
            if (shouldShowSearchFilterReset(state.filters)) {
                TextButton(onClick = { onResetFilters(searchFilterResetAction()) }) {
                    Text(text = "Reset filters")
                }
            }
        }
    }
}

@Composable
private fun SearchResultList(
    results: List<SearchGridResult>,
    appListContext: AppListContext,
    emptyText: String,
    presentation: SearchResultPresentation,
    webPreview: SearchWebPreview?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .widthIn(max = SEARCH_GRID_MAX_WIDTH_DP.dp)
                .fillMaxWidth(),
    ) {
        if (results.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            when (presentation) {
                SearchResultPresentation.ICONS ->
                    SearchResultIconGrid(
                        results = results,
                        appListContext = appListContext,
                        modifier = Modifier.weight(1f),
                    )

                SearchResultPresentation.LIST ->
                    SearchResultListItems(
                        results = results,
                        appListContext = appListContext,
                        modifier = Modifier.weight(1f),
                    )
            }
        }
        webPreview?.let { preview ->
            SearchWebPanel(
                preview = preview,
                onAction = appListContext.onAction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SearchResultListItems(
    results: List<SearchGridResult>,
    appListContext: AppListContext,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag(SEARCH_RESULT_LIST_TEST_TAG),
        contentPadding = searchResultContentPadding(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(items = results, key = { result -> result.key }) { result ->
            SearchResultListItem(result = result, appListContext = appListContext)
        }
    }
}

@Composable
private fun SearchResultIconGrid(
    results: List<SearchGridResult>,
    appListContext: AppListContext,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = SEARCH_ICON_TILE_MIN_WIDTH_DP.dp),
        modifier = modifier.fillMaxWidth().testTag(SEARCH_RESULT_ICON_GRID_TEST_TAG),
        contentPadding = searchResultContentPadding(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        gridItems(items = results, key = { result -> result.key }) { result ->
            SearchResultIconTile(result = result, appListContext = appListContext)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SearchResultListItem(
    result: SearchGridResult,
    appListContext: AppListContext,
) {
    val appIdentity =
        when (result) {
            is SearchGridResult.App -> result.app.identity
            is SearchGridResult.Shortcut -> result.shortcut.appIdentity
            is SearchGridResult.Setting -> null
        }
    val supportingText =
        when (result) {
            is SearchGridResult.App -> "App"
            is SearchGridResult.Shortcut -> "App shortcut"
            is SearchGridResult.Setting -> result.subtitle
        }

    val isMenuExpanded = remember(result.key) { mutableStateOf(false) }
    Box {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = SEARCH_RESULT_MIN_HEIGHT_DP.dp)
                    .combinedClickable(
                        onClick = { appListContext.onAction(result.action) },
                        onLongClick = result.longClickAction(appListContext, isMenuExpanded),
                        onLongClickLabel = result.longClickLabel,
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (appIdentity == null) {
                Surface(
                    modifier = Modifier.size(SEARCH_RESULT_ICON_SIZE_DP.dp),
                    shape = RoundedCornerShape(SEARCH_RESULT_ICON_CORNER_DP.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "SET",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.size(SEARCH_RESULT_ICON_SIZE_DP.dp)) {
                    LauncherAppIcon(
                        identity = appIdentity,
                        label = result.label,
                        iconLoader = appListContext.appIconLoader,
                        modifier = Modifier.size(SEARCH_RESULT_ICON_SIZE_DP.dp),
                    )
                    NotificationCountBadge(
                        count = appListContext.notificationCountFor(appIdentity),
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SearchResultContextMenu(
            result = result,
            appListContext = appListContext,
            expanded = isMenuExpanded.value,
            onExpandedChange = { expanded -> isMenuExpanded.value = expanded },
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SearchResultIconTile(
    result: SearchGridResult,
    appListContext: AppListContext,
) {
    val isMenuExpanded = remember(result.key) { mutableStateOf(false) }
    val appIdentity = result.appIdentity
    Box {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = SEARCH_ICON_TILE_MIN_HEIGHT_DP.dp)
                    .combinedClickable(
                        onClick = { appListContext.onAction(result.action) },
                        onLongClick = result.longClickAction(appListContext, isMenuExpanded),
                        onLongClickLabel = result.longClickLabel,
                    )
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (appIdentity == null) {
                SearchSettingIcon()
            } else {
                Box(modifier = Modifier.size(SEARCH_ICON_TILE_SIZE_DP.dp)) {
                    LauncherAppIcon(
                        identity = appIdentity,
                        label = result.label,
                        iconLoader = appListContext.appIconLoader,
                        modifier = Modifier.size(SEARCH_ICON_TILE_SIZE_DP.dp),
                    )
                    NotificationCountBadge(
                        count = appListContext.notificationCountFor(appIdentity),
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
            Text(
                text = result.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SearchResultContextMenu(
            result = result,
            appListContext = appListContext,
            expanded = isMenuExpanded.value,
            onExpandedChange = { expanded -> isMenuExpanded.value = expanded },
        )
    }
}

@Composable
private fun SearchSettingIcon() {
    Surface(
        modifier = Modifier.size(SEARCH_ICON_TILE_SIZE_DP.dp),
        shape = RoundedCornerShape(SEARCH_RESULT_ICON_CORNER_DP.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "SET",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun SearchResultContextMenu(
    result: SearchGridResult,
    appListContext: AppListContext,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    if (result is SearchGridResult.Setting) return
    DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
        when (result) {
            is SearchGridResult.App -> {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (appListContext.homeLayout.containsHomeApp(result.app.identity)) {
                                "Added to home"
                            } else {
                                "Add to home"
                            },
                        )
                    },
                    enabled = !appListContext.homeLayout.containsHomeApp(result.app.identity),
                    onClick = {
                        onExpandedChange(false)
                        appListContext.onAction(LauncherShellAction.AddAppToHome(result.app))
                    },
                )
                DropdownMenuItem(
                    text = { Text("App info") },
                    onClick = {
                        onExpandedChange(false)
                        appListContext.onAction(LauncherShellAction.OpenAppInfo(result.app.identity))
                    },
                )
            }

            is SearchGridResult.Shortcut ->
                DropdownMenuItem(
                    text = { Text("Add shortcut to home") },
                    enabled =
                        !appListContext.homeLayout.containsHomeAppShortcut(
                            result.shortcut.appIdentity,
                            result.shortcut.id,
                        ),
                    onClick = {
                        onExpandedChange(false)
                        appListContext.onAction(LauncherShellAction.AddAppShortcutToHome(result.shortcut))
                    },
                )

            is SearchGridResult.Setting -> Unit
        }
    }
}

private val SearchGridResult.appIdentity
    get() =
        when (this) {
            is SearchGridResult.App -> app.identity
            is SearchGridResult.Shortcut -> shortcut.appIdentity
            is SearchGridResult.Setting -> null
        }

private val SearchGridResult.longClickLabel: String?
    get() =
        when (this) {
            is SearchGridResult.App,
            is SearchGridResult.Shortcut,
            -> "Show $label actions"

            is SearchGridResult.Setting -> null
        }

private fun SearchGridResult.longClickAction(
    appListContext: AppListContext,
    menuExpanded: androidx.compose.runtime.MutableState<Boolean>,
): (() -> Unit)? =
    if (this is SearchGridResult.Setting) {
        null
    } else {
        {
            appListContext.haptics.longPress()
            menuExpanded.value = true
        }
    }

private fun searchResultContentPadding(): PaddingValues =
    PaddingValues(start = 4.dp, top = 8.dp, end = 4.dp, bottom = SEARCH_RESULT_BOTTOM_PADDING_DP.dp)

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

private const val SEARCH_CONTROLS_MAX_WIDTH_DP = 840
private const val SEARCH_CONTROLS_CORNER_DP = 28
private const val SEARCH_CONTROLS_ALPHA = 0.96f
private const val SEARCH_GRID_MAX_WIDTH_DP = 840
private const val SEARCH_RESULT_BOTTOM_PADDING_DP = 32
private const val SEARCH_RESULT_MIN_HEIGHT_DP = 64
private const val SEARCH_RESULT_ICON_SIZE_DP = 40
private const val SEARCH_RESULT_ICON_CORNER_DP = 12
internal const val SEARCH_RESULT_LIST_TEST_TAG = "search-result-list"
internal const val SEARCH_RESULT_ICON_GRID_TEST_TAG = "search-result-icon-grid"
private const val SEARCH_ICON_TILE_MIN_WIDTH_DP = 84
private const val SEARCH_ICON_TILE_MIN_HEIGHT_DP = 104
private const val SEARCH_ICON_TILE_SIZE_DP = 52
