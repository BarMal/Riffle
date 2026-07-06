package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.WallpaperSource
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherWallpaperControllerTest {
    @Test
    fun systemWallpaperSourceUsesTransparentWallpaperWindow() {
        assertEquals(
            LauncherWallpaperWindowCommand.ShowSystemWallpaper,
            WallpaperSource.SYSTEM.launcherWallpaperWindowCommand(),
        )
    }

    @Test
    fun solidColorWallpaperSourceUsesSolidColorWindow() {
        assertEquals(
            LauncherWallpaperWindowCommand.ShowSolidColor,
            WallpaperSource.SOLID_COLOR.launcherWallpaperWindowCommand(),
        )
    }
}
