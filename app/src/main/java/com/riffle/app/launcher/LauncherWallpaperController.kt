package com.riffle.app.launcher

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat

interface LauncherWallpaperController {
    fun showSystemWallpaper()
}

class AndroidLauncherWallpaperController(
    private val window: Window,
) : LauncherWallpaperController {
    override fun showSystemWallpaper() {
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER,
            WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER,
        )
    }
}

object NoopLauncherWallpaperController : LauncherWallpaperController {
    override fun showSystemWallpaper() = Unit
}
