package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.WallpaperSource

@Composable
internal fun SettingsPageContent(
    modifier: Modifier,
    state: SettingsSurfaceState,
    page: SettingsPage,
    onPageSelected: (SettingsPage) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (page) {
            SettingsPage.MAIN ->
                SettingsMainPageContent(
                    state = state,
                    onPageSelected = onPageSelected,
                    onAction = onAction,
                )

            SettingsPage.LAYOUT -> SettingsLayoutPageContent(state = state, onAction = onAction)
            SettingsPage.DOCK -> SettingsDockPageContent(state = state, onAction = onAction)
            SettingsPage.APPEARANCE -> SettingsAppearancePageContent(state = state, onAction = onAction)
            SettingsPage.FLOATING_DOCK -> SettingsFloatingDockPageContent(state = state, onAction = onAction)
            SettingsPage.GESTURES -> SettingsGesturesPageContent(state = state, onAction = onAction)
            SettingsPage.HAPTICS -> SettingsHapticsPageContent(state = state, onAction = onAction)
            SettingsPage.PERMISSIONS -> SettingsPermissionsPageContent(state = state, onAction = onAction)
            SettingsPage.APPS ->
                SettingsAppsPageContent(
                    state = state,
                    onPageSelected = onPageSelected,
                    onAction = onAction,
                )
            SettingsPage.BACKUP -> SettingsBackupPageContent(onAction = onAction)
            SettingsPage.HIDDEN_APPS -> SettingsHiddenAppsPageContent(state = state, onAction = onAction)
            SettingsPage.VERSION -> SettingsVersionPageContent(state = state)
        }
    }
}

@Composable
private fun SettingsMainPageContent(
    state: SettingsSurfaceState,
    onPageSelected: (SettingsPage) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    val settingsQuery = remember { mutableStateOf("") }

    SettingsLauncherSection(
        status = state.homeRoleStatus,
        onAction = onAction,
    )
    AppSearchField(
        modifier = Modifier.fillMaxWidth(),
        query = settingsQuery.value,
        onQueryChanged = { query -> settingsQuery.value = query },
        label = "Search settings",
    )
    val entries =
        settingsMainPageEntriesMatching(
            query = settingsQuery.value,
            status = state.settingsOverviewStatus(),
        )
    if (entries.isEmpty()) {
        Text(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp),
            text = "No settings found",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        settingsMainPageGroups().forEach { group ->
            entries
                .filter { entry -> entry.group == group }
                .takeIf { groupEntries -> groupEntries.isNotEmpty() }
                ?.let { groupEntries ->
                    SettingsSection(title = group.title) {
                        groupEntries.forEach { entry ->
                            SettingsPageEntryRow(
                                entry = entry,
                                onPageSelected = onPageSelected,
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun SettingsPageEntryRow(
    entry: SettingsPageEntry,
    onPageSelected: (SettingsPage) -> Unit,
) {
    SettingsClickableRow(
        title = entry.label,
        subtitle = entry.subtitle,
        onClick = { onPageSelected(entry.page) },
    )
}

@Composable
private fun SettingsDeviceConfigurationTabs(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsLayoutDeviceTabs(
        selectedDeviceClass = state.selectedLayoutDeviceClass,
        availableDeviceClasses = state.availableLayoutDeviceClasses,
        onAction = onAction,
    )
}

@Composable
private fun SettingsLayoutPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsDeviceConfigurationTabs(state = state, onAction = onAction)
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
    SettingsSection(title = "Labels") {
        HomeLabelSetting(
            settings = state.homeLayout.settings.labels,
            onAction = onAction,
        )
    }
}

@Composable
private fun SettingsDockPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsDeviceConfigurationTabs(state = state, onAction = onAction)
    SettingsSection(title = "Dock") {
        DockSetting(
            dock = state.homeLayout.dock,
            onAction = onAction,
        )
    }
}

@Composable
private fun SettingsAppearancePageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = "Wallpaper") {
        WallpaperSourceSetting(
            selectedSource = state.settings.appearance.wallpaper.source,
            onAction = onAction,
        )
    }
}

@Composable
private fun SettingsFloatingDockPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = "Floating dock") {
        OverlayDockSetting(
            settings = state.settings.overlayDock,
            onAction = onAction,
        )
    }
}

@Composable
private fun SettingsGesturesPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = "Gestures") {
        HomeSwipeGestureSetting(
            settings = state.settings.gestures.homeSwipe,
            onAction = onAction,
        )
    }
}

@Composable
private fun WallpaperSourceSetting(
    selectedSource: WallpaperSource,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsListRow(
        title = "Wallpaper",
        trailingContent = {
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
        },
    )
}
