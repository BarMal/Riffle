package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength

@Composable
internal fun SettingsHiddenAppsPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.settings_page_hidden_apps)) {
        HiddenAppsSetting(
            apps = state.hiddenApps,
            onAction = onAction,
        )
    }
}

@Composable
internal fun SettingsVersionPageContent(
    state: SettingsSurfaceState,
    onPageSelected: (SettingsPage) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.settings_page_version)) {
        VersionInformationSetting(
            appVersionLabel = state.appVersionLabel,
            appBuildIdentityLabel = state.appBuildIdentityLabel,
        )
    }
    SettingsSection(title = stringResource(R.string.settings_section_diagnostics)) {
        SettingsClickableRow(
            title = stringResource(R.string.settings_page_app_publish),
            subtitle = stringResource(R.string.settings_page_app_publish_summary),
            onClick = { onPageSelected(SettingsPage.APP_PUBLISH) },
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
internal fun HapticStrengthSetting(
    selectedStrength: HapticFeedbackStrength,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsChoiceRow(
        modifier = Modifier.settingAnchor(SettingKey.MOTION_HAPTICS),
        title = SettingKey.MOTION_HAPTICS.label(),
        options = HapticFeedbackStrength.entries,
        selected = selectedStrength,
        onSelect = { strength -> onAction(LauncherShellAction.SelectHapticFeedbackStrength(strength)) },
        label = { strength -> strength.label },
    )
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

/** Backup: Export and Import as two plain rows, each saying what it covers and what it never includes. */
@Composable
internal fun SettingsBackupPageContent(onAction: (LauncherShellAction) -> Unit) {
    SettingsSection(title = stringResource(R.string.settings_page_backup)) {
        BackupSetting(onAction = onAction)
    }
}
