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
                "Automatically bring forward a Today, Work, Personal, frequently-used, or notification " +
                    "page or card when it matches what's happening right now. This is automatic -- there's " +
                    "nothing else to configure.",
            checked = state.settings.contextual.enabled,
            onCheckedChange = { enabled ->
                onAction(LauncherShellAction.SelectContextualEnabled(enabled))
            },
        )
    }
}
