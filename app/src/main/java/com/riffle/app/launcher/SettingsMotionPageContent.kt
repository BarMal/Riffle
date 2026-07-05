package com.riffle.app.launcher

import androidx.compose.runtime.Composable

@Composable
internal fun SettingsMotionPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = "Motion") {
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
