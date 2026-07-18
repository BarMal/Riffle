package com.riffle.app.launcher

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.settings.CustomThemeSettings
import com.riffle.core.domain.launcher.settings.LauncherThemeAccent
import com.riffle.core.domain.launcher.settings.LauncherThemePreset
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
    fun customPresetKeepsTheMaterialFallbackForItsColorScheme() {
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
    fun cardShapeTokenVariesByPreset() {
        assertEquals(RoundedCornerShape(0.dp), launcherCardShape(LauncherThemePreset.TERMINAL))
        assertEquals(RoundedCornerShape(28.dp), launcherCardShape(LauncherThemePreset.GLASS))
        assertEquals(RoundedCornerShape(24.dp), launcherCardShape(LauncherThemePreset.MATERIAL))
        assertEquals(
            RoundedCornerShape(12.dp),
            launcherCardShape(LauncherThemePreset.CUSTOM, CustomThemeSettings(cardCornerRadiusDp = 12)),
        )
    }

    @Test
    fun panelShapeTokenVariesByPreset() {
        assertEquals(RoundedCornerShape(0.dp), launcherPanelShape(LauncherThemePreset.TERMINAL))
        assertEquals(RoundedCornerShape(36.dp), launcherPanelShape(LauncherThemePreset.GLASS))
        assertEquals(RoundedCornerShape(32.dp), launcherPanelShape(LauncherThemePreset.MATERIAL))
        assertEquals(
            RoundedCornerShape(20.dp),
            launcherPanelShape(LauncherThemePreset.CUSTOM, CustomThemeSettings(cardCornerRadiusDp = 12)),
        )
    }

    @Test
    fun typographyTokenVariesForTerminal() {
        assertEquals(FontFamily.Monospace, launcherTypography(LauncherThemePreset.TERMINAL).headlineMedium.fontFamily)
        assertEquals(FontFamily.Monospace, launcherTypography(LauncherThemePreset.TERMINAL).bodyMedium.fontFamily)
        assertSame(launcherTypography(LauncherThemePreset.MATERIAL), launcherTypography(LauncherThemePreset.CUSTOM))
    }
}
