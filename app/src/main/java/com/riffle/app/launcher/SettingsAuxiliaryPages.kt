package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength

@Composable
internal fun SettingsHapticsPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = "Haptics") {
        HapticStrengthSetting(
            selectedStrength = state.settings.haptics.feedbackStrength,
            onAction = onAction,
        )
    }
}

@Composable
internal fun SettingsPermissionsPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsPermissionsSection(
        notificationAccessStatus = state.notificationAccessStatus,
        overlayDockPermissionStatus = state.overlayDockPermissionStatus,
        onAction = onAction,
    )
}

@Composable
internal fun SettingsAppsPageContent(
    state: SettingsSurfaceState,
    onPageSelected: (SettingsPage) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = "Apps") {
        SettingsListRow(
            title = "Launchable apps",
            subtitle = "${state.installedApps.size.appCountLabel()} available",
        )
        SettingsClickableRow(
            title = "Hidden apps",
            subtitle = "${state.hiddenApps.size.appCountLabel()} hidden",
            onClick = { onPageSelected(SettingsPage.HIDDEN_APPS) },
        )
        AppRefreshSetting(onAction = onAction)
    }
}

@Composable
internal fun SettingsBackupPageContent(onAction: (LauncherShellAction) -> Unit) {
    SettingsSection(title = "Backup") {
        BackupSetting(onAction = onAction)
    }
}

@Composable
internal fun SettingsHiddenAppsPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = "Hidden apps") {
        HiddenAppsSetting(
            apps = state.hiddenApps,
            onAction = onAction,
        )
    }
}

@Composable
internal fun SettingsVersionPageContent(state: SettingsSurfaceState) {
    SettingsSection(title = "Version") {
        VersionInformationSetting(
            appVersionLabel = state.appVersionLabel,
            appBuildIdentityLabel = state.appBuildIdentityLabel,
        )
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
private fun HiddenAppsSetting(
    apps: List<InstalledApp>,
    onAction: (LauncherShellAction) -> Unit,
) {
    val query = remember { mutableStateOf("") }
    val profileFilter = remember { mutableStateOf(AppDrawerProfileFilter.ALL) }
    val filteredApps =
        apps.filteredHiddenApps(
            query = query.value,
            profileFilter = profileFilter.value,
        )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppSearchField(
            modifier = Modifier.fillMaxWidth(),
            query = query.value,
            onQueryChanged = { value -> query.value = value },
            label = "Search hidden apps",
        )
        AppProfileFilterChips(
            selectedFilter = profileFilter.value,
            onFilterSelected = { filter -> profileFilter.value = filter },
            apps = apps,
        )
        Text(
            text =
                hiddenAppsSummaryText(
                    totalHiddenAppCount = apps.size,
                    resultCount = filteredApps.size,
                    query = query.value,
                    profileFilter = profileFilter.value,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (shouldShowHiddenAppsClearFilters(query = query.value, profileFilter = profileFilter.value)) {
            TextButton(
                onClick = {
                    query.value = ""
                    profileFilter.value = AppDrawerProfileFilter.ALL
                },
            ) {
                Text(text = "Clear filters")
            }
        }
        if (filteredApps.isEmpty()) {
            Text(
                text =
                    hiddenAppsEmptyText(
                        totalHiddenAppCount = apps.size,
                        query = query.value,
                        profileFilter = profileFilter.value,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        filteredApps.forEach { app ->
            HiddenAppRow(
                app = app,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun HiddenAppRow(
    app: InstalledApp,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsClickableRow(
        title = app.label,
        subtitle = app.drawerSubtitle(),
        onClick = { onAction(LauncherShellAction.UnhideApp(app.identity)) },
        trailingContent = {
            SettingsButtonText(text = "Unhide")
        },
    )
}

private val HapticFeedbackStrength.label: String
    get() =
        when (this) {
            HapticFeedbackStrength.OFF -> "Off"
            HapticFeedbackStrength.LIGHT -> "Light"
            HapticFeedbackStrength.MEDIUM -> "Medium"
            HapticFeedbackStrength.STRONG -> "Strong"
        }
