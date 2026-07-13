package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

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
        SettingsClickableRow(
            title = "Animation performance",
            subtitle = frameRateAvailability.settingsDescription(),
            enabled = effectiveChoice != null,
            onClick = {
                effectiveChoice?.let { choice ->
                    onAction(
                        LauncherShellAction.SelectMotionPerformanceTargetFps(
                            nextDockShelfFrameRateTarget(choice.targetFps, frameRateAvailability.choices),
                        ),
                    )
                }
            },
            trailingContent = {
                val targetLabel = effectiveChoice?.let { "${it.targetFps.framesPerSecond} fps" } ?: "Unavailable"
                SettingsButtonText(text = targetLabel)
            },
        )
        SettingsSwitchRow(
            title = "Reduced motion",
            subtitle = "Minimise home page settle animations",
            checked = state.settings.motion.reducedMotion,
            onCheckedChange = { enabled ->
                onAction(LauncherShellAction.SelectReducedMotionEnabled(enabled))
            },
        )
    }
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
