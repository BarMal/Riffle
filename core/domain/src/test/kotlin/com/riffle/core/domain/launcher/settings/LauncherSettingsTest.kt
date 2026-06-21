package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import kotlin.test.Test
import kotlin.test.assertEquals

class LauncherSettingsTest {
    @Test
    fun defaultsToSystemWallpaper() {
        val settings = LauncherSettings()

        assertEquals(WallpaperSettings.system(), settings.appearance.wallpaper)
    }

    @Test
    fun appearanceCanSelectSolidColourWallpaperFallback() {
        val settings =
            LauncherSettings(
                appearance =
                    AppearanceSettings(
                        wallpaper = WallpaperSettings(source = WallpaperSource.SOLID_COLOR),
                    ),
            )

        assertEquals(WallpaperSource.SOLID_COLOR, settings.appearance.wallpaper.source)
    }
}
