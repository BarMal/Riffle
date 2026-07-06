package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.WallpaperScrollMode
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutWallpaperJsonCodecTest {
    @Test
    fun roundTripsWallpaperScrollMode() {
        val layout =
            HomeLayoutDefaults.standard().copy(
                settings =
                    HomeLayoutDefaults.standard().settings.copy(
                        wallpaper =
                            WallpaperSettings(
                                source = WallpaperSource.SYSTEM,
                                scrollMode = WallpaperScrollMode.SCROLLING,
                            ),
                    ),
            )

        val decodedLayout = decodeHomeLayout(encodeHomeLayout(layout))

        assertEquals(WallpaperScrollMode.SCROLLING, decodedLayout.settings.wallpaper.scrollMode)
    }
}
