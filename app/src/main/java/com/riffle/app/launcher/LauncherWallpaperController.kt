package com.riffle.app.launcher

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.view.WindowManager

interface LauncherWallpaperController {
    fun showSystemWallpaper()
}

class AndroidLauncherWallpaperController(
    private val window: Window,
) : LauncherWallpaperController {
    override fun showSystemWallpaper() {
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER,
            WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER,
        )
    }
}

object NoopLauncherWallpaperController : LauncherWallpaperController {
    override fun showSystemWallpaper() = Unit
}
