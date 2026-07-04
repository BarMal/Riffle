package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.InstalledApp

@Composable
fun AppDrawer(
    query: String,
    profileFilter: AppDrawerProfileFilter,
    installedApps: List<InstalledApp>,
    apps: List<InstalledApp>,
    appListContext: AppListContext,
    onAction: (LauncherShellAction) -> Unit,
) {
    LauncherPanel(
        title =
            appPanelTitle(
                baseTitle = "Apps",
                resultCount = apps.size,
                query = query,
                profileFilter = profileFilter,
            ),
        onAction = onAction,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppSearchField(
                modifier = Modifier.fillMaxWidth(),
                query = query,
                onQueryChanged = { value -> onAction(LauncherShellAction.AppDrawerQueryChanged(value)) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppProfileFilterChips(
                selectedFilter = profileFilter,
                onFilterSelected = { filter -> onAction(LauncherShellAction.AppDrawerProfileFilterSelected(filter)) },
                apps = installedApps,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    appListSummaryText(
                        totalAppCount = installedApps.size,
                        resultCount = apps.size,
                        query = query,
                        profileFilter = profileFilter,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppList(
                modifier = Modifier.weight(1f),
                apps = apps,
                emptyText =
                    appListEmptyText(
                        surface = AppListSurface.DRAWER,
                        query = query,
                        profileFilter = profileFilter,
                    ),
                context = appListContext,
                showSections = true,
            )
        }
    }
}

@Composable
fun SearchSurface(
    query: String,
    profileFilter: AppDrawerProfileFilter,
    installedApps: List<InstalledApp>,
    results: List<InstalledApp>,
    appListContext: AppListContext,
    onAction: (LauncherShellAction) -> Unit,
) {
    LauncherPanel(
        title =
            appPanelTitle(
                baseTitle = "Search",
                resultCount = results.size,
                query = query,
                profileFilter = profileFilter,
            ),
        onAction = onAction,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppSearchField(
                modifier = Modifier.fillMaxWidth(),
                query = query,
                onQueryChanged = { value -> onAction(LauncherShellAction.SearchQueryChanged(value)) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppProfileFilterChips(
                selectedFilter = profileFilter,
                onFilterSelected = { filter -> onAction(LauncherShellAction.SearchProfileFilterSelected(filter)) },
                apps = installedApps,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    appListSummaryText(
                        totalAppCount = installedApps.size,
                        resultCount = results.size,
                        query = query,
                        profileFilter = profileFilter,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppList(
                modifier = Modifier.weight(1f),
                apps = results,
                emptyText =
                    appListEmptyText(
                        surface = AppListSurface.SEARCH,
                        query = query,
                        profileFilter = profileFilter,
                    ),
                context = appListContext,
                showInlineActions = false,
            )
        }
    }
}

@Composable
fun LauncherPanel(
    title: String,
    onAction: (LauncherShellAction) -> Unit,
    showSettingsAction: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                Modifier
                    .widthIn(max = PANEL_MAX_WIDTH_DP.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = PANEL_SURFACE_ALPHA),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (showSettingsAction) {
                            TextButton(onClick = { onAction(LauncherShellAction.OpenSettings) }) {
                                Text(text = "Settings")
                            }
                        }
                        TextButton(onClick = { onAction(LauncherShellAction.OpenHome) }) {
                            Text(text = "Home")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    content()
                }
            }
        }
    }
}

private const val PANEL_MAX_WIDTH_DP = 840
private const val PANEL_SURFACE_ALPHA = 0.96f
