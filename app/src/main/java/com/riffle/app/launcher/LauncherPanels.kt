package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.settings.LauncherSettings

@Composable
fun AppDrawer(
    query: String,
    profileFilter: AppDrawerProfileFilter,
    apps: List<InstalledApp>,
    homeLayout: HomeLayout,
    notificationCountsByPackage: Map<AppPackageName, Int>,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    LauncherPanel(
        title = "Apps",
        onAction = onAction,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppSearchField(
                modifier = Modifier.fillMaxWidth(),
                query = query,
                onQueryChanged = { value -> onAction(LauncherShellAction.AppDrawerQueryChanged(value)) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppDrawerProfileFilters(
                selectedFilter = profileFilter,
                onAction = onAction,
            )
            Spacer(modifier = Modifier.height(16.dp))
            AppList(
                modifier = Modifier.weight(1f),
                apps = apps,
                emptyText =
                    if (query.isBlank()) {
                        "No launchable apps found"
                    } else {
                        "No matching apps"
                    },
                context =
                    AppListContext(
                        homeLayout = homeLayout,
                        notificationCountsByPackage = notificationCountsByPackage,
                        appIconLoader = appIconLoader,
                        onAction = onAction,
                    ),
                showSections = true,
            )
        }
    }
}

@Composable
private fun AppDrawerProfileFilters(
    selectedFilter: AppDrawerProfileFilter,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppDrawerProfileFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selectedFilter,
                onClick = { onAction(LauncherShellAction.AppDrawerProfileFilterSelected(filter)) },
                label = { Text(text = filter.label) },
            )
        }
    }
}

private val AppDrawerProfileFilter.label: String
    get() =
        when (this) {
            AppDrawerProfileFilter.ALL -> "All"
            AppDrawerProfileFilter.PERSONAL -> "Personal"
            AppDrawerProfileFilter.WORK -> "Work"
        }

@Composable
fun SearchSurface(
    query: String,
    results: List<InstalledApp>,
    homeLayout: HomeLayout,
    notificationCountsByPackage: Map<AppPackageName, Int>,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    LauncherPanel(
        title = "Search",
        onAction = onAction,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppSearchField(
                modifier = Modifier.fillMaxWidth(),
                query = query,
                onQueryChanged = { value -> onAction(LauncherShellAction.SearchQueryChanged(value)) },
            )
            Spacer(modifier = Modifier.height(16.dp))
            AppList(
                modifier = Modifier.weight(1f),
                apps = results,
                emptyText = "No matching apps",
                context =
                    AppListContext(
                        homeLayout = homeLayout,
                        notificationCountsByPackage = notificationCountsByPackage,
                        appIconLoader = appIconLoader,
                        onAction = onAction,
                    ),
            )
        }
    }
}

@Composable
fun SettingsSurface(
    settings: LauncherSettings,
    notificationAccessStatus: NotificationAccessStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    LauncherPanel(
        title = "Settings",
        onAction = onAction,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
            )
            WallpaperSourceSetting(
                selectedSource = settings.appearance.wallpaper.source,
                onAction = onAction,
            )
            Text(
                text = "Gestures",
                style = MaterialTheme.typography.titleMedium,
            )
            HomeSwipeGestureSetting(
                settings = settings.gestures.homeSwipe,
                onAction = onAction,
            )
            Text(
                text = "Permissions",
                style = MaterialTheme.typography.titleMedium,
            )
            NotificationAccessSetting(
                status = notificationAccessStatus,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun WallpaperSourceSetting(
    selectedSource: WallpaperSource,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = "Wallpaper",
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WallpaperSourceButton(
                label = "System",
                source = WallpaperSource.SYSTEM,
                selectedSource = selectedSource,
                onAction = onAction,
            )
            WallpaperSourceButton(
                label = "Solid",
                source = WallpaperSource.SOLID_COLOR,
                selectedSource = selectedSource,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun WallpaperSourceButton(
    label: String,
    source: WallpaperSource,
    selectedSource: WallpaperSource,
    onAction: (LauncherShellAction) -> Unit,
) {
    TextButton(
        enabled = source != selectedSource,
        onClick = { onAction(LauncherShellAction.SelectWallpaperSource(source)) },
    ) {
        Text(text = label)
    }
}

@Composable
private fun NotificationAccessSetting(
    status: NotificationAccessStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = status.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onAction(LauncherShellAction.OpenNotifications) }) {
                Text(text = "View")
            }
            TextButton(onClick = { onAction(LauncherShellAction.RequestNotificationAccess) }) {
                Text(text = "Open")
            }
        }
    }
}

private val NotificationAccessStatus.label: String
    get() =
        when (this) {
            NotificationAccessStatus.UNKNOWN -> "Unknown"
            NotificationAccessStatus.GRANTED -> "Allowed"
            NotificationAccessStatus.NOT_GRANTED -> "Not allowed"
        }

@Composable
fun LauncherPanel(
    title: String,
    onAction: (LauncherShellAction) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
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
            TextButton(onClick = { onAction(LauncherShellAction.OpenHome) }) {
                Text(text = "Home")
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
