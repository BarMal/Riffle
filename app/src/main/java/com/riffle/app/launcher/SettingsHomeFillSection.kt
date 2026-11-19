package com.riffle.app.launcher

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.riffle.app.R
import com.riffle.core.domain.launcher.home.HomeArrangement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal const val ADD_ALL_APPS_TEST_TAG = "home-add-all-apps"
internal const val ARRANGE_HOME_TEST_TAG = "home-arrange"
internal const val ARRANGE_COMPACT_TEST_TAG = "home-arrange-compact"
internal const val ARRANGE_CONFIRM_TEST_TAG = "home-arrange-confirm"

/**
 * Settings > Home > "Fill Home": the two bulk rules (`HomeArrangement`) and the Compact choice. Each button acts on the
 * layout this page shows, says what it will do now, and announces what it did with Undo (the Settings snackbar).
 * Arranging asks first, because it replaces a hand-made arrangement. Both are off in Simple mode, where Home is
 * generated.
 */
@Composable
internal fun SettingsHomeFillSection(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    val snackbar = LocalSettingsSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val resources = LocalContext.current.resources
    val undo = stringResource(R.string.stream_undo)
    val simple = state.settings.features.simpleMode
    val missing = HomeArrangement.missingApps(state.homeLayout, state.installedApps).size
    var confirmArrange by remember { mutableStateOf(false) }
    val before = state.homeLayout
    val announce: (String) -> Unit = { message ->
        scope.announceWithUndo(snackbar, message, undo) { onAction(LauncherShellAction.UndoHomeBulkEdit(before)) }
    }

    SettingsSection(title = stringResource(R.string.settings_section_fill_home)) {
        SettingsClickableRow(
            modifier = Modifier.settingAnchor(SettingKey.HOME_ADD_ALL_APPS).testTag(ADD_ALL_APPS_TEST_TAG),
            title = SettingKey.HOME_ADD_ALL_APPS.label(),
            subtitle =
                when {
                    simple -> stringResource(R.string.settings_feature_off_in_simple_mode)
                    missing > 0 -> pluralStringResource(R.plurals.settings_add_all_apps_subtitle, missing, missing)
                    else -> stringResource(R.string.settings_add_all_apps_none)
                },
            enabled = missing > 0 && !simple,
            trailingContent = { SettingsButtonText(text = stringResource(R.string.settings_add_all_apps_action)) },
            onClick = {
                onAction(LauncherShellAction.AddAllAppsToHome)
                announce(resources.getQuantityString(R.plurals.home_added_apps, missing, missing))
            },
        )
        SettingsClickableRow(
            modifier = Modifier.settingAnchor(SettingKey.HOME_ARRANGE).testTag(ARRANGE_HOME_TEST_TAG),
            title = SettingKey.HOME_ARRANGE.label(),
            subtitle =
                stringResource(
                    if (simple) R.string.settings_feature_off_in_simple_mode else R.string.settings_arrange_subtitle,
                ),
            enabled = !simple,
            trailingContent = { SettingsButtonText(text = stringResource(R.string.settings_arrange_action)) },
            onClick = { confirmArrange = true },
        )
        SettingsSwitchRow(
            modifier = Modifier.settingAnchor(SettingKey.HOME_ARRANGE_COMPACT).testTag(ARRANGE_COMPACT_TEST_TAG),
            title = SettingKey.HOME_ARRANGE_COMPACT.label(),
            subtitle = stringResource(R.string.settings_arrange_compact_subtitle),
            checked = state.settings.home.compactArrange,
            onCheckedChange = { on -> onAction(LauncherShellAction.SetCompactArrange(on)) },
        )
    }
    if (confirmArrange) {
        ArrangeHomeDialog(
            onConfirm = {
                confirmArrange = false
                onAction(LauncherShellAction.ArrangeHomeAtoZ)
                announce(resources.getString(R.string.home_arranged))
            },
            onDismiss = { confirmArrange = false },
        )
    }
}

/** "Arrange Home A to Z? Your current arrangement will be replaced." with Arrange and Cancel. */
@Composable
internal fun ArrangeHomeDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_arrange_dialog_title)) },
        text = { Text(stringResource(R.string.home_arrange_dialog_body)) },
        confirmButton = {
            TextButton(modifier = Modifier.testTag(ARRANGE_CONFIRM_TEST_TAG), onClick = onConfirm) {
                Text(stringResource(R.string.home_arrange_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_arrange_dialog_cancel)) }
        },
    )
}

/** A transient message with an Undo action; the previous one is dropped. [onUndo] runs only if Undo was tapped. */
internal fun CoroutineScope.announceWithUndo(
    snackbar: SnackbarHostState?,
    message: String,
    undoLabel: String,
    onUndo: () -> Unit,
) {
    if (snackbar == null) return
    launch {
        snackbar.currentSnackbarData?.dismiss()
        val outcome =
            snackbar.showSnackbar(message = message, actionLabel = undoLabel, duration = SnackbarDuration.Long)
        if (outcome == SnackbarResult.ActionPerformed) onUndo()
    }
}
