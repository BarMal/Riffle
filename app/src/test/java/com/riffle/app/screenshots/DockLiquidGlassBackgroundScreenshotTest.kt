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
import com.riffle.app.launcher.ProvideLiquidGlassBackdrop
import com.riffle.app.launcher.RiffleLauncherTheme
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.DockVisualEffect
import com.riffle.core.domain.launcher.settings.ResolvedLiquidGlass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The dock's at-rest background fill (`dockSurfaceAppearance`'s branch 1, via the new
 * `dockAtRestBackground` helper it delegates to) with a real [ProvideLiquidGlassBackdrop] installed
 * above it and liquid glass turned on -- the exact eligibility gate `dockAtRestBackground` checks
 * (API 33+, enabled, non-null backdrop) before reaching for the shared AGSL shader treatment
 * `GlassSurface` also uses.
 *
 * Robolectric's native graphics mode cannot construct a real [android.graphics.RuntimeShader] (see
 * `GlassSurface.kt`'s own `runningUnderRobolectric` doc comment), so every case here -- enabled or
 * disabled -- renders through the same flat-fill fallback; that is by design; what these tests
 * actually guard is that reaching the eligible branch with a live backdrop layer, for every
 * [DockVisualEffect], never crashes and never regresses the flat look. The real shader treatment
 * itself is exercised on-device, per this feature's own convention (see `GlassSurface.kt`'s doc
 * comment and this PR's manual validation steps).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockLiquidGlassBackgroundScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun flatEffectAtRestWithLiquidGlassEnabledAndBackdropInstalled() {
        render(DockVisualEffect.FLAT, liquidGlassEnabled = true)
    }

    @Test
    fun elevatedEffectAtRestWithLiquidGlassEnabledAndBackdropInstalled() {
        render(DockVisualEffect.ELEVATED, liquidGlassEnabled = true)
    }

    @Test
    fun outlinedEffectAtRestWithLiquidGlassEnabledAndBackdropInstalled() {
        render(DockVisualEffect.OUTLINED, liquidGlassEnabled = true)
    }

    @Test
    fun flatEffectAtRestWithLiquidGlassDisabledStaysOnLegacyFill() {
        render(DockVisualEffect.FLAT, liquidGlassEnabled = false)
    }

    private fun render(
        visualEffect: DockVisualEffect,
        liquidGlassEnabled: Boolean,
    ) {
        val iconLoader = SolidColorAppIconLoader()
        val pinned = listOf(ScreenshotFixtures.camera, ScreenshotFixtures.mail)
        composeRule.setContent {
            RiffleLauncherTheme(liquidGlass = ResolvedLiquidGlass(liquidGlassEnabled, 0.45f, 0.2f, false)) {
                ProvideLiquidGlassBackdrop { backdropCapture ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(Color(0xFF2B3A55))
                                .then(backdropCapture)
                                .padding(16.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Dock(
                            dock =
                                DockModel(
                                    capacity = pinned.size,
                                    items = pinned.map { app -> ScreenshotFixtures.shortcut(app) },
                                    position = DockPosition.BOTTOM,
                                    visualEffect = visualEffect,
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
        }
        composeRule.captureScreen()
    }
}
