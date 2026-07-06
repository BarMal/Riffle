package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.WallpaperScrollMode
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import com.riffle.core.domain.launcher.settings.HapticSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.OverlayDockEdge
import com.riffle.core.domain.launcher.settings.OverlayDockExpandedOrientation
import com.riffle.core.domain.launcher.settings.OverlayDockSettings
import com.riffle.core.domain.launcher.settings.coerceOverlayDockSettings
import org.json.JSONArray
import org.json.JSONObject

fun encodeLauncherSettings(settings: LauncherSettings): String =
    JSONObject()
        .put("version", LAUNCHER_SETTINGS_JSON_VERSION)
        .put("appearance", encodeAppearance(settings.appearance))
        .put("contextual", encodeContextual(settings.contextual))
        .put("gestures", encodeGestures(settings.gestures))
        .put("haptics", encodeHaptics(settings.haptics))
        .put("motion", encodeMotionSettings(settings.motion))
        .put("overlayDock", encodeOverlayDock(settings.overlayDock))
        .toString()

fun decodeLauncherSettings(value: String): LauncherSettings =
    JSONObject(value).let { json ->
        val defaults = LauncherSettings()
        defaults.copy(
            appearance = json.optJSONObject("appearance")?.toAppearance(defaults.appearance) ?: defaults.appearance,
            contextual = json.optJSONObject("contextual")?.toContextual(defaults.contextual) ?: defaults.contextual,
            gestures = json.optJSONObject("gestures")?.toGestures(defaults.gestures) ?: defaults.gestures,
            haptics = json.optJSONObject("haptics")?.toHaptics(defaults.haptics) ?: defaults.haptics,
            motion = json.optJSONObject("motion")?.toMotionSettings(defaults.motion) ?: defaults.motion,
            overlayDock =
                json.optJSONObject("overlayDock")?.toOverlayDock(defaults.overlayDock) ?: defaults.overlayDock,
        )
    }

private fun encodeAppearance(settings: AppearanceSettings): JSONObject =
    JSONObject()
        .put("wallpaper", encodeWallpaper(settings.wallpaper))
        .put("fullscreenHome", settings.fullscreenHome)
        .put("hideStatusBarOnHome", settings.hideStatusBarOnHome)
        .put("hideNavigationBarOnHome", settings.hideNavigationBarOnHome)

private fun JSONObject.toAppearance(defaults: AppearanceSettings): AppearanceSettings {
    val fullscreenHome = optBoolean("fullscreenHome", defaults.fullscreenHome)
    return defaults.copy(
        wallpaper = optJSONObject("wallpaper")?.toWallpaper(defaults.wallpaper) ?: defaults.wallpaper,
        fullscreenHome = fullscreenHome,
        hideStatusBarOnHome = optBoolean("hideStatusBarOnHome", fullscreenHome),
        hideNavigationBarOnHome = optBoolean("hideNavigationBarOnHome", fullscreenHome),
    )
}

private fun encodeWallpaper(settings: WallpaperSettings): JSONObject =
    JSONObject()
        .put("source", settings.source.name)
        .put("scrollMode", settings.scrollMode.name)

private fun JSONObject.toWallpaper(defaults: WallpaperSettings): WallpaperSettings =
    WallpaperSettings(
        source =
            runCatching { WallpaperSource.valueOf(optString("source")) }
                .getOrDefault(defaults.source),
        scrollMode =
            runCatching { WallpaperScrollMode.valueOf(optString("scrollMode")) }
                .getOrDefault(defaults.scrollMode),
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
        .put("items", JSONArray(settings.items.map(::encodeShortcut)))
        .put("edge", settings.edge.name)
        .put("handleThicknessDp", settings.handleThicknessDp)
        .put("handleHeightDp", settings.handleHeightDp)
        .put("verticalOffsetDp", settings.verticalOffsetDp)
        .put("handleAlphaPercent", settings.handleAlphaPercent)
        .put("expandedIconSizeDp", settings.expandedIconSizeDp)
        .put("expandedOrientation", settings.expandedOrientation.name)
        .put("showLabels", settings.showLabels)

private fun JSONObject.toOverlayDock(defaults: OverlayDockSettings): OverlayDockSettings =
    defaults.copy(
        enabled = optBoolean("enabled", defaults.enabled),
        items = optJSONArray("items")?.toShortcuts() ?: defaults.items,
        edge =
            runCatching { OverlayDockEdge.valueOf(optString("edge")) }
                .getOrDefault(defaults.edge),
        handleThicknessDp = optInt("handleThicknessDp", defaults.handleThicknessDp),
        handleHeightDp = optInt("handleHeightDp", defaults.handleHeightDp),
        verticalOffsetDp = optInt("verticalOffsetDp", defaults.verticalOffsetDp),
        handleAlphaPercent = optInt("handleAlphaPercent", defaults.handleAlphaPercent),
        expandedIconSizeDp = optInt("expandedIconSizeDp", defaults.expandedIconSizeDp),
        expandedOrientation =
            runCatching { OverlayDockExpandedOrientation.valueOf(optString("expandedOrientation")) }
                .getOrDefault(defaults.expandedOrientation),
        showLabels = optBoolean("showLabels", defaults.showLabels),
    ).coerceOverlayDockSettings()

internal const val LAUNCHER_SETTINGS_JSON_VERSION = 1
