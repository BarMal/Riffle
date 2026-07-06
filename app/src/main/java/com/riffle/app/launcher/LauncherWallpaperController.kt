package com.riffle.app.launcher

import android.app.WallpaperManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.view.WindowManager
import com.riffle.core.domain.launcher.home.WallpaperScrollMode
import com.riffle.core.domain.launcher.home.WallpaperSource

interface LauncherWallpaperController {
    fun applySource(source: WallpaperSource): LauncherWallpaperApplyResult

    fun applyOffset(command: LauncherWallpaperOffsetCommand)
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

    override fun applyOffset(command: LauncherWallpaperOffsetCommand) {
        runCatching { wallpaperWindow.applyOffset(command) }
    }
}

object NoopLauncherWallpaperController : LauncherWallpaperController {
    override fun applySource(source: WallpaperSource) = LauncherWallpaperApplyResult.Applied(source)

    override fun applyOffset(command: LauncherWallpaperOffsetCommand) = Unit
}

data class LauncherWallpaperOffsetCommand(
    val xOffset: Float,
    val xOffsetStep: Float,
)

internal enum class LauncherWallpaperWindowCommand {
    ShowSystemWallpaper,
    ShowSolidColor,
}

internal interface LauncherWallpaperWindow {
    fun apply(command: LauncherWallpaperWindowCommand)

    fun applyOffset(command: LauncherWallpaperOffsetCommand)
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

    override fun applyOffset(command: LauncherWallpaperOffsetCommand) {
        val token = window.decorView.windowToken ?: return
        WallpaperManager.getInstance(window.context).setWallpaperOffsetSteps(command.xOffsetStep, 1f)
        WallpaperManager.getInstance(window.context).setWallpaperOffsets(token, command.xOffset, 0f)
    }
}

internal fun wallpaperOffsetCommand(
    scrollMode: WallpaperScrollMode,
    selectedPageIndex: Int,
    pageCount: Int,
): LauncherWallpaperOffsetCommand =
    when {
        scrollMode == WallpaperScrollMode.STATIC || pageCount <= 1 ->
            LauncherWallpaperOffsetCommand(
                xOffset = STATIC_WALLPAPER_X_OFFSET,
                xOffsetStep = 1f,
            )

        else -> {
            val lastPageIndex = pageCount - 1
            val safeSelectedPageIndex = selectedPageIndex.coerceIn(0, lastPageIndex)
            LauncherWallpaperOffsetCommand(
                xOffset = safeSelectedPageIndex.toFloat() / lastPageIndex.toFloat(),
                xOffsetStep = 1f / lastPageIndex.toFloat(),
            )
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

internal fun LauncherWallpaperApplyResult.fallbackWallpaperSourceAction(): LauncherShellAction.SelectWallpaperSource? =
    when (this) {
        is LauncherWallpaperApplyResult.Applied -> null
        is LauncherWallpaperApplyResult.Failed ->
            fallbackSource
                ?.takeIf { source -> source != requestedSource }
                ?.let { source -> LauncherShellAction.SelectWallpaperSource(source) }
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

private const val STATIC_WALLPAPER_X_OFFSET = 0.5f
