package com.riffle.app.launcher

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.settings.LauncherThemeAccent
import com.riffle.core.domain.launcher.settings.LauncherThemeColors
import com.riffle.core.domain.launcher.settings.LauncherThemeCornerStyle
import com.riffle.core.domain.launcher.settings.LauncherThemePreset
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
    fun nonMaterialPresetsUseDistinctFallbackPrimaryColors() {
        val materialPrimary = fallbackScheme(darkTheme = false).primary

        LauncherThemePreset.entries
            .filterNot { preset -> preset in setOf(LauncherThemePreset.MATERIAL, LauncherThemePreset.CUSTOM) }
            .forEach { preset ->
                assertNotEquals(
                    "preset=$preset",
                    materialPrimary,
                    fallbackScheme(darkTheme = false, themePreset = preset).primary,
                )
            }
    }

    @Test
    fun customPresetKeepsTheMaterialFallbackUntilCustomTokensExist() {
        assertSame(
            lightScheme,
            fallbackScheme(darkTheme = false, themePreset = LauncherThemePreset.CUSTOM),
        )
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
    fun customThemeColoursOverrideUserReachableBackgroundAndAccentRoles() {
        val colors = LauncherThemeColors(backgroundArgb = 0xFF102030.toInt(), accentArgb = 0xFF405060.toInt())

        val scheme = lightScheme.withThemeColors(colors)

        assertEquals(androidx.compose.ui.graphics.Color(0xFF102030), scheme.background)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF102030), scheme.surface)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF405060), scheme.primary)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF405060), scheme.secondary)
    }

    @Test
    fun parsesRgbAndArgbThemeHexValues() {
        assertEquals(androidx.compose.ui.graphics.Color(0xFF102030), parseThemeColorHex("#102030"))
        assertEquals(androidx.compose.ui.graphics.Color(0x80102030), parseThemeColorHex("#80102030"))
        assertEquals(null, parseThemeColorHex("#12345"))
    }

    @Test
    fun cardShapeTokenVariesByPreset() {
        assertEquals(RoundedCornerShape(0.dp), launcherCardShape(LauncherThemePreset.TERMINAL))
        assertEquals(RoundedCornerShape(28.dp), launcherCardShape(LauncherThemePreset.GLASS))
        assertEquals(RoundedCornerShape(24.dp), launcherCardShape(LauncherThemePreset.MATERIAL))
    }

    @Test
    fun panelShapeTokenVariesByPreset() {
        assertEquals(RoundedCornerShape(0.dp), launcherPanelShape(LauncherThemePreset.TERMINAL))
        assertEquals(RoundedCornerShape(36.dp), launcherPanelShape(LauncherThemePreset.GLASS))
        assertEquals(RoundedCornerShape(32.dp), launcherPanelShape(LauncherThemePreset.MATERIAL))
    }

    @Test
    fun typographyTokenVariesForTerminal() {
        assertEquals(FontFamily.Monospace, launcherTypography(LauncherThemePreset.TERMINAL).headlineMedium.fontFamily)
        assertEquals(FontFamily.Monospace, launcherTypography(LauncherThemePreset.TERMINAL).bodyMedium.fontFamily)
        assertSame(launcherTypography(LauncherThemePreset.MATERIAL), launcherTypography(LauncherThemePreset.CUSTOM))
    }

    @Test
    fun cornerOverrideTakesPrecedenceOverPresetShape() {
        assertEquals(
            RoundedCornerShape(8.dp),
            launcherCardShape(LauncherThemePreset.GLASS, LauncherThemeCornerStyle.COMPACT),
        )
        assertEquals(
            RoundedCornerShape(36.dp),
            launcherPanelShape(LauncherThemePreset.TERMINAL, LauncherThemeCornerStyle.ROUNDED),
        )
    }

    @Test
    fun typographyOverrideTakesPrecedenceOverPresetTypography() {
        assertEquals(
            FontFamily.Monospace,
            launcherTypography(LauncherThemePreset.MATERIAL, LauncherThemeTypography.MONOSPACE).bodyMedium.fontFamily,
        )
        assertNotEquals(
            FontFamily.Monospace,
            launcherTypography(LauncherThemePreset.TERMINAL, LauncherThemeTypography.SYSTEM).bodyMedium.fontFamily,
        )
    }
}
