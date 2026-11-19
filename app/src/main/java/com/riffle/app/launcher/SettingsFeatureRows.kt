package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.riffle.app.R
import com.riffle.core.domain.launcher.settings.FeatureSettings
import com.riffle.core.domain.launcher.settings.LauncherFeature

internal const val ADD_NEW_APPS_TEST_TAG = "add-new-apps"
internal const val SIMPLE_MODE_TEST_TAG = "simple-mode"

/** The test tag of a feature switch's row. */
internal fun featureSwitchTestTag(feature: LauncherFeature): String = "feature-switch-${feature.name.lowercase()}"

/**
 * Settings > Home, first: Simple mode (the locked-down Home: every app A to Z, not arrangeable) and whether a newly
 * installed app is placed on Home. There is one Home (ADR 0003, Amendment 8); there is no "Home shows" choice.
 */
@Composable
internal fun SettingsHomeModeSections(
    features: FeatureSettings,
    addNewApps: Boolean,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.settings_section_home_mode)) {
        SettingsSwitchRow(
            modifier = Modifier.settingAnchor(SettingKey.SIMPLE_MODE).testTag(SIMPLE_MODE_TEST_TAG),
            title = SettingKey.SIMPLE_MODE.label(),
            subtitle = stringResource(R.string.settings_simple_mode_subtitle),
            checked = features.simpleMode,
            onCheckedChange = { on -> onAction(LauncherShellAction.SetSimpleMode(on)) },
        )
        SettingsSwitchRow(
            modifier = Modifier.settingAnchor(SettingKey.ADD_NEW_APPS).testTag(ADD_NEW_APPS_TEST_TAG),
            title = SettingKey.ADD_NEW_APPS.label(),
            subtitle = stringResource(R.string.settings_add_new_apps_subtitle),
            checked = addNewApps,
            onCheckedChange = { on -> onAction(LauncherShellAction.SetAddNewApps(on)) },
        )
    }
}

/** One feature switch's row on its Place's page: on by default, and off (and unchangeable) while Simple mode is on. */
@Composable
internal fun SettingsFeatureSwitchRow(
    feature: LauncherFeature,
    features: FeatureSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    val key =
        when (feature) {
            LauncherFeature.STREAM -> SettingKey.FEATURE_STREAM
            LauncherFeature.DOCK_LIVE_APPS -> SettingKey.FEATURE_DOCK_LIVE_APPS
            LauncherFeature.LIBRARY -> SettingKey.FEATURE_LIBRARY
            LauncherFeature.DOCK_STREAM_HANDLE -> SettingKey.FEATURE_DOCK_STREAM_HANDLE
            LauncherFeature.STREAM_VIEW_BUTTON -> SettingKey.FEATURE_STREAM_VIEW_BUTTON
        }
    val subtitle =
        when {
            features.simpleMode -> R.string.settings_feature_off_in_simple_mode
            feature == LauncherFeature.STREAM -> R.string.settings_feature_stream_subtitle
            feature == LauncherFeature.DOCK_LIVE_APPS -> R.string.settings_feature_live_apps_subtitle
            feature == LauncherFeature.DOCK_STREAM_HANDLE -> R.string.settings_feature_stream_handle_subtitle
            feature == LauncherFeature.STREAM_VIEW_BUTTON -> R.string.settings_feature_stream_view_button_subtitle
            else -> R.string.settings_feature_library_subtitle
        }
    val stored =
        when (feature) {
            LauncherFeature.STREAM -> features.stream
            LauncherFeature.DOCK_LIVE_APPS -> features.dockLiveApps
            LauncherFeature.LIBRARY -> features.library
            LauncherFeature.DOCK_STREAM_HANDLE -> features.dockStreamHandle
            LauncherFeature.STREAM_VIEW_BUTTON -> features.streamViewButton
        }
    SettingsSwitchRow(
        modifier = Modifier.settingAnchor(key).testTag(featureSwitchTestTag(feature)),
        title = key.label(),
        subtitle = stringResource(subtitle),
        checked = stored && !features.simpleMode,
        enabled = !features.simpleMode,
        onCheckedChange = { on -> onAction(LauncherShellAction.SetFeatureEnabled(feature, on)) },
    )
}
