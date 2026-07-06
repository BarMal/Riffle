package com.riffle.app.launcher

import androidx.compose.runtime.Composable

@Composable
internal fun SettingsContextualPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = "Contextual") {
        SettingsSwitchRow(
            title = "Contextual behaviour",
            subtitle = "Allow persisted contextual model and action decisions",
            checked = state.settings.contextual.enabled,
            onCheckedChange = { enabled ->
                onAction(LauncherShellAction.SelectContextualEnabled(enabled))
            },
        )
    }
}
