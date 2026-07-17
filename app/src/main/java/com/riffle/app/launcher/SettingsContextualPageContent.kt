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
            subtitle =
                "Open one of your configured Work, Personal, or Cards pages when it matches current activity",
            checked = state.settings.contextual.enabled,
            onCheckedChange = { enabled ->
                onAction(LauncherShellAction.SelectContextualEnabled(enabled))
            },
        )
    }
}
