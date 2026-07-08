package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.WallpaperSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsWallpaperMotionSettingTest {
    @Test
    fun enablesWallpaperMotionForSystemWallpaper() {
        val state = wallpaperScrollModeSettingState(WallpaperSource.SYSTEM)

        assertTrue(state.enabled)
        assertEquals("Move system wallpaper between home pages", state.subtitle)
    }

    @Test
    fun disablesWallpaperMotionForSolidColorWallpaper() {
        val state = wallpaperScrollModeSettingState(WallpaperSource.SOLID_COLOR)

        assertFalse(state.enabled)
        assertEquals("Available when using system wallpaper", state.subtitle)
    }
}
