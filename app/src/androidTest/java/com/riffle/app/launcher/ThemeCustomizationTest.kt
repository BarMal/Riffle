package com.riffle.app.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockVisualEffect
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.settings.LauncherThemeAccent
import com.riffle.core.domain.launcher.settings.LauncherThemeCornerStyle
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import com.riffle.core.domain.launcher.settings.LauncherThemePreset
import com.riffle.core.domain.launcher.settings.LauncherThemeTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeCustomizationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectedThemeTokensApplyInLightAndDarkModes() {
        var mode by mutableStateOf(LauncherThemeMode.LIGHT)
        var primary = Color.Unspecified
        var panelShape: Shape? = null
        var bodyFontFamily: FontFamily? = null

        composeRule.setContent {
            RiffleLauncherTheme(
                themeMode = mode,
                themePreset = LauncherThemePreset.GLASS,
                themeAccent = LauncherThemeAccent.TEAL,
                themeCornerStyle = LauncherThemeCornerStyle.COMPACT,
                themeTypography = LauncherThemeTypography.MONOSPACE,
            ) {
                captureThemeTokens(
                    onTokens = { color, shape, fontFamily ->
                        primary = color
                        panelShape = shape
                        bodyFontFamily = fontFamily
                    },
                )
            }
        }

        composeRule.runOnIdle {
            assertEquals(androidx.compose.foundation.shape.RoundedCornerShape(12.dp), panelShape)
            assertEquals(FontFamily.Monospace, bodyFontFamily)
        }
        val lightPrimary = primary

        composeRule.runOnIdle {
            mode = LauncherThemeMode.DARK
        }

        composeRule.runOnIdle {
            assertNotEquals(lightPrimary, primary)
            assertEquals(androidx.compose.foundation.shape.RoundedCornerShape(12.dp), panelShape)
            assertEquals(FontFamily.Monospace, bodyFontFamily)
        }
    }

    @Test
    fun appearanceAndDockControlsDispatchAccessibleCustomizationActions() {
        val selectedAccents = mutableListOf<LauncherThemeAccent>()
        val selectedDockEffects = mutableListOf<DockVisualEffect>()

        composeRule.setContent {
            Column {
                ThemeAccentSetting(
                    selectedAccent = LauncherThemeAccent.DEFAULT,
                    onAction = { action ->
                        if (action is LauncherShellAction.SelectLauncherThemeAccent) {
                            selectedAccents += action.accent
                        }
                    },
                )
                DockSetting(
                    dock = DockModel(capacity = 4),
                    notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED,
                    onAction = { action ->
                        if (action is LauncherShellAction.SelectDockVisualEffect) {
                            selectedDockEffects += action.effect
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithText("Teal").assertHasClickAction().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(LauncherThemeAccent.TEAL), selectedAccents)
        }

        composeRule.onNodeWithText("Outlined").assertHasClickAction().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(DockVisualEffect.OUTLINED), selectedDockEffects)
        }
    }

    @Composable
    private fun captureThemeTokens(onTokens: (Color, Shape, FontFamily?) -> Unit) {
        val primary = MaterialTheme.colorScheme.primary
        val panelShape = LocalLauncherPanelShape.current
        val bodyFontFamily = MaterialTheme.typography.bodyMedium.fontFamily
        SideEffect { onTokens(primary, panelShape, bodyFontFamily) }
    }
}
