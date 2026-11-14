package com.riffle.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.Dock
import com.riffle.app.launcher.DockInteractions
import com.riffle.app.launcher.RiffleLauncherTheme
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.settings.ResolvedLiquidGlass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The dock's at-rest background (`dockSurfaceAppearance`): a flat fill at the "Dock background"
 * setting's alpha with the edge lights on top, and the opaque fill under reduced transparency.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockLiquidGlassBackgroundScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun atRest() {
        render(reducedTransparency = false)
    }

    @Test
    fun atRestWithReducedTransparencyDrawsTheOpaqueFill() {
        render(reducedTransparency = true)
    }

    private fun render(reducedTransparency: Boolean) {
        val iconLoader = SolidColorAppIconLoader()
        val pinned = listOf(ScreenshotFixtures.camera, ScreenshotFixtures.mail)
        composeRule.setContent {
            RiffleLauncherTheme(
                reducedTransparency = reducedTransparency,
                liquidGlass = ResolvedLiquidGlass(reducedMotion = false, reducedTransparency = reducedTransparency),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF2B3A55)).padding(16.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Dock(
                        dock =
                            DockModel(
                                capacity = pinned.size,
                                items = pinned.map { app -> ScreenshotFixtures.shortcut(app) },
                                position = DockPosition.BOTTOM,
                            ),
                        isEditing = false,
                        notificationGroupsByApp = emptyList(),
                        appShortcutsByApp = emptyMap(),
                        appIconLoader = iconLoader,
                        position = DockPosition.BOTTOM,
                        interactions = DockInteractions(position = DockPosition.BOTTOM, onAction = {}),
                        dynamicEntries = emptyList(),
                    )
                }
            }
        }
        composeRule.captureScreen()
    }
}
