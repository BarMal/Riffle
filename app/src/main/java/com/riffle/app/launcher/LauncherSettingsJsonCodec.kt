package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.json.JSONObject

fun encodeLauncherSettings(settings: LauncherSettings): String =
    JSONObject()
        .put("appearance", encodeAppearance(settings.appearance))
        .toString()

fun decodeLauncherSettings(value: String): LauncherSettings =
    JSONObject(value).let { json ->
        val defaults = LauncherSettings()
        defaults.copy(
            appearance = json.optJSONObject("appearance")?.toAppearance(defaults.appearance) ?: defaults.appearance,
        )
    }

private fun encodeAppearance(settings: AppearanceSettings): JSONObject =
    JSONObject()
        .put("wallpaper", encodeWallpaper(settings.wallpaper))

private fun JSONObject.toAppearance(defaults: AppearanceSettings): AppearanceSettings =
    defaults.copy(
        wallpaper = optJSONObject("wallpaper")?.toWallpaper(defaults.wallpaper) ?: defaults.wallpaper,
    )

private fun encodeWallpaper(settings: WallpaperSettings): JSONObject =
    JSONObject()
        .put("source", settings.source.name)

private fun JSONObject.toWallpaper(defaults: WallpaperSettings): WallpaperSettings =
    WallpaperSettings(
        source =
            runCatching { WallpaperSource.valueOf(optString("source")) }
                .getOrDefault(defaults.source),
    )
