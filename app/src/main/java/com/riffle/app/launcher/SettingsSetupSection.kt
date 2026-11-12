package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import com.riffle.app.launcher.onboarding.LocalWalkthroughLauncher
import com.riffle.app.launcher.onboarding.OnboardingText

/**
 * Top of the main settings page: where the Stream's sources are managed, and "Set up Riffle again", which re-runs the
 * four-step first-run set-up (Home, Dock, Library, Stream). Each part draws only where the shell provides its host.
 */
@Composable
internal fun SettingsSetupSection(onPageSelected: (SettingsPage) -> Unit = {}) {
    val settingsHost = LocalWorkspaceSettingsHost.current
    val runSetup = LocalWalkthroughLauncher.current
    if (settingsHost == null && runSetup == null) return
    SettingsSection(title = OnboardingText.SETTINGS_SECTION) {
        if (settingsHost != null) {
            SettingsClickableRow(
                title = SourcesSettingsText.TITLE,
                subtitle = "What each source shows, its status and an on/off switch",
                onClick = { onPageSelected(SettingsPage.SOURCES) },
            )
        }
        runSetup?.let { run ->
            SettingsClickableRow(
                title = OnboardingText.SETTINGS_ROW,
                subtitle = OnboardingText.SETTINGS_ROW_SUBTITLE,
                onClick = run,
            )
        }
    }
}
