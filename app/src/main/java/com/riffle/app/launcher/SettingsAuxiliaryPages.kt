package com.riffle.app.launcher

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import kotlinx.coroutines.launch

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

/** Taps on the Version row that reveal the "Developer options" switch, the conventional way. */
internal const val DEVELOPER_OPTIONS_REVEAL_TAPS = 7

internal const val VERSION_ROW_TEST_TAG = "settings-version-row"

@Composable
internal fun SettingsVersionPageContent(
    state: SettingsSurfaceState,
    onPageSelected: (SettingsPage) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    val diagnostics = state.settings.diagnostics
    val context = LocalContext.current
    val taps = remember { mutableIntStateOf(0) }
    val revealedMessage = stringResource(R.string.settings_developer_options_revealed)
    val alreadyMessage = stringResource(R.string.settings_developer_options_already)
    SettingsSection(title = stringResource(R.string.settings_page_version)) {
        VersionInformationSetting(
            appVersionLabel = state.appVersionLabel,
            appBuildIdentityLabel = state.appBuildIdentityLabel,
            onVersionTapped = {
                taps.intValue += 1
                if (taps.intValue >= DEVELOPER_OPTIONS_REVEAL_TAPS) {
                    taps.intValue = 0
                    val message = if (diagnostics.developerOptionsRevealed) alreadyMessage else revealedMessage
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    if (!diagnostics.developerOptionsRevealed) {
                        onAction(LauncherShellAction.RevealDeveloperOptions)
                    }
                }
            },
        )
    }
    SettingsSection(title = stringResource(R.string.settings_section_support)) {
        SettingsClickableRow(
            title = stringResource(R.string.settings_page_after_update),
            subtitle = stringResource(R.string.settings_page_after_update_summary),
            onClick = { onPageSelected(SettingsPage.AFTER_UPDATE) },
        )
    }
    SettingsResetSection(settings = state.settings, onAction = onAction)
    if (diagnostics.developerOptionsRevealed) {
        SettingsSection(title = stringResource(R.string.settings_section_developer)) {
            SettingsSwitchRow(
                modifier = Modifier.settingAnchor(SettingKey.ABOUT_DEVELOPER_OPTIONS),
                title = SettingKey.ABOUT_DEVELOPER_OPTIONS.label(),
                subtitle = stringResource(R.string.setting_about_developer_options_summary),
                checked = diagnostics.developerOptions,
                onCheckedChange = { enabled -> onAction(LauncherShellAction.SetDeveloperOptions(enabled)) },
            )
            if (diagnostics.developerOptions) {
                SettingsClickableRow(
                    title = stringResource(R.string.settings_page_developer_options),
                    subtitle = stringResource(R.string.settings_page_developer_options_summary),
                    onClick = { onPageSelected(SettingsPage.DEVELOPER_OPTIONS) },
                )
            }
        }
    }
}

/** Settings > About > Developer options: the diagnostics pages, offered only while the switch is on. */
@Composable
internal fun SettingsDeveloperOptionsPageContent(onPageSelected: (SettingsPage) -> Unit) {
    SettingsSection(title = stringResource(R.string.settings_page_developer_options)) {
        SettingsPage.DEVELOPER_OPTIONS.children().forEach { page ->
            SettingsClickableRow(
                title = stringResource(page.titleRes),
                subtitle = stringResource(page.summaryRes),
                onClick = { onPageSelected(page) },
            )
        }
    }
}

@Composable
private fun VersionInformationSetting(
    appVersionLabel: String,
    appBuildIdentityLabel: String,
    onVersionTapped: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsTextColumn(
            modifier = Modifier.testTag(VERSION_ROW_TEST_TAG).clickable(onClick = onVersionTapped),
            title = stringResource(R.string.settings_version_row),
            subtitle = appVersionLabel,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SettingsPrimaryText(text = stringResource(R.string.settings_build_row))
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
            label = stringResource(R.string.settings_hidden_apps_search),
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
                Text(text = stringResource(R.string.filter_clear))
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
            SettingsButtonText(text = stringResource(R.string.action_unhide))
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

internal const val RESET_SETTINGS_TEST_TAG = "about-reset-settings"
internal const val RESET_SETTINGS_CONFIRM_TEST_TAG = "about-reset-settings-confirm"

/** Settings > About > Reset settings: asks first, says what is kept, then "Settings reset" with Undo. */
@Composable
private fun SettingsResetSection(
    settings: com.riffle.core.domain.launcher.settings.LauncherSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    val snackbar = LocalSettingsSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val undo = stringResource(R.string.stream_undo)
    val done = stringResource(R.string.settings_reset_done)
    val streamLayouts = LocalStreamLayoutRepository.current
    var confirm by remember { mutableStateOf(false) }
    SettingsSection(title = stringResource(R.string.settings_section_reset)) {
        SettingsClickableRow(
            modifier = Modifier.settingAnchor(SettingKey.ABOUT_RESET_SETTINGS).testTag(RESET_SETTINGS_TEST_TAG),
            title = SettingKey.ABOUT_RESET_SETTINGS.label(),
            subtitle = stringResource(R.string.settings_reset_settings_subtitle),
            onClick = { confirm = true },
            trailingContent = { SettingsButtonText(text = stringResource(R.string.settings_reset_settings_action)) },
        )
    }
    if (confirm) {
        ResetSettingsDialog(
            onConfirm = {
                confirm = false
                val before = settings
                onAction(LauncherShellAction.ResetSettings)
                // The Stream's sections live in their own store: they go back to the default with the settings, and
                // Undo puts back exactly what was there.
                scope.launch {
                    val streamBefore = streamLayouts?.resetAll()
                    scope.announceWithUndo(snackbar, done, undo) {
                        onAction(LauncherShellAction.RestoreSettings(before))
                        streamBefore?.let { streamLayouts?.restoreAll(it) }
                    }
                }
            },
            onDismiss = { confirm = false },
        )
    }
}

/** "Reset settings?" with the exact list of what is kept, and Reset and Cancel. */
@Composable
internal fun ResetSettingsDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_reset_dialog_title)) },
        text = { Text(stringResource(R.string.settings_reset_dialog_body)) },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag(RESET_SETTINGS_CONFIRM_TEST_TAG),
                onClick = onConfirm,
            ) { Text(stringResource(R.string.settings_reset_dialog_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_reset_dialog_cancel))
            }
        },
    )
}
