@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.cards.CardsChapterId
import com.riffle.core.domain.launcher.cards.CardsChapterPreferences
import com.riffle.core.domain.launcher.home.WallpaperScrollMode
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.CardsSettings
import com.riffle.core.domain.launcher.settings.CustomThemeSettings
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import com.riffle.core.domain.launcher.settings.HapticSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherThemeAccent
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import com.riffle.core.domain.launcher.settings.LauncherThemePreset
import com.riffle.core.domain.launcher.settings.MAX_CUSTOM_THEME_CARD_CORNER_RADIUS_DP
import com.riffle.core.domain.launcher.settings.MIN_CUSTOM_THEME_CARD_CORNER_RADIUS_DP
import com.riffle.core.domain.launcher.settings.OverlayDockEdge
import com.riffle.core.domain.launcher.settings.OverlayDockExpandedOrientation
import com.riffle.core.domain.launcher.settings.OverlayDockSettings
import com.riffle.core.domain.launcher.settings.coerceOverlayDockSettings
import com.riffle.core.domain.launcher.settings.homeSystemBars
import com.riffle.core.domain.launcher.settings.withHomeSystemBars
import org.json.JSONArray
import org.json.JSONObject

fun encodeLauncherSettings(settings: LauncherSettings): String =
    JSONObject()
        .put("version", LAUNCHER_SETTINGS_JSON_VERSION)
        .put("appearance", encodeAppearance(settings.appearance))
        .put("cards", encodeCardsSettings(settings.cards))
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
            cards = json.optJSONObject("cards")?.toCardsSettings(defaults.cards) ?: defaults.cards,
            contextual = json.optJSONObject("contextual")?.toContextual(defaults.contextual) ?: defaults.contextual,
            gestures = json.optJSONObject("gestures")?.toGestures(defaults.gestures) ?: defaults.gestures,
            haptics = json.optJSONObject("haptics")?.toHaptics(defaults.haptics) ?: defaults.haptics,
            motion = json.optJSONObject("motion")?.toMotionSettings(defaults.motion) ?: defaults.motion,
            overlayDock =
                json.optJSONObject("overlayDock")?.toOverlayDock(defaults.overlayDock) ?: defaults.overlayDock,
        )
    }

private fun encodeCardsSettings(settings: CardsSettings): JSONObject =
    JSONObject()
        .put("pinnedChapterIds", JSONArray(settings.chapterPreferences.pinnedChapterIds.map(::encodeCardsAppChapterId)))
        .put("selectedChapterId", encodeCardsChapterId(settings.chapterPreferences.selectedChapterId))

private fun encodeCardsChapterId(id: CardsChapterId): JSONObject =
    when (id) {
        CardsChapterId.Overview -> JSONObject().put("kind", "overview")
        is CardsChapterId.App -> encodeCardsAppChapterId(id).put("kind", "app")
    }

private fun encodeCardsAppChapterId(id: CardsChapterId.App): JSONObject =
    JSONObject().put("packageName", id.packageName.value).put("profileId", id.profileId.value)

private fun JSONObject.toCardsSettings(defaults: CardsSettings): CardsSettings {
    val pinnedChapterIds =
        optJSONArray("pinnedChapterIds")
            ?.let { ids ->
                (0 until ids.length())
                    .mapNotNull { index -> ids.optJSONObject(index)?.toCardsAppChapterId() }
                    .distinct()
            }
            ?: defaults.chapterPreferences.pinnedChapterIds
    val selectedChapterId =
        optJSONObject("selectedChapterId")?.toCardsChapterId() ?: defaults.chapterPreferences.selectedChapterId
    return CardsSettings(CardsChapterPreferences(pinnedChapterIds, selectedChapterId))
}

private fun JSONObject.toCardsChapterId(): CardsChapterId? =
    when (optString("kind")) {
        "overview" -> CardsChapterId.Overview
        "app" -> toCardsAppChapterId()
        else -> null
    }

private fun JSONObject.toCardsAppChapterId(): CardsChapterId.App? =
    optString("packageName").takeIf(String::isNotBlank)?.let { packageName ->
        optString("profileId").takeIf(String::isNotBlank)?.let { profileId ->
            CardsChapterId.App(AppPackageName(packageName), AppProfileId(profileId))
        }
    }

private fun encodeAppearance(settings: AppearanceSettings): JSONObject =
    JSONObject()
        .put("fullscreenHome", settings.homeSystemBars.fullscreenHome)
        .put("hideStatusBarOnHome", settings.homeSystemBars.hideStatusBarOnHome)
        .put("hideNavigationBarOnHome", settings.homeSystemBars.hideNavigationBarOnHome)
        .put("themeMode", settings.themeMode.name)
        .put("themePreset", settings.themePreset.name)
        .put("themeAccent", settings.themeAccent.name)
        .put("customTheme", encodeCustomTheme(settings.customTheme))
        .put("wallpaper", encodeWallpaper(settings.wallpaper))

private fun JSONObject.toAppearance(defaults: AppearanceSettings): AppearanceSettings {
    val fullscreenHome = optBoolean("fullscreenHome", defaults.homeSystemBars.fullscreenHome)
    val homeSystemBars =
        defaults.homeSystemBars.copy(
            fullscreenHome = fullscreenHome,
            hideStatusBarOnHome = optBoolean("hideStatusBarOnHome", fullscreenHome),
            hideNavigationBarOnHome = optBoolean("hideNavigationBarOnHome", fullscreenHome),
        )
    return defaults
        .copy(
            themeMode =
                runCatching { LauncherThemeMode.valueOf(optString("themeMode")) }
                    .getOrDefault(defaults.themeMode),
            themePreset =
                runCatching { LauncherThemePreset.valueOf(optString("themePreset")) }
                    .getOrDefault(defaults.themePreset),
            themeAccent =
                runCatching { LauncherThemeAccent.valueOf(optString("themeAccent")) }
                    .getOrDefault(defaults.themeAccent),
            customTheme = optJSONObject("customTheme")?.toCustomTheme(defaults.customTheme) ?: defaults.customTheme,
            wallpaper = optJSONObject("wallpaper")?.toWallpaper(defaults.wallpaper) ?: defaults.wallpaper,
        ).withHomeSystemBars(homeSystemBars)
}

private fun encodeCustomTheme(settings: CustomThemeSettings): JSONObject =
    JSONObject().put("cardCornerRadiusDp", settings.cardCornerRadiusDp)

private fun JSONObject.toCustomTheme(defaults: CustomThemeSettings): CustomThemeSettings =
    defaults.copy(
        cardCornerRadiusDp =
            optInt("cardCornerRadiusDp", defaults.cardCornerRadiusDp)
                .coerceIn(MIN_CUSTOM_THEME_CARD_CORNER_RADIUS_DP, MAX_CUSTOM_THEME_CARD_CORNER_RADIUS_DP),
    )

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
        items =
            optJSONArray("items")?.let { items ->
                (0 until items.length()).mapNotNull { index ->
                    items.optJSONObject(index)?.let { shortcut ->
                        runCatching { shortcut.toShortcut() }.getOrNull()
                    }
                }
            } ?: defaults.items,
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

internal const val LAUNCHER_SETTINGS_JSON_VERSION = 2
