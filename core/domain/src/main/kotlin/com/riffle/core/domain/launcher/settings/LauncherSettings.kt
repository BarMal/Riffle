package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.home.WallpaperSettings

data class LauncherSettings(
    val appearance: AppearanceSettings = AppearanceSettings(),
)

data class AppearanceSettings(
    val wallpaper: WallpaperSettings = WallpaperSettings.system(),
)

interface LauncherSettingsRepository {
    fun loadLauncherSettings(): LauncherSettings?

    fun saveLauncherSettings(settings: LauncherSettings)
}
