package com.riffle.app.launcher

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.view.WindowManager
import com.riffle.core.domain.launcher.home.WallpaperSource

interface LauncherWallpaperController {
    fun applySource(source: WallpaperSource): LauncherWallpaperApplyResult
}

sealed interface LauncherWallpaperApplyResult {
    val requestedSource: WallpaperSource

    data class Applied(
        override val requestedSource: WallpaperSource,
    ) : LauncherWallpaperApplyResult

    data class Failed(
        override val requestedSource: WallpaperSource,
        val fallbackSource: WallpaperSource?,
    ) : LauncherWallpaperApplyResult
}

class AndroidLauncherWallpaperController internal constructor(
    private val wallpaperWindow: LauncherWallpaperWindow,
) : LauncherWallpaperController {
    constructor(window: Window) : this(AndroidLauncherWallpaperWindow(window))

    override fun applySource(source: WallpaperSource): LauncherWallpaperApplyResult =
        runCatching {
            wallpaperWindow.apply(source.launcherWallpaperWindowCommand())
        }.fold(
            onSuccess = { LauncherWallpaperApplyResult.Applied(source) },
            onFailure = { source.fallbackResultAfterApplyFailure(wallpaperWindow) },
        )
}

object NoopLauncherWallpaperController : LauncherWallpaperController {
    override fun applySource(source: WallpaperSource) = LauncherWallpaperApplyResult.Applied(source)
}

internal enum class LauncherWallpaperWindowCommand {
    ShowSystemWallpaper,
    ShowSolidColor,
}

internal interface LauncherWallpaperWindow {
    fun apply(command: LauncherWallpaperWindowCommand)
}

private class AndroidLauncherWallpaperWindow(
    private val window: Window,
) : LauncherWallpaperWindow {
    override fun apply(command: LauncherWallpaperWindowCommand) {
        when (command) {
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

internal fun WallpaperSource.launcherWallpaperWindowCommand(): LauncherWallpaperWindowCommand =
    when (this) {
        WallpaperSource.SYSTEM -> LauncherWallpaperWindowCommand.ShowSystemWallpaper
        WallpaperSource.SOLID_COLOR -> LauncherWallpaperWindowCommand.ShowSolidColor
    }

internal fun LauncherWallpaperApplyResult.failureMessage(): String? =
    when (this) {
        is LauncherWallpaperApplyResult.Applied -> null
        is LauncherWallpaperApplyResult.Failed ->
            when (fallbackSource) {
                WallpaperSource.SOLID_COLOR -> "System wallpaper could not be shown; using solid background."
                null -> "Wallpaper could not be updated."
                WallpaperSource.SYSTEM -> "Wallpaper could not be updated."
            }
    }

private fun WallpaperSource.fallbackResultAfterApplyFailure(
    wallpaperWindow: LauncherWallpaperWindow,
): LauncherWallpaperApplyResult.Failed {
    val fallbackSource =
        if (this == WallpaperSource.SYSTEM) {
            runCatching {
                wallpaperWindow.apply(WallpaperSource.SOLID_COLOR.launcherWallpaperWindowCommand())
            }.getOrNull()
                ?.let { WallpaperSource.SOLID_COLOR }
        } else {
            null
        }
    return LauncherWallpaperApplyResult.Failed(
        requestedSource = this,
        fallbackSource = fallbackSource,
    )
}
