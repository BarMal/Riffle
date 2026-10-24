@file:Suppress("MatchingDeclarationName")

package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The S4 cutover switch ("Home from placed items") as the Developer section draws it: the stored choice, and what this
 * process runs. Since S9 it is the one developer fallback left (the Workspaces preview switch and the Classic idea it
 * stood for are gone): off hands the pool's home back to the previous `HomeLayoutSet` storage at the next start, which
 * is the only revert that needs no backup restore while the pool storage is new. See `workspaces-default.md`.
 */
internal data class PoolHomeSetting(
    val enabled: Boolean,
    val activeThisProcess: Boolean,
    val onEnabledChange: (Boolean) -> Unit,
)

/** Provided by the shell when it has the switch; absent in tests and screenshots that draw no Developer section. */
internal val LocalPoolHomeSetting = staticCompositionLocalOf<PoolHomeSetting?> { null }

/**
 * Top of the main settings page (S9: workspaces are the launcher's home, no longer a preview): the Workspaces, Sources
 * and Saved lenses pages, then the Developer fallback. Each part draws only where the shell provides its host.
 */
@Composable
internal fun SettingsWorkspacesSection(onPageSelected: (SettingsPage) -> Unit = {}) {
    val settingsHost = LocalWorkspaceSettingsHost.current
    if (settingsHost != null) {
        SettingsSection(title = WorkspacesSettingsText.WORKSPACES_TITLE) {
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
    LocalPoolHomeSetting.current?.let { poolHome ->
        SettingsSection(title = "Developer") { PoolHomeSwitchRow(poolHome) }
    }
}

/**
 * "Home from placed items": on (the default) the home, dock and folders live in the placed-items store; off falls back
 * to the previous storage. Either way the data is handed over on the next start, nothing is lost.
 */
@Composable
private fun PoolHomeSwitchRow(setting: PoolHomeSetting) {
    val restartNote = if (setting.enabled != setting.activeThisProcess) " Restart Riffle to apply." else ""
    SettingsSwitchRow(
        title = "Home from placed items",
        subtitle =
            "On: your home screen, dock and folders are kept in the placed-items store your workspaces use. Off: " +
                "go back to the previous storage (a fallback for testing; every workspace then shows the same home). " +
                "Your items move over the next time Riffle starts." + restartNote,
        checked = setting.enabled,
        onCheckedChange = setting.onEnabledChange,
    )
}
