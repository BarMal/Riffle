package com.riffle.app.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

interface WallpaperPickerGateway {
    fun launchWallpaperPicker(): WallpaperPickerLaunchResult
}

enum class WallpaperPickerLaunchResult {
    Launched,
    Unavailable,
    Failed,
}

class AndroidWallpaperPickerGateway(
    private val context: Context,
) : WallpaperPickerGateway {
    override fun launchWallpaperPicker(): WallpaperPickerLaunchResult =
        launchWallpaperPicker(
            isAvailable = { intent -> intent.canResolve(context.packageManager) },
            launch = context::startActivity,
        )
}

internal fun launchWallpaperPicker(
    isAvailable: (Intent) -> Boolean,
    launch: (Intent) -> Unit,
    createChooser: (Intent) -> Intent = ::wallpaperPickerChooserIntent,
): WallpaperPickerLaunchResult {
    val intent = wallpaperPickerIntent()
    if (!isAvailable(intent)) {
        return WallpaperPickerLaunchResult.Unavailable
    }
    return runCatching { launch(createChooser(intent)) }
        .fold(
            onSuccess = { WallpaperPickerLaunchResult.Launched },
            onFailure = { WallpaperPickerLaunchResult.Failed },
        )
}

internal fun wallpaperPickerIntent(): Intent = Intent(WALLPAPER_PICKER_ACTION)

internal const val WALLPAPER_PICKER_ACTION: String = Intent.ACTION_SET_WALLPAPER
internal const val WALLPAPER_PICKER_CHOOSER_TITLE: String = "Change wallpaper"

internal fun wallpaperPickerChooserIntent(intent: Intent): Intent {
    return Intent.createChooser(intent, WALLPAPER_PICKER_CHOOSER_TITLE)
}

private fun Intent.canResolve(packageManager: PackageManager): Boolean = resolveActivity(packageManager) != null
