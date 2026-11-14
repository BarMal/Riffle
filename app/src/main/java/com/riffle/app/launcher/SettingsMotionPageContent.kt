package com.riffle.app.launcher

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.core.domain.launcher.settings.DockMotionSettings
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
    SettingsDockMotionSection(settings = state.settings.motion.dock, onAction = onAction)
}

/** Motion > Dock and Stream: the adjustable numbers behind the trampoline, the fan, the stack and hold to choose. */
@Composable
internal fun SettingsDockMotionSection(
    settings: DockMotionSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    val change = { next: DockMotionSettings -> onAction(LauncherShellAction.SetDockMotion(next)) }
    SettingsSection(title = stringResource(R.string.settings_section_dock_motion)) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            text = stringResource(R.string.settings_motion_dock_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsSteppedSlider(
            modifier = Modifier.settingAnchor(SettingKey.MOTION_SPEED),
            title = SettingKey.MOTION_SPEED.label(),
            value = settings.speedMillis,
            values = DockMotionSettings.SPEED_VALUES,
            valueLabel = { millis -> "$millis ms" },
            onValueChange = { millis -> change(settings.copy(speedMillis = millis)) },
        )
        SettingsSteppedSlider(
            modifier = Modifier.settingAnchor(SettingKey.MOTION_TRAMPOLINE),
            title = SettingKey.MOTION_TRAMPOLINE.label(),
            value = settings.trampolinePercent,
            values = DockMotionSettings.STRENGTH_VALUES,
            valueLabel = ::percentOrOff,
            onValueChange = { percent -> change(settings.copy(trampolinePercent = percent)) },
        )
        SettingsSteppedSlider(
            modifier = Modifier.settingAnchor(SettingKey.MOTION_INERTIA),
            title = SettingKey.MOTION_INERTIA.label(),
            value = settings.inertiaPercent,
            values = DockMotionSettings.INERTIA_VALUES,
            valueLabel = ::percentOrOff,
            onValueChange = { percent -> change(settings.copy(inertiaPercent = percent)) },
        )
        SettingsSteppedSlider(
            modifier = Modifier.settingAnchor(SettingKey.MOTION_MAGNET),
            title = SettingKey.MOTION_MAGNET.label(),
            value = settings.magnetPercent,
            values = DockMotionSettings.STRENGTH_VALUES,
            valueLabel = ::percentOrOff,
            onValueChange = { percent -> change(settings.copy(magnetPercent = percent)) },
        )
        SettingsSteppedSlider(
            modifier = Modifier.settingAnchor(SettingKey.MOTION_HOLD),
            title = SettingKey.MOTION_HOLD.label(),
            value = settings.holdMillis,
            values = DockMotionSettings.HOLD_VALUES,
            valueLabel = { millis -> "$millis ms" },
            onValueChange = { millis -> change(settings.copy(holdMillis = millis)) },
        )
        SettingsSwitchRow(
            modifier = Modifier.settingAnchor(SettingKey.MOTION_TICKS),
            title = SettingKey.MOTION_TICKS.label(),
            subtitle =
                stringResource(
                    if (settings.ticks) R.string.settings_motion_ticks_on else R.string.settings_motion_ticks_off,
                ),
            checked = settings.ticks,
            onCheckedChange = { on -> change(settings.copy(ticks = on)) },
        )
    }
}

private fun percentOrOff(percent: Int): String = if (percent == 0) "Off" else "$percent%"

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
