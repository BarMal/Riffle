package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The Workspaces (preview) developer setting as the settings page sees it: whether it is on, how to flip
 * it, and how to open the preview. Provided by the shell only when a preview host exists, so every other
 * host of the settings surface (tests, screenshots) draws no extra section.
 */
internal data class WorkspacePreviewSetting(
    val enabled: Boolean,
    val onEnabledChange: (Boolean) -> Unit,
    val onOpen: () -> Unit,
)

internal val LocalWorkspacePreviewSetting = staticCompositionLocalOf<WorkspacePreviewSetting?> { null }

/** Top of the main settings page: easy to find, off by default, reversible without losing any data. */
@Composable
internal fun SettingsWorkspacePreviewSection() {
    val setting = LocalWorkspacePreviewSetting.current ?: return
    SettingsSection(title = "Developer") {
        SettingsSwitchRow(
            title = "Workspaces (preview)",
            subtitle =
                "Experimental. Try workspaces (pages, lenses, the workspace menu and editor) in a separate " +
                    "full-screen preview. Your home screen, drawer and dock are not changed, and turning this " +
                    "off deletes nothing.",
            checked = setting.enabled,
            onCheckedChange = setting.onEnabledChange,
        )
        if (setting.enabled) {
            SettingsClickableRow(
                title = "Open Workspaces (preview)",
                subtitle = "Leave it any time with Exit preview or the Back button.",
                onClick = setting.onOpen,
            )
        }
    }
}
