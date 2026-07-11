package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.LauncherThemePreset
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
}
