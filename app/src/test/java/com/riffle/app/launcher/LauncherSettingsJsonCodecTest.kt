package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherSettingsJsonCodecTest {
    @Test
    fun roundTripsWallpaperSource() {
        val settings =
            LauncherSettings(
                appearance =
                    AppearanceSettings(
                        wallpaper = WallpaperSettings(source = WallpaperSource.SOLID_COLOR),
                    ),
            )

        val decodedSettings = decodeLauncherSettings(encodeLauncherSettings(settings))

        assertEquals(WallpaperSource.SOLID_COLOR, decodedSettings.appearance.wallpaper.source)
    }

    @Test
    fun defaultsMissingAppearanceSettings() {
        val decodedSettings = decodeLauncherSettings("{}")

        assertEquals(WallpaperSettings.system(), decodedSettings.appearance.wallpaper)
    }

    @Test
    fun defaultsUnknownWallpaperSource() {
        val decodedSettings =
            decodeLauncherSettings(
                """
                {
                  "appearance": {
                    "wallpaper": {
                      "source": "UNKNOWN"
                    }
                  }
                }
                """.trimIndent(),
            )

        assertEquals(WallpaperSettings.system(), decodedSettings.appearance.wallpaper)
    }
}
