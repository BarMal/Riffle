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
    /** The S4 cutover switch ("Home from placed items"); null where the host has none. */
    val poolHome: PoolHomeSetting? = null,
)

/** The cutover switch as the Developer section draws it: the stored choice, and what this process runs. */
internal data class PoolHomeSetting(
    val enabled: Boolean,
    val activeThisProcess: Boolean,
    val onEnabledChange: (Boolean) -> Unit,
)

internal val LocalWorkspacePreviewSetting = staticCompositionLocalOf<WorkspacePreviewSetting?> { null }

/** Top of the main settings page: easy to find, off by default, reversible without losing any data. */
@Composable
internal fun SettingsWorkspacePreviewSection(onPageSelected: (SettingsPage) -> Unit = {}) {
    val setting = LocalWorkspacePreviewSetting.current ?: return
    SettingsSection(title = "Developer") {
        setting.poolHome?.let { poolHome -> PoolHomeSwitchRow(poolHome) }
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
            // The pages exist only while the preview is on (and only where a host provides them).
            val settingsHost = LocalWorkspaceSettingsHost.current
            if (settingsHost != null) {
                SettingsClickableRow(
                    title = WorkspacesSettingsText.WORKSPACES_TITLE,
                    subtitle = "Switch, rename, duplicate, delete, presets and copying between layouts",
                    onClick = { onPageSelected(SettingsPage.WORKSPACES) },
                )
                SettingsClickableRow(
                    title = SourcesSettingsText.TITLE,
                    subtitle = "What each source shows, its status and an on/off switch",
                    onClick = { onPageSelected(SettingsPage.SOURCES) },
                )
                if (settingsHost.lenses != null) {
                    SettingsClickableRow(
                        title = LensesSettingsText.TITLE,
                        subtitle = LensesSettingsText.DEVELOPER_SUBTITLE,
                        onClick = { onPageSelected(SettingsPage.LENSES) },
                    )
                }
            }
        }
    }
}

/**
 * "Home from placed items": on (the default) the home, dock and folders live in the placed-items store; off falls back
 * to the previous storage for this release. Either way the data is handed over on the next start, nothing is lost.
 */
@Composable
private fun PoolHomeSwitchRow(setting: PoolHomeSetting) {
    val restartNote = if (setting.enabled != setting.activeThisProcess) " Restart Riffle to apply." else ""
    SettingsSwitchRow(
        title = "Home from placed items",
        subtitle =
            "On: your home screen, dock and folders are kept in the new placed-items store. Off: go back to the " +
                "previous storage (available for one release). Your items move over the next time Riffle starts." +
                restartNote,
        checked = setting.enabled,
        onCheckedChange = setting.onEnabledChange,
    )
}
