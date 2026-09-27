package com.riffle.app.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.riffle.app.launcher.SettingsSurface
import com.riffle.app.launcher.settingsSurfaceState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Settings screen's own section containers ([com.riffle.app.launcher.SettingsSection]), with
 * liquid glass switched on and off.
 *
 * Settings is composed as its own top-level destination rather than inside [HomeDestination]'s
 * subtree (see `LauncherShell.kt`), so no [com.riffle.app.launcher.LocalLiquidGlassBackdrop] is
 * ever installed above it -- under Robolectric's native graphics mode the shader path is unavailable
 * anyway (see `GlassSurface.kt`), so the "enabled" screenshot below always exercises the same
 * legacy blurred-tint fallback a real API 31/32 device would render too, while "disabled" locks in
 * today's flat, opaque section fill with zero visual change.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class SettingsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sectionsLiquidGlassEnabled() {
        render(liquidGlassEnabled = true)
    }

    @Test
    fun sectionsLiquidGlassDisabled() {
        render(liquidGlassEnabled = false)
    }

    private fun render(liquidGlassEnabled: Boolean) {
        val state = ScreenshotFixtures.cardsState().settingsSurfaceState()
        composeRule.setContent {
            ScreenshotBackdrop(liquidGlassEnabled = liquidGlassEnabled) {
                SettingsSurface(
                    state = state,
                    onAction = {},
                )
            }
        }
        composeRule.captureScreen()
    }
}
