package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import com.riffle.app.R
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference

@Composable
internal fun SettingsMotionPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.settings_section_motion)) {
        val performanceTargetFps = state.settings.motion.performanceTargetFps
        val view = LocalView.current
        val frameRateGateway =
            remember(view) {
                DockShelfFrameRateGateway(AndroidDockShelfFrameRatePlatform(view.context))
            }
        val frameRateAvailability =
            remember(frameRateGateway, performanceTargetFps) {
                frameRateGateway.availability(performanceTargetFps)
            }
        val effectiveChoice = frameRateAvailability.effectiveChoice
        if (effectiveChoice == null) {
            SettingsListRow(
                modifier = Modifier.settingAnchor(SettingKey.MOTION_PERFORMANCE),
                title = SettingKey.MOTION_PERFORMANCE.label(),
                subtitle = frameRateAvailability.settingsDescription(),
                trailingContent = {
                    SettingsButtonText(text = stringResource(R.string.settings_motion_unavailable))
                },
            )
        } else {
            SettingsChoiceRow(
                modifier = Modifier.settingAnchor(SettingKey.MOTION_PERFORMANCE),
                title = SettingKey.MOTION_PERFORMANCE.label(),
                subtitle = frameRateAvailability.settingsDescription(),
                options = frameRateAvailability.choices.map(DockShelfFrameRateChoice::targetFps),
                selected = effectiveChoice.targetFps,
                onSelect = { target -> onAction(LauncherShellAction.SelectMotionPerformanceTargetFps(target)) },
                label = { target -> "${target.framesPerSecond} fps" },
            )
        }
        val motion = state.settings.motion
        SettingsChoiceRow(
            modifier = Modifier.settingAnchor(SettingKey.MOTION_REDUCED),
            title = SettingKey.MOTION_REDUCED.label(),
            subtitle = reducedMotionDescription(motion.reducedMotionPreference, motion.systemReducedMotion),
            options = ReducedMotionPreference.entries,
            selected = motion.reducedMotionPreference,
            onSelect = { preference -> onAction(LauncherShellAction.SelectReducedMotionPreference(preference)) },
            label = { preference -> preference.settingsLabel() },
        )
        HapticStrengthSetting(
            selectedStrength = state.settings.haptics.feedbackStrength,
            onAction = onAction,
        )
    }
}

internal fun ReducedMotionPreference.settingsLabel(): String =
    when (this) {
        ReducedMotionPreference.SYSTEM -> "System"
        ReducedMotionPreference.ON -> "On"
        ReducedMotionPreference.OFF -> "Off"
    }

internal fun reducedMotionDescription(
    preference: ReducedMotionPreference,
    systemReducedMotion: Boolean,
): String =
    when (preference) {
        ReducedMotionPreference.SYSTEM ->
            if (systemReducedMotion) {
                "Following system: animations are off, so motion is reduced"
            } else {
                "Following system animation settings"
            }
        ReducedMotionPreference.ON -> "Minimise launcher animations"
        ReducedMotionPreference.OFF -> "Always animate, even when system animations are off"
    }

private fun DockShelfFrameRateAvailability.settingsDescription(): String {
    val effectiveFrameRateChoice = effectiveChoice
    return when {
        effectiveFrameRateChoice == null ->
            "No supported dock animation frame rate is available on this display"
        usesFallback ->
            "${requestedTargetFps.framesPerSecond} fps is unavailable; " +
                "using ${effectiveFrameRateChoice.targetFps.framesPerSecond} fps"
        else -> "Target ${effectiveFrameRateChoice.targetFps.framesPerSecond} fps for dock animations"
    }
}
