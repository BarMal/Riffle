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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength

@Composable
fun SettingsSurface(
    state: SettingsSurfaceState,
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
            SettingsPageHeader(
                appVersionLabel = state.appVersionLabel,
                onAction = onAction,
            )
            Spacer(modifier = Modifier.height(24.dp))
            SettingsPageContent(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .widthIn(max = SETTINGS_PAGE_MAX_WIDTH_DP.dp)
                        .align(Alignment.CenterHorizontally)
                        .verticalScroll(rememberScrollState()),
                state = state,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun ColumnScope.SettingsPageHeader(
    appVersionLabel: String,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .widthIn(max = SETTINGS_PAGE_MAX_WIDTH_DP.dp)
                .align(Alignment.CenterHorizontally),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = appVersionLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { onAction(LauncherShellAction.OpenHome) }) {
            SettingsButtonText(text = "Home")
        }
    }
}

@Composable
private fun SettingsPageContent(
    modifier: Modifier,
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsLayoutDeviceTabs(
            selectedDeviceClass = state.selectedLayoutDeviceClass,
            onAction = onAction,
        )
        SettingsSection(title = "Appearance") {
            WallpaperSourceSetting(
                selectedSource = state.settings.appearance.wallpaper.source,
                onAction = onAction,
            )
            HomeLabelSetting(
                settings = state.homeLayout.settings.labels,
                onAction = onAction,
            )
        }
        SettingsSection(title = "Home layout") {
            HomeViewModeSetting(
                viewMode = state.homeLayout.viewMode,
                onAction = onAction,
            )
            HomeGridSetting(
                grid = state.homeLayout.settings.grid,
                viewMode = state.homeLayout.viewMode,
                onAction = onAction,
            )
        }
        SettingsSection(title = "Dock") {
            DockSetting(
                dock = state.homeLayout.dock,
                onAction = onAction,
            )
        }
        SettingsSection(title = "Gestures") {
            HomeSwipeGestureSetting(
                settings = state.settings.gestures.homeSwipe,
                onAction = onAction,
            )
        }
        SettingsSection(title = "Haptics") {
            HapticStrengthSetting(
                selectedStrength = state.settings.haptics.feedbackStrength,
                onAction = onAction,
            )
        }
        SettingsSection(title = "Permissions") {
            NotificationAccessSetting(
                status = state.notificationAccessStatus,
                onAction = onAction,
            )
        }
        SettingsSection(title = "Apps") {
            AppRefreshSetting(onAction = onAction)
        }
        SettingsSection(title = "Backup") {
            BackupSetting(onAction = onAction)
        }
        SettingsSection(title = "Hidden apps") {
            HiddenAppsSetting(
                apps = state.hiddenApps,
                onAction = onAction,
            )
        }
        SettingsSection(title = "Version") {
            VersionInformationSetting(
                appVersionLabel = state.appVersionLabel,
                appBuildIdentityLabel = state.appBuildIdentityLabel,
            )
        }
    }
}

@Composable
private fun VersionInformationSetting(
    appVersionLabel: String,
    appBuildIdentityLabel: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsTextColumn(
            title = "Version",
            subtitle = appVersionLabel,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SettingsPrimaryText(text = "Build")
            Text(
                text = appBuildIdentityLabel,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                softWrap = true,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HapticStrengthSetting(
    selectedStrength: HapticFeedbackStrength,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Feedback strength",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HapticFeedbackStrength.entries.forEach { strength ->
                TextButton(
                    enabled = strength != selectedStrength,
                    onClick = { onAction(LauncherShellAction.SelectHapticFeedbackStrength(strength)) },
                ) {
                    SettingsButtonText(text = strength.label)
                }
            }
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Wallpaper",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = WallpaperSource.SYSTEM != selectedSource,
                onClick = { onAction(LauncherShellAction.SelectWallpaperSource(WallpaperSource.SYSTEM)) },
            ) {
                SettingsButtonText(text = "System")
            }
            TextButton(
                enabled = WallpaperSource.SOLID_COLOR != selectedSource,
                onClick = { onAction(LauncherShellAction.SelectWallpaperSource(WallpaperSource.SOLID_COLOR)) },
            ) {
                SettingsButtonText(text = "Solid")
            }
        }
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
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Notifications",
            subtitle = status.label,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onAction(LauncherShellAction.OpenNotifications) }) {
                SettingsButtonText(text = "View")
            }
            TextButton(onClick = { onAction(LauncherShellAction.RequestNotificationAccess) }) {
                SettingsButtonText(text = "Open")
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
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = app.label,
            subtitle = app.drawerSubtitle(),
        )
        TextButton(onClick = { onAction(LauncherShellAction.UnhideApp(app.identity)) }) {
            SettingsButtonText(text = "Unhide")
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

private val HapticFeedbackStrength.label: String
    get() =
        when (this) {
            HapticFeedbackStrength.OFF -> "Off"
            HapticFeedbackStrength.LIGHT -> "Light"
            HapticFeedbackStrength.MEDIUM -> "Medium"
            HapticFeedbackStrength.STRONG -> "Strong"
        }

private const val SETTINGS_PAGE_MAX_WIDTH_DP = 840
