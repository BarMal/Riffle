package com.riffle.app.launcher

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.core.domain.launcher.settings.LauncherThemeAccent
import com.riffle.core.domain.launcher.settings.LauncherThemeColors
import com.riffle.core.domain.launcher.settings.LauncherThemeCornerStyle
import com.riffle.core.domain.launcher.settings.LauncherThemeTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherThemeTest {
    @Test
    fun dynamicMaterialColorIsDisabledBeforeAndroid12() {
        assertFalse(supportsDynamicMaterialColor(sdkInt = 30))
    }

    @Test
    fun dynamicMaterialColorIsEnabledFromAndroid12() {
        assertTrue(supportsDynamicMaterialColor(sdkInt = 31))
    }

    @Test
    fun fallbackThemeUsesLightSchemeWhenSystemIsLight() {
        assertSame(lightScheme, fallbackScheme(darkTheme = false))
    }

    @Test
    fun fallbackThemeUsesDarkSchemeWhenSystemIsDark() {
        assertSame(darkScheme, fallbackScheme(darkTheme = true))
    }

    @Test
    fun defaultAccentPreservesTheBaseColorScheme() {
        assertSame(
            lightScheme,
            lightScheme.withThemeAccent(LauncherThemeAccent.DEFAULT, darkTheme = false),
        )
    }

    @Test
    fun selectedAccentChangesPrimaryAndContainerRolesInLightAndDarkThemes() {
        LauncherThemeAccent.entries
            .filterNot { accent -> accent == LauncherThemeAccent.DEFAULT }
            .forEach { accent ->
                assertNotEquals(
                    "light accent=$accent",
                    lightScheme.primary,
                    lightScheme.withThemeAccent(accent, darkTheme = false).primary,
                )
                assertNotEquals(
                    "dark accent=$accent",
                    darkScheme.primaryContainer,
                    darkScheme.withThemeAccent(accent, darkTheme = true).primaryContainer,
                )
            }
    }

    @Test
    fun selectedAccentUpdatesElevatedSurfaceTintAndInversePrimary() {
        LauncherThemeAccent.entries
            .filterNot { accent -> accent == LauncherThemeAccent.DEFAULT }
            .forEach { accent ->
                val accentedLightScheme = lightScheme.withThemeAccent(accent, darkTheme = false)
                val accentedDarkScheme = darkScheme.withThemeAccent(accent, darkTheme = true)

                assertEquals(accentedLightScheme.primary, accentedLightScheme.surfaceTint)
                assertEquals(accentedDarkScheme.primary, accentedDarkScheme.surfaceTint)
                assertNotEquals(lightScheme.inversePrimary, accentedLightScheme.inversePrimary)
                assertNotEquals(darkScheme.inversePrimary, accentedDarkScheme.inversePrimary)
            }
    }

    @Test
    fun selectedAccentAlsoThemesDockSurfaceAndOutlineRoles() {
        LauncherThemeAccent.entries
            .filterNot { accent -> accent == LauncherThemeAccent.DEFAULT }
            .forEach { accent ->
                val accentedLightScheme = lightScheme.withThemeAccent(accent, darkTheme = false)
                val accentedDarkScheme = darkScheme.withThemeAccent(accent, darkTheme = true)

                assertNotEquals(lightScheme.surfaceVariant, accentedLightScheme.surfaceVariant)
                assertNotEquals(darkScheme.surfaceVariant, accentedDarkScheme.surfaceVariant)
                assertNotEquals(lightScheme.outlineVariant, accentedLightScheme.outlineVariant)
                assertNotEquals(darkScheme.outlineVariant, accentedDarkScheme.outlineVariant)
                assertEquals(accentedLightScheme.surfaceVariant, accentedLightScheme.surfaceContainerHigh)
                assertEquals(accentedDarkScheme.surfaceVariant, accentedDarkScheme.surfaceContainerHighest)
                assertEquals(accentedLightScheme.secondary, accentedLightScheme.outlineVariant)
                assertEquals(accentedDarkScheme.secondary, accentedDarkScheme.outlineVariant)
            }
    }

    @Test
    fun customAccentOverridesTheAccentRolesOnly() {
        val colors = LauncherThemeColors(accentArgb = 0xFF405060.toInt())

        val scheme = lightScheme.withThemeColors(colors)

        assertEquals(lightScheme.background, scheme.background)
        assertEquals(lightScheme.surface, scheme.surface)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF405060), scheme.primary)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF405060), scheme.secondary)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF405060), scheme.secondaryContainer)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF405060), scheme.tertiaryContainer)
    }

    @Test
    fun glassProvidesTranslucentLauncherOverlayTokens() {
        val glassTokens = launcherThemeSurfaceTokens(lightScheme)

        assertEquals(0.78f, glassTokens.overlayAlpha)
        assertEquals(glassTokens.overlayAlpha, glassTokens.panelColor.alpha, 0.01f)
        assertEquals(glassTokens.overlayAlpha, glassTokens.menuColor.alpha, 0.01f)
    }

    @Test
    fun reducedTransparencyUsesOpaqueLauncherOverlayTokens() {
        val tokens =
            launcherThemeSurfaceTokens(colorScheme = lightScheme, reducedTransparency = true)

        assertEquals(1f, tokens.overlayAlpha)
        assertEquals(1f, tokens.panelColor.alpha)
        assertEquals(1f, tokens.menuColor.alpha)
    }

    @Test
    fun glassSurfacesKeepSearchWidgetAndMenuContentReadableOverDarkAndLightWallpapers() {
        val scheme = midToneScheme()
        val tokens = launcherThemeSurfaceTokens(scheme)
        val overlays =
            listOf(
                "search" to (tokens.panelColor to tokens.panelContentColor),
                "widget" to (tokens.panelColor to tokens.panelContentColor),
                "menu" to (tokens.menuColor to tokens.menuContentColor),
            )

        overlays.forEach { (name, surfaceAndContent) ->
            val (surface, content) = surfaceAndContent
            listOf(Color.Black, Color.White).forEach { wallpaper ->
                val renderedSurface = surface.compositeOver(wallpaper)
                assertTrue(
                    "$name content must contrast over $wallpaper",
                    contrastRatio(content, renderedSurface) >= 4.5f,
                )
            }
        }
    }

    @Test
    fun reducedTransparencyUsesOpaqueSurfacesWithoutGlassScrims() {
        val scheme = midToneScheme()
        val tokens = launcherThemeSurfaceTokens(scheme, reducedTransparency = true)

        assertEquals(scheme.surface, tokens.panelColor)
        assertEquals(scheme.surfaceContainerHigh, tokens.menuColor)
        assertEquals(Color.Black, tokens.panelContentColor)
        assertEquals(Color.Black, tokens.menuContentColor)
    }

    @Test
    fun wallpaperAwareGlassSurfaceAddsAScrimWhenForegroundAloneWouldFail() {
        val customSurface = Color(0x00101010)
        val surface = wallpaperAwareSurface(customSurface, overlayAlpha = 0.78f, fallback = Color.Black)
        val renderedSurface = surface.color.compositeOver(Color.Black)

        assertEquals(Color.White, surface.contentColor)
        assertTrue(contrastRatio(surface.contentColor, renderedSurface) >= 4.5f)
    }

    @Test
    fun defaultHomeLabelTextContrastsWithItsScrimBackground() {
        assertEquals(Color.White, wallpaperReadableLabelColors(lightScheme).text)
    }

    @Test
    fun midToneCustomAccentUsesReadableForegroundsForSecondaryAndTertiaryContainers() {
        val scheme = lightScheme.withThemeColors(LauncherThemeColors(accentArgb = 0xFF808080.toInt()))

        assertEquals(androidx.compose.ui.graphics.Color.Black, scheme.onSecondary)
        assertEquals(androidx.compose.ui.graphics.Color.Black, scheme.onSecondaryContainer)
        assertEquals(androidx.compose.ui.graphics.Color.Black, scheme.onTertiary)
        assertEquals(androidx.compose.ui.graphics.Color.Black, scheme.onTertiaryContainer)
    }

    @Test
    fun parsesRgbAndArgbThemeHexValues() {
        assertEquals(androidx.compose.ui.graphics.Color(0xFF102030), parseThemeColorHex("#102030"))
        assertEquals(androidx.compose.ui.graphics.Color(0x80102030), parseThemeColorHex("#80102030"))
        assertEquals(null, parseThemeColorHex("#12345"))
    }

    @Test
    fun formatsThemeColoursUsingAndroidArgbIncludingAlpha() {
        assertEquals("#80102030", Color(0x80102030).toThemeHex())
    }

    @Test
    fun standardCornersSitOnTheRadiusScale() {
        assertEquals(RiffleShapes.continuous(24.dp), launcherCardShape())
        assertEquals(RiffleShapes.continuous(32.dp), launcherPanelShape())
    }

    @Test
    fun cornerChoiceChangesCardAndPanelShapes() {
        assertEquals(RiffleShapes.continuous(8.dp), launcherCardShape(LauncherThemeCornerStyle.COMPACT))
        assertEquals(RiffleShapes.continuous(28.dp), launcherCardShape(LauncherThemeCornerStyle.ROUNDED))
        assertEquals(RiffleShapes.continuous(12.dp), launcherPanelShape(LauncherThemeCornerStyle.COMPACT))
        assertEquals(RiffleShapes.continuous(36.dp), launcherPanelShape(LauncherThemeCornerStyle.ROUNDED))
    }

    @Test
    fun typographyChoiceSwapsOnlyTheFontFamily() {
        assertEquals(
            FontFamily.Monospace,
            launcherTypography(LauncherThemeTypography.MONOSPACE).bodyMedium.fontFamily,
        )
        assertNotEquals(
            FontFamily.Monospace,
            launcherTypography(LauncherThemeTypography.SYSTEM).bodyMedium.fontFamily,
        )
        assertSame(
            launcherTypography(LauncherThemeTypography.PRESET),
            launcherTypography(LauncherThemeTypography.SYSTEM),
        )
    }

    @Test
    fun typographyUsesLightDisplayAndTrackedMediumLabels() {
        val typography = launcherTypography()

        assertEquals(FontWeight.Light, typography.displayLarge.fontWeight)
        assertEquals(FontWeight.Light, typography.displaySmall.fontWeight)
        assertEquals(FontWeight.Medium, typography.labelMedium.fontWeight)
        assertTrue(typography.labelMedium.letterSpacing.value > Typography().labelMedium.letterSpacing.value)
    }

    @Test
    fun monospaceSwapKeepsTheRiffleTypeTuning() {
        val typography = launcherTypography(LauncherThemeTypography.MONOSPACE)

        assertEquals(FontFamily.Monospace, typography.displayLarge.fontFamily)
        assertEquals(FontWeight.Light, typography.displayLarge.fontWeight)
        assertEquals(
            launcherTypography().labelSmall.letterSpacing,
            typography.labelSmall.letterSpacing,
        )
    }

    @Test
    fun materialShapesStayOnTheRadiusScale() {
        assertEquals(RiffleShapes.materialShapes(), launcherMaterialShapes())
    }

    private fun midToneScheme() =
        lightScheme.copy(surface = Color(0xFF808080), surfaceContainerHigh = Color(0xFF808080))
}
