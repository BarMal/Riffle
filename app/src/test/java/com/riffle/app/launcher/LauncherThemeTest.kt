package com.riffle.app.launcher

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
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
    fun customPresetKeepsTheMaterialFallbackUntilCustomTokensExist() {
        assertSame(
            lightScheme,
            fallbackScheme(darkTheme = false, themePreset = LauncherThemePreset.CUSTOM),
        )
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
    fun typographyTokenVariesForVictorianAndTerminal() {
        assertEquals(FontFamily.Serif, launcherTypography(LauncherThemePreset.VICTORIAN).headlineMedium.fontFamily)
        assertEquals(FontFamily.Serif, launcherTypography(LauncherThemePreset.VICTORIAN).bodyMedium.fontFamily)
        assertEquals(FontFamily.Monospace, launcherTypography(LauncherThemePreset.TERMINAL).headlineMedium.fontFamily)
        assertEquals(FontFamily.Monospace, launcherTypography(LauncherThemePreset.TERMINAL).bodyMedium.fontFamily)
        assertSame(launcherTypography(LauncherThemePreset.MATERIAL), launcherTypography(LauncherThemePreset.CUSTOM))
    }
}
