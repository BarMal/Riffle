package com.riffle.app.launcher

import androidx.compose.runtime.Composable

@Composable
internal fun SettingsMotionPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = "Motion") {
        val performanceTargetFps = state.settings.motion.performanceTargetFps
        SettingsClickableRow(
            title = "Animation performance",
            subtitle = "Target ${performanceTargetFps.framesPerSecond} fps for dock animations",
            onClick = {
                onAction(
                    LauncherShellAction.SelectMotionPerformanceTargetFps(performanceTargetFps.next()),
                )
            },
            trailingContent = {
                SettingsButtonText(text = "${performanceTargetFps.framesPerSecond} fps")
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
