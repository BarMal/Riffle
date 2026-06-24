package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.settings.LauncherSettings

@Composable
fun SettingsSurface(
    settings: LauncherSettings,
    homeLayout: HomeLayout,
    notificationAccessStatus: NotificationAccessStatus,
    hiddenApps: List<InstalledApp>,
    onAction: (LauncherShellAction) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            SettingsPageHeader(onAction = onAction)
            Spacer(modifier = Modifier.height(24.dp))
            SettingsPageContent(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .widthIn(max = SETTINGS_PAGE_MAX_WIDTH_DP.dp)
                        .align(Alignment.CenterHorizontally)
                        .verticalScroll(rememberScrollState()),
                settings = settings,
                homeLayout = homeLayout,
                notificationAccessStatus = notificationAccessStatus,
                hiddenApps = hiddenApps,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun ColumnScope.SettingsPageHeader(onAction: (LauncherShellAction) -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .widthIn(max = SETTINGS_PAGE_MAX_WIDTH_DP.dp)
                .align(Alignment.CenterHorizontally),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
        )
        TextButton(onClick = { onAction(LauncherShellAction.OpenHome) }) {
            Text(text = "Home")
        }
    }
}

@Composable
private fun SettingsPageContent(
    modifier: Modifier,
    settings: LauncherSettings,
    homeLayout: HomeLayout,
    notificationAccessStatus: NotificationAccessStatus,
    hiddenApps: List<InstalledApp>,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsSection(title = "Appearance") {
            WallpaperSourceSetting(
                selectedSource = settings.appearance.wallpaper.source,
                onAction = onAction,
            )
            HomeLabelSetting(
                settings = homeLayout.settings.labels,
                onAction = onAction,
            )
        }
        SettingsSection(title = "Home layout") {
            HomeViewModeSetting(
                viewMode = homeLayout.viewMode,
                onAction = onAction,
            )
            HomeGridSetting(
                grid = homeLayout.settings.grid,
                viewMode = homeLayout.viewMode,
                onAction = onAction,
            )
        }
        SettingsSection(title = "Dock") {
            DockSetting(
                dock = homeLayout.dock,
                onAction = onAction,
            )
        }
        SettingsSection(title = "Gestures") {
            HomeSwipeGestureSetting(
                settings = settings.gestures.homeSwipe,
                onAction = onAction,
            )
        }
        SettingsSection(title = "Permissions") {
            NotificationAccessSetting(
                status = notificationAccessStatus,
                onAction = onAction,
            )
        }
        SettingsSection(title = "Hidden apps") {
            HiddenAppsSetting(
                apps = hiddenApps,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = SETTINGS_SECTION_ALPHA),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            content()
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

@Composable
private fun HiddenAppsSetting(
    apps: List<InstalledApp>,
    onAction: (LauncherShellAction) -> Unit,
) {
    if (apps.isEmpty()) {
        Text(
            text = "No hidden apps",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            apps.forEach { app ->
                HiddenAppRow(
                    app = app,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun HiddenAppRow(
    app: InstalledApp,
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
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = app.drawerSubtitle(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { onAction(LauncherShellAction.UnhideApp(app.identity)) }) {
            Text(text = "Unhide")
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

private const val SETTINGS_PAGE_MAX_WIDTH_DP = 840
private const val SETTINGS_SECTION_ALPHA = 0.64f
