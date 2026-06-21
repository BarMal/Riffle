package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutJsonCodecTest {
    @Test
    fun roundTripsWallpaperSettings() {
        val layout =
            HomeLayoutDefaults.standard().copy(
                settings =
                    HomeLayoutDefaults.standard().settings.copy(
                        wallpaper = WallpaperSettings(source = WallpaperSource.SOLID_COLOR),
                    ),
            )

        val decodedLayout = decodeHomeLayout(encodeHomeLayout(layout))

        assertEquals(WallpaperSource.SOLID_COLOR, decodedLayout.settings.wallpaper.source)
    }

    @Test
    fun defaultsWallpaperSettingsWhenOlderJsonDoesNotHaveSettings() {
        val decodedLayout =
            decodeHomeLayout(
                """
                {
                  "selectedPageId": "home",
                  "pages": [
                    {
                      "id": "home",
                      "columns": 4,
                      "rows": 5,
                      "items": []
                    }
                  ],
                  "dock": {
                    "capacity": 5,
                    "items": []
                  }
                }
                """.trimIndent(),
            )

        assertEquals(WallpaperSettings.system(), decodedLayout.settings.wallpaper)
    }
}
