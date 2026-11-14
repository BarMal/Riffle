package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.riffle.app.R
import com.riffle.core.domain.launcher.settings.FeatureSettings
import com.riffle.core.domain.launcher.settings.HomeMode
import com.riffle.core.domain.launcher.settings.LauncherFeature

internal const val HOME_MODE_TEST_TAG_PREFIX = "home-mode-"
internal const val SIMPLE_MODE_TEST_TAG = "simple-mode"

/** The test tag of a feature switch's row. */
internal fun featureSwitchTestTag(feature: LauncherFeature): String = "feature-switch-${feature.name.lowercase()}"

/**
 * Settings > Home, first: what Home shows (Placed items or App library) and Simple mode. While Simple mode is on it
 * decides both, so the Home mode row is shown as it reads then and cannot be changed.
 */
@Composable
internal fun SettingsHomeModeSections(
    features: FeatureSettings,
    homeMode: HomeMode,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.settings_section_simple_mode)) {
        SettingsSwitchRow(
            modifier = Modifier.settingAnchor(SettingKey.SIMPLE_MODE).testTag(SIMPLE_MODE_TEST_TAG),
            title = SettingKey.SIMPLE_MODE.label(),
            subtitle = stringResource(R.string.settings_simple_mode_subtitle),
            checked = features.simpleMode,
            onCheckedChange = { on -> onAction(LauncherShellAction.SetSimpleMode(on)) },
        )
    }
    SettingsSection(title = stringResource(R.string.settings_section_view)) {
        SettingsChoiceRow(
            modifier = Modifier.settingAnchor(SettingKey.HOME_MODE),
            title = SettingKey.HOME_MODE.label(),
            subtitle =
                stringResource(
                    if (features.simpleMode) {
                        R.string.settings_home_mode_simple_subtitle
                    } else {
                        R.string.settings_home_mode_subtitle
                    },
                ),
            enabled = !features.simpleMode,
            options = HomeMode.entries,
            selected = if (features.simpleMode) HomeMode.AppLibrary else homeMode,
            onSelect = { mode -> onAction(LauncherShellAction.SelectHomeMode(mode)) },
            label = { mode ->
                stringResource(
                    if (mode == HomeMode.PlacedItems) {
                        R.string.settings_home_mode_placed
                    } else {
                        R.string.settings_home_mode_app_library
                    },
                )
            },
            optionTag = { mode -> "$HOME_MODE_TEST_TAG_PREFIX${mode.name}" },
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
        }
    val subtitle =
        when {
            features.simpleMode -> R.string.settings_feature_off_in_simple_mode
            feature == LauncherFeature.STREAM -> R.string.settings_feature_stream_subtitle
            feature == LauncherFeature.DOCK_LIVE_APPS -> R.string.settings_feature_live_apps_subtitle
            else -> R.string.settings_feature_library_subtitle
        }
    val stored =
        when (feature) {
            LauncherFeature.STREAM -> features.stream
            LauncherFeature.DOCK_LIVE_APPS -> features.dockLiveApps
            LauncherFeature.LIBRARY -> features.library
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
