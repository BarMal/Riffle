package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridInsets
import com.riffle.core.domain.launcher.home.GridSettings
import com.riffle.core.domain.launcher.home.GridSpacing
import com.riffle.core.domain.launcher.home.HomeLayoutSettings
import com.riffle.core.domain.launcher.home.WallpaperScrollMode
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import org.json.JSONObject

fun encodeSettings(settings: HomeLayoutSettings): JSONObject =
    JSONObject()
        .put("grid", encodeGrid(settings.grid))
        .put("wallpaper", encodeWallpaper(settings.wallpaper))
        .put("labels", encodeLabels(settings.labels))

fun JSONObject.toSettings(defaults: HomeLayoutSettings): HomeLayoutSettings =
    defaults.copy(
        grid = optJSONObject("grid")?.toGridSettings(defaults.grid) ?: defaults.grid,
        wallpaper = optJSONObject("wallpaper")?.toWallpaperSettings(defaults.wallpaper) ?: defaults.wallpaper,
        labels = optJSONObject("labels")?.toLabelSettings(defaults.labels) ?: defaults.labels,
    )

private fun encodeGrid(settings: GridSettings): JSONObject =
    JSONObject()
        .put("columns", settings.dimensions.columns)
        .put("rows", settings.dimensions.rows)
        .put("margin", encodeInsets(settings.margin))
        .put("padding", encodeInsets(settings.padding))
        .put("cellSpacing", encodeSpacing(settings.cellSpacing))
        .put("compactLibraryPages", settings.compactLibraryPages)

private fun JSONObject.toGridSettings(defaults: GridSettings): GridSettings =
    defaults.copy(
        dimensions =
            GridDimensions(
                columns = optInt("columns", defaults.dimensions.columns),
                rows = optInt("rows", defaults.dimensions.rows),
            ),
        margin = optJSONObject("margin")?.toInsets(defaults.margin) ?: defaults.margin,
        padding = optJSONObject("padding")?.toInsets(defaults.padding) ?: defaults.padding,
        cellSpacing = optJSONObject("cellSpacing")?.toSpacing(defaults.cellSpacing) ?: defaults.cellSpacing,
        compactLibraryPages = optBoolean("compactLibraryPages", defaults.compactLibraryPages),
    )

private fun encodeInsets(insets: GridInsets): JSONObject =
    JSONObject()
        .put("start", insets.start)
        .put("top", insets.top)
        .put("end", insets.end)
        .put("bottom", insets.bottom)

private fun JSONObject.toInsets(defaults: GridInsets): GridInsets =
    GridInsets(
        start = optInt("start", defaults.start),
        top = optInt("top", defaults.top),
        end = optInt("end", defaults.end),
        bottom = optInt("bottom", defaults.bottom),
    )

private fun encodeSpacing(spacing: GridSpacing): JSONObject =
    JSONObject()
        .put("horizontal", spacing.horizontal)
        .put("vertical", spacing.vertical)

private fun JSONObject.toSpacing(defaults: GridSpacing): GridSpacing =
    GridSpacing(
        horizontal = optInt("horizontal", defaults.horizontal),
        vertical = optInt("vertical", defaults.vertical),
    )

private fun encodeWallpaper(settings: WallpaperSettings): JSONObject =
    JSONObject()
        .put("source", settings.source.name)
        .put("scrollMode", settings.scrollMode.name)

private fun JSONObject.toWallpaperSettings(defaults: WallpaperSettings): WallpaperSettings =
    WallpaperSettings(
        source =
            runCatching { WallpaperSource.valueOf(optString("source")) }
                .getOrDefault(defaults.source),
        scrollMode =
            runCatching { WallpaperScrollMode.valueOf(optString("scrollMode")) }
                .getOrDefault(defaults.scrollMode),
    )
