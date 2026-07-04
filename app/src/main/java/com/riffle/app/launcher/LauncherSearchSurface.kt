package com.riffle.app.launcher

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppSearchScope
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.HomeLabelSettings
import com.riffle.core.domain.launcher.home.HomeLayout

@Composable
fun SearchSurface(
    state: SearchSurfaceState,
    appListContext: AppListContext,
    onAction: (LauncherShellAction) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SearchTopControls(
            query = state.query,
            profileFilter = state.profileFilter,
            searchScope = state.searchScope,
            installedApps = state.installedApps,
            resultCount = state.results.size,
            focusRequester = focusRequester,
            onAction = onAction,
        )
        Spacer(modifier = Modifier.height(14.dp))
        SearchIconGrid(
            apps = searchGridApps(state.results),
            homeLayout = state.homeLayout,
            appListContext = appListContext,
            emptyText =
                appListEmptyText(
                    surface = AppListSurface.SEARCH,
                    query = state.query,
                    profileFilter = state.profileFilter,
                ),
            modifier = Modifier.weight(1f),
        )
    }
}

data class SearchSurfaceState(
    val query: String,
    val profileFilter: AppDrawerProfileFilter,
    val searchScope: AppSearchScope,
    val installedApps: List<InstalledApp>,
    val results: List<InstalledApp>,
    val homeLayout: HomeLayout,
)

@Composable
private fun SearchTopControls(
    query: String,
    profileFilter: AppDrawerProfileFilter,
    searchScope: AppSearchScope,
    installedApps: List<InstalledApp>,
    resultCount: Int,
    focusRequester: FocusRequester,
    onAction: (LauncherShellAction) -> Unit,
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
                            .weight(1f)
                            .focusRequester(focusRequester),
                    query = query,
                    onQueryChanged = { value -> onAction(LauncherShellAction.SearchQueryChanged(value)) },
                )
                TextButton(onClick = { onAction(LauncherShellAction.OpenHome) }) {
                    Text(text = "Home")
                }
            }
            SearchFilterChips(
                searchScope = searchScope,
                profileFilter = profileFilter,
                installedApps = installedApps,
                onScopeSelected = { scope -> onAction(LauncherShellAction.SearchScopeSelected(scope)) },
                onProfileFilterSelected = { filter ->
                    onAction(LauncherShellAction.SearchProfileFilterSelected(filter))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text =
                    appListSummaryText(
                        totalAppCount = installedApps.size,
                        resultCount = resultCount,
                        query = query,
                        profileFilter = profileFilter,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchIconGrid(
    apps: List<InstalledApp>,
    homeLayout: HomeLayout,
    appListContext: AppListContext,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    if (apps.isEmpty()) {
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
            items = apps,
            key = { app -> app.identity.stableSearchGridKey },
        ) { app ->
            SearchIconGridItem(
                app = app,
                labelSettings = homeLayout.settings.labels,
                appListContext = appListContext,
            )
        }
    }
}

@Composable
private fun SearchIconGridItem(
    app: InstalledApp,
    labelSettings: HomeLabelSettings,
    appListContext: AppListContext,
) {
    val metrics = HomeGridLayoutMetrics()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = metrics.homeItemContentHeightDp(labelSettings).dp)
                .clickable { appListContext.onAction(LauncherShellAction.LaunchApp(app.identity)) }
                .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(HOME_ICON_SIZE_DP.dp)) {
            LauncherAppIcon(
                identity = app.identity,
                label = app.label,
                iconLoader = appListContext.appIconLoader,
                modifier = Modifier.size(HOME_ICON_SIZE_DP.dp),
            )
            NotificationCountBadge(
                count = appListContext.notificationCountsByPackage[app.identity.packageName] ?: 0,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        WallpaperReadableLabel(
            text = app.label,
            settings = labelSettings,
        )
    }
}

private val AppIdentity.stableSearchGridKey: String
    get() = "${packageName.value}/${activityName.value}/${profile.id.value}"

private const val SEARCH_CONTROLS_MAX_WIDTH_DP = 840
private const val SEARCH_CONTROLS_CORNER_DP = 28
private const val SEARCH_CONTROLS_ALPHA = 0.96f
private const val SEARCH_GRID_MAX_WIDTH_DP = 840
private const val SEARCH_GRID_KEYBOARD_OVERLAY_SCROLL_PADDING_DP = 220
