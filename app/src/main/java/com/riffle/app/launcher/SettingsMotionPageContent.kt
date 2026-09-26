package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.riffle.core.domain.launcher.settings.MotionPerformanceTargetFps
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference

@Composable
internal fun SettingsMotionPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = "Motion") {
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
                title = "Animation performance",
                subtitle = frameRateAvailability.settingsDescription(),
                trailingContent = {
                    SettingsButtonText(text = "Unavailable")
                },
            )
        } else {
            SettingsClickableRow(
                title = "Animation performance",
                subtitle = frameRateAvailability.settingsDescription(),
                onClick = {
                    onAction(
                        LauncherShellAction.SelectMotionPerformanceTargetFps(
                            nextDockShelfFrameRateTarget(
                                effectiveChoice.targetFps,
                                frameRateAvailability.choices,
                            ),
                        ),
                    )
                },
                trailingContent = {
                    SettingsButtonText(text = "${effectiveChoice.targetFps.framesPerSecond} fps")
                },
            )
        }
        val motion = state.settings.motion
        SettingsClickableRow(
            title = "Reduced motion",
            subtitle = reducedMotionDescription(motion.reducedMotionPreference, motion.systemReducedMotion),
            onClick = {
                onAction(LauncherShellAction.SelectReducedMotionPreference(motion.reducedMotionPreference.next()))
            },
            trailingContent = {
                SettingsButtonText(text = motion.reducedMotionPreference.settingsLabel())
            },
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

internal fun nextDockShelfFrameRateTarget(
    currentTargetFps: MotionPerformanceTargetFps,
    choices: List<DockShelfFrameRateChoice>,
): MotionPerformanceTargetFps {
    val targets = choices.map(DockShelfFrameRateChoice::targetFps)
    if (targets.isEmpty()) return currentTargetFps
    val currentIndex = targets.indexOf(currentTargetFps)
    return targets[(currentIndex + 1) % targets.size]
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
