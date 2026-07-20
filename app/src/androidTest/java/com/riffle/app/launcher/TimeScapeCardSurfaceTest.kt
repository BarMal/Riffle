package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.riffle.core.domain.launcher.settings.TimeScapeAppearanceSettings
import com.riffle.core.domain.launcher.settings.TimeScapeBackgroundSource
import com.riffle.core.domain.launcher.settings.TimeScapeMotion
import com.riffle.core.domain.launcher.settings.TimeScapeSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimeScapeCardSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everyBackgroundSourceRendersCardContentWithAFallback() {
        val appearances =
            TimeScapeBackgroundSource.entries.map { source ->
                TimeScapeAppearanceSettings(surface = TimeScapeSurface(backgroundSource = source))
            }

        composeRule.setContent {
            MaterialTheme {
                appearances.forEachIndexed { index, appearance ->
                    TimeScapeCardSurface(
                        appearance = appearance,
                        background = TimeScapeCardBackground(appSeed = "card-$index"),
                    ) {
                        Text("Card $index")
                    }
                }
            }
        }

        appearances.indices.forEach { index -> composeRule.onNodeWithText("Card $index").assertIsDisplayed() }
    }

    @Test
    fun reducedTransparencyKeepsAnOpaqueLegibleSurface() {
        val colors =
            resolveTimeScapeCardColors(
                appearance =
                    TimeScapeAppearanceSettings(
                        surface =
                            TimeScapeSurface(
                                customBackgroundArgb = 0xFF101010L,
                                backgroundSource = TimeScapeBackgroundSource.CUSTOM_SOLID,
                            ),
                        motion = TimeScapeMotion(reducedTransparency = true),
                    ),
                background = TimeScapeCardBackground(),
                materialBackground = Color.White,
                materialAccent = Color.Blue,
            )

        assertEquals(1f, colors.glass.alpha)
        assertTrue(contrastRatio(colors.foreground, colors.glass) >= 4.5f)
    }

    @Test
    fun oldPlatformDisablesBlurWithoutChangingStoredAppearance() {
        val appearance = TimeScapeAppearanceSettings(surface = TimeScapeSurface(blurStrengthPercent = 72))

        assertFalse(timeScapeRendererCapabilities(sdkInt = 30).supportsBlur)
        assertEquals(72, appearance.surface.blurStrengthPercent)
        assertEquals(0, appearance.effectiveFor(timeScapeRendererCapabilities(sdkInt = 30)).surface.blurStrengthPercent)
    }

    @Test
    fun corruptOrOversizedArtworkFallsBackAndDecodingIsBounded() {
        assertNull(decodeTimeScapeArtwork("not-base64"))
        assertNull(decodeTimeScapeArtwork("a".repeat(2_800_001)))
        assertEquals(2, timeScapeArtworkSampleSize(width = 1_024, height = 600))
        assertEquals(4, timeScapeArtworkSampleSize(width = 3_000, height = 900))
    }

    @Test
    fun resolvedStackRetainsFocusedCardAsHighestOrderEntryWithoutMotion() {
        val appearance = TimeScapeAppearanceSettings(motion = TimeScapeMotion(reducedMotion = true))
        val entries =
            appearance
                .resolveCardStack(
                    viewport = com.riffle.core.domain.launcher.settings.TimeScapeViewportDp(800, 1_200),
                    globalReducedMotion = true,
                ).layoutPolicy
                .entries(cardCount = 3, activeIndex = 1, reducedMotion = true)

        assertEquals(1, entries.maxBy { entry -> entry.order }.cardIndex)
        assertTrue(entries.all { entry -> entry.rotationDegrees == 0f })
    }
}
