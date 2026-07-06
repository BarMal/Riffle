package com.riffle.app.launcher

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.view.WindowManager
import com.riffle.core.domain.launcher.home.WallpaperSource

interface LauncherWallpaperController {
    fun applySource(source: WallpaperSource)
}

class AndroidLauncherWallpaperController(
    private val window: Window,
) : LauncherWallpaperController {
    override fun applySource(source: WallpaperSource) {
        when (source.launcherWallpaperWindowCommand()) {
            LauncherWallpaperWindowCommand.ShowSystemWallpaper -> {
                window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER,
                    WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER,
                )
            }

            LauncherWallpaperWindowCommand.ShowSolidColor -> {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                window.setBackgroundDrawable(null)
            }
        }
    }
}

object NoopLauncherWallpaperController : LauncherWallpaperController {
    override fun applySource(source: WallpaperSource) = Unit
}

internal enum class LauncherWallpaperWindowCommand {
    ShowSystemWallpaper,
    ShowSolidColor,
}

internal fun WallpaperSource.launcherWallpaperWindowCommand(): LauncherWallpaperWindowCommand =
    when (this) {
        WallpaperSource.SYSTEM -> LauncherWallpaperWindowCommand.ShowSystemWallpaper
        WallpaperSource.SOLID_COLOR -> LauncherWallpaperWindowCommand.ShowSolidColor
    }
