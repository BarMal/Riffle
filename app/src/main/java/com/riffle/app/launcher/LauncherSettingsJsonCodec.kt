package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import com.riffle.core.domain.launcher.settings.HapticSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.OverlayDockEdge
import com.riffle.core.domain.launcher.settings.OverlayDockSettings
import org.json.JSONObject

fun encodeLauncherSettings(settings: LauncherSettings): String =
    JSONObject()
        .put("appearance", encodeAppearance(settings.appearance))
        .put("gestures", encodeGestures(settings.gestures))
        .put("haptics", encodeHaptics(settings.haptics))
        .put("overlayDock", encodeOverlayDock(settings.overlayDock))
        .toString()

fun decodeLauncherSettings(value: String): LauncherSettings =
    JSONObject(value).let { json ->
        val defaults = LauncherSettings()
        defaults.copy(
            appearance = json.optJSONObject("appearance")?.toAppearance(defaults.appearance) ?: defaults.appearance,
            gestures = json.optJSONObject("gestures")?.toGestures(defaults.gestures) ?: defaults.gestures,
            haptics = json.optJSONObject("haptics")?.toHaptics(defaults.haptics) ?: defaults.haptics,
            overlayDock =
                json.optJSONObject("overlayDock")?.toOverlayDock(defaults.overlayDock) ?: defaults.overlayDock,
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

private fun encodeHaptics(settings: HapticSettings): JSONObject =
    JSONObject()
        .put("feedbackStrength", settings.feedbackStrength.name)

private fun JSONObject.toHaptics(defaults: HapticSettings): HapticSettings =
    defaults.copy(
        feedbackStrength =
            runCatching { HapticFeedbackStrength.valueOf(optString("feedbackStrength")) }
                .getOrDefault(defaults.feedbackStrength),
    )

private fun encodeOverlayDock(settings: OverlayDockSettings): JSONObject =
    JSONObject()
        .put("enabled", settings.enabled)
        .put("edge", settings.edge.name)
        .put("handleThicknessDp", settings.handleThicknessDp)
        .put("handleHeightDp", settings.handleHeightDp)
        .put("verticalOffsetDp", settings.verticalOffsetDp)
        .put("handleAlphaPercent", settings.handleAlphaPercent)

private fun JSONObject.toOverlayDock(defaults: OverlayDockSettings): OverlayDockSettings =
    defaults.copy(
        enabled = optBoolean("enabled", defaults.enabled),
        edge =
            runCatching { OverlayDockEdge.valueOf(optString("edge")) }
                .getOrDefault(defaults.edge),
        handleThicknessDp = optInt("handleThicknessDp", defaults.handleThicknessDp),
        handleHeightDp = optInt("handleHeightDp", defaults.handleHeightDp),
        verticalOffsetDp = optInt("verticalOffsetDp", defaults.verticalOffsetDp),
        handleAlphaPercent = optInt("handleAlphaPercent", defaults.handleAlphaPercent),
    )
