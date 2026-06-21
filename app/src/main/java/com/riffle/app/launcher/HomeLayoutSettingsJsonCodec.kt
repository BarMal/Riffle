package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutSettings
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import org.json.JSONObject

fun encodeSettings(settings: HomeLayoutSettings): JSONObject =
    JSONObject()
        .put("wallpaper", encodeWallpaper(settings.wallpaper))

fun JSONObject.toSettings(defaults: HomeLayoutSettings): HomeLayoutSettings =
    defaults.copy(
        wallpaper = optJSONObject("wallpaper")?.toWallpaperSettings(defaults.wallpaper) ?: defaults.wallpaper,
    )

private fun encodeWallpaper(settings: WallpaperSettings): JSONObject =
    JSONObject()
        .put("source", settings.source.name)

private fun JSONObject.toWallpaperSettings(defaults: WallpaperSettings): WallpaperSettings =
    WallpaperSettings(
        source =
            runCatching { WallpaperSource.valueOf(optString("source")) }
                .getOrDefault(defaults.source),
    )
