package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherThemeAccent
import com.riffle.core.domain.launcher.settings.LauncherThemeCornerStyle
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import com.riffle.core.domain.launcher.settings.LauncherThemePreset
import com.riffle.core.domain.launcher.settings.LauncherThemeTypography
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeModeSettingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appearanceSettingsShowSelectedThemeTokens() {
        composeRule.setContent {
            MaterialTheme {
                SettingsPageContent(
                    modifier = Modifier,
                    state =
                        LauncherShellState(
                            launcherSettings =
                                LauncherSettings(
                                    appearance =
                                        AppearanceSettings(
                                            themeMode = LauncherThemeMode.DARK,
                                            themePreset = LauncherThemePreset.GLASS,
                                            themeAccent = LauncherThemeAccent.TEAL,
                                            themeCornerStyle = LauncherThemeCornerStyle.ROUNDED,
                                            themeTypography = LauncherThemeTypography.MONOSPACE,
                                        ),
                                ),
                        ).settingsSurfaceState(),
                    page = SettingsPage.APPEARANCE,
                    onPageSelected = {},
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("Dark (selected)").assertIsDisplayed()
        composeRule.onNodeWithText("Glass (selected)").assertIsDisplayed()
        composeRule.onNodeWithText("Teal (selected)").assertIsDisplayed()
        composeRule.onNodeWithText("Rounded (selected)").assertIsDisplayed()
        composeRule.onNodeWithText("Monospace (selected)").assertIsDisplayed()
    }
}
