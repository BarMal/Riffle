@file:Suppress("TooManyFunctions", "LongMethod", "MaxLineLength")

package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.cards.AppStagePreferences
import com.riffle.core.domain.launcher.cards.CardsChapterId
import com.riffle.core.domain.launcher.cards.CardsChapterPreferences
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.WallpaperScrollMode
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.CardsSettings
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import com.riffle.core.domain.launcher.settings.HapticSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherThemeAccent
import com.riffle.core.domain.launcher.settings.LauncherThemeColors
import com.riffle.core.domain.launcher.settings.LauncherThemeCornerStyle
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import com.riffle.core.domain.launcher.settings.LauncherThemePreset
import com.riffle.core.domain.launcher.settings.LauncherThemeTypography
import com.riffle.core.domain.launcher.settings.OverlayDockEdge
import com.riffle.core.domain.launcher.settings.OverlayDockExpandedOrientation
import com.riffle.core.domain.launcher.settings.OverlayDockSettings
import com.riffle.core.domain.launcher.settings.TimeScapeAppearanceSettings
import com.riffle.core.domain.launcher.settings.TimeScapeGeometry
import com.riffle.core.domain.launcher.settings.TimeScapeMotion
import com.riffle.core.domain.launcher.settings.TimeScapeSurface
import com.riffle.core.domain.launcher.settings.TimeScapeTypography
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
        .put("stagePreferencesByLayout", JSONArray(settings.stagePreferencesByLayout.map(::encodeStagePreferences)))
        .put("timeScapeAppearance", encodeTimeScapeAppearance(settings.timeScapeAppearance))

private fun encodeStagePreferences(entry: Map.Entry<HomeLayoutKey, AppStagePreferences>): JSONObject =
    JSONObject()
        .put("viewMode", entry.key.viewMode.name)
        .put("deviceClass", entry.key.deviceClass.name)
        .put("pinnedStageIds", JSONArray(entry.value.pinnedStageIds.map(::encodeAppStageId)))
        .put("selectedStageId", entry.value.selectedStageId?.let(::encodeAppStageId))

private fun encodeAppStageId(id: AppStageId): JSONObject =
    JSONObject().put("packageName", id.packageName.value).put("profileId", id.profileId.value)

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
    val stagePreferencesByLayout =
        optJSONArray("stagePreferencesByLayout")
            ?.let { entries ->
                (0 until entries.length())
                    .mapNotNull { index -> entries.optJSONObject(index)?.toStagePreferencesEntry() }
                    .toMap()
            }
            ?: defaults.stagePreferencesByLayout
    return CardsSettings(
        chapterPreferences = CardsChapterPreferences(pinnedChapterIds, selectedChapterId),
        stagePreferencesByLayout = stagePreferencesByLayout,
        timeScapeAppearance =
            optJSONObject("timeScapeAppearance")?.toTimeScapeAppearance(defaults.timeScapeAppearance)
                ?: defaults.timeScapeAppearance,
    )
}

private fun encodeTimeScapeAppearance(settings: TimeScapeAppearanceSettings): JSONObject =
    settings.coerce().let { appearance ->
        JSONObject()
            .put("version", appearance.version)
            .put("preset", appearance.preset.name)
            .put(
                "geometry",
                JSONObject().put(
                    "cardAspectRatioPercent",
                    appearance.geometry.cardAspectRatioPercent,
                ).put(
                    "focusedScalePercent",
                    appearance.geometry.focusedScalePercent,
                ).put(
                    "focusedGapDp",
                    appearance.geometry.focusedGapDp,
                ).put(
                    "visibleDepth",
                    appearance.geometry.visibleDepth,
                ).put(
                    "overlapPercent",
                    appearance.geometry.overlapPercent,
                ).put(
                    "verticalSpacingDp",
                    appearance.geometry.verticalSpacingDp,
                ).put(
                    "horizontalOffsetDp",
                    appearance.geometry.horizontalOffsetDp,
                ).put(
                    "curveDp",
                    appearance.geometry.curveDp,
                ).put(
                    "fanDirection",
                    appearance.geometry.fanDirection.name,
                ).put(
                    "rotationDegrees",
                    appearance.geometry.rotationDegrees,
                ).put(
                    "cornerRadiusDp",
                    appearance.geometry.cornerRadiusDp,
                ).put("clipContent", appearance.geometry.clipContent).put("contentPaddingDp", appearance.geometry.contentPaddingDp),
            )
            .put(
                "surface",
                JSONObject().put(
                    "backgroundSource",
                    appearance.surface.backgroundSource.name,
                ).put(
                    "customBackgroundArgb",
                    appearance.surface.customBackgroundArgb,
                ).put(
                    "glassTransparencyPercent",
                    appearance.surface.glassTransparencyPercent,
                ).put(
                    "glassTintArgb",
                    appearance.surface.glassTintArgb,
                ).put(
                    "blurStrengthPercent",
                    appearance.surface.blurStrengthPercent,
                ).put(
                    "saturationPercent",
                    appearance.surface.saturationPercent,
                ).put(
                    "contrastPercent",
                    appearance.surface.contrastPercent,
                ).put(
                    "outlineWidthDp",
                    appearance.surface.outlineWidthDp,
                ).put(
                    "highlightPercent",
                    appearance.surface.highlightPercent,
                ).put(
                    "shadowElevationDp",
                    appearance.surface.shadowElevationDp,
                ).put("textureIntensityPercent", appearance.surface.textureIntensityPercent),
            )
            .put(
                "typography",
                JSONObject().put(
                    "accentSource",
                    appearance.typography.accentSource.name,
                ).put(
                    "customAccentArgb",
                    appearance.typography.customAccentArgb,
                ).put(
                    "automaticForegroundContrast",
                    appearance.typography.automaticForegroundContrast,
                ).put(
                    "contentDensity",
                    appearance.typography.contentDensity.name,
                ).put("textScalePercent", appearance.typography.textScalePercent),
            )
            .put(
                "motion",
                JSONObject().put(
                    "settleDurationMillis",
                    appearance.motion.settleDurationMillis,
                ).put(
                    "reflowDurationMillis",
                    appearance.motion.reflowDurationMillis,
                ).put(
                    "enterDurationMillis",
                    appearance.motion.enterDurationMillis,
                ).put(
                    "exitDurationMillis",
                    appearance.motion.exitDurationMillis,
                ).put(
                    "expandDurationMillis",
                    appearance.motion.expandDurationMillis,
                ).put(
                    "easing",
                    appearance.motion.easing.name,
                ).put(
                    "springBouncinessPercent",
                    appearance.motion.springBouncinessPercent,
                ).put(
                    "travelIntensityPercent",
                    appearance.motion.travelIntensityPercent,
                ).put(
                    "parallaxIntensityPercent",
                    appearance.motion.parallaxIntensityPercent,
                ).put(
                    "rotationIntensityPercent",
                    appearance.motion.rotationIntensityPercent,
                ).put(
                    "hapticStrength",
                    appearance.motion.hapticStrength.name,
                ).put("reducedMotion", appearance.motion.reducedMotion).put("reducedTransparency", appearance.motion.reducedTransparency),
            )
    }

private fun JSONObject.toTimeScapeAppearance(defaults: TimeScapeAppearanceSettings): TimeScapeAppearanceSettings {
    val geometry = optJSONObject("geometry")
    val surface = optJSONObject("surface")
    val typography = optJSONObject("typography")
    val motion = optJSONObject("motion")
    return TimeScapeAppearanceSettings(
        version = optInt("version", defaults.version),
        preset = enumOrDefault("preset", defaults.preset),
        geometry =
            TimeScapeGeometry(
                cardAspectRatioPercent =
                    geometry.optIntOrDefault(
                        "cardAspectRatioPercent",
                        defaults.geometry.cardAspectRatioPercent,
                    ),
                focusedScalePercent =
                    geometry.optIntOrDefault(
                        "focusedScalePercent",
                        defaults.geometry.focusedScalePercent,
                    ),
                focusedGapDp =
                    geometry.optIntOrDefault(
                        "focusedGapDp",
                        defaults.geometry.focusedGapDp,
                    ),
                visibleDepth =
                    geometry.optIntOrDefault(
                        "visibleDepth",
                        defaults.geometry.visibleDepth,
                    ),
                overlapPercent =
                    geometry.optIntOrDefault(
                        "overlapPercent",
                        defaults.geometry.overlapPercent,
                    ),
                verticalSpacingDp =
                    geometry.optIntOrDefault(
                        "verticalSpacingDp",
                        defaults.geometry.verticalSpacingDp,
                    ),
                horizontalOffsetDp =
                    geometry.optIntOrDefault(
                        "horizontalOffsetDp",
                        defaults.geometry.horizontalOffsetDp,
                    ),
                curveDp =
                    geometry.optIntOrDefault(
                        "curveDp",
                        defaults.geometry.curveDp,
                    ),
                fanDirection =
                    geometry.enumOrDefault(
                        "fanDirection",
                        defaults.geometry.fanDirection,
                    ),
                rotationDegrees =
                    geometry.optIntOrDefault(
                        "rotationDegrees",
                        defaults.geometry.rotationDegrees,
                    ),
                cornerRadiusDp =
                    geometry.optIntOrDefault(
                        "cornerRadiusDp",
                        defaults.geometry.cornerRadiusDp,
                    ),
                clipContent =
                    geometry.optBooleanOrDefault(
                        "clipContent",
                        defaults.geometry.clipContent,
                    ),
                contentPaddingDp =
                    geometry.optIntOrDefault(
                        "contentPaddingDp",
                        defaults.geometry.contentPaddingDp,
                    ),
            ),
        surface =
            TimeScapeSurface(
                backgroundSource =
                    surface.enumOrDefault(
                        "backgroundSource",
                        defaults.surface.backgroundSource,
                    ),
                customBackgroundArgb =
                    surface.optLongOrDefault(
                        "customBackgroundArgb",
                        defaults.surface.customBackgroundArgb,
                    ),
                glassTransparencyPercent =
                    surface.optIntOrDefault(
                        "glassTransparencyPercent",
                        defaults.surface.glassTransparencyPercent,
                    ),
                glassTintArgb =
                    surface.optLongOrDefault(
                        "glassTintArgb",
                        defaults.surface.glassTintArgb,
                    ),
                blurStrengthPercent =
                    surface.optIntOrDefault(
                        "blurStrengthPercent",
                        defaults.surface.blurStrengthPercent,
                    ),
                saturationPercent =
                    surface.optIntOrDefault(
                        "saturationPercent",
                        defaults.surface.saturationPercent,
                    ),
                contrastPercent =
                    surface.optIntOrDefault(
                        "contrastPercent",
                        defaults.surface.contrastPercent,
                    ),
                outlineWidthDp =
                    surface.optIntOrDefault(
                        "outlineWidthDp",
                        defaults.surface.outlineWidthDp,
                    ),
                highlightPercent =
                    surface.optIntOrDefault(
                        "highlightPercent",
                        defaults.surface.highlightPercent,
                    ),
                shadowElevationDp =
                    surface.optIntOrDefault(
                        "shadowElevationDp",
                        defaults.surface.shadowElevationDp,
                    ),
                textureIntensityPercent =
                    surface.optIntOrDefault(
                        "textureIntensityPercent",
                        defaults.surface.textureIntensityPercent,
                    ),
            ),
        typography =
            TimeScapeTypography(
                accentSource = typography.enumOrDefault("accentSource", defaults.typography.accentSource),
                customAccentArgb = typography.optLongOrDefault("customAccentArgb", defaults.typography.customAccentArgb),
                automaticForegroundContrast =
                    typography.optBooleanOrDefault(
                        "automaticForegroundContrast",
                        defaults.typography.automaticForegroundContrast,
                    ),
                contentDensity = typography.enumOrDefault("contentDensity", defaults.typography.contentDensity),
                textScalePercent = typography.optIntOrDefault("textScalePercent", defaults.typography.textScalePercent),
            ),
        motion =
            TimeScapeMotion(
                settleDurationMillis =
                    motion.optIntOrDefault(
                        "settleDurationMillis",
                        defaults.motion.settleDurationMillis,
                    ),
                reflowDurationMillis =
                    motion.optIntOrDefault(
                        "reflowDurationMillis",
                        defaults.motion.reflowDurationMillis,
                    ),
                enterDurationMillis =
                    motion.optIntOrDefault(
                        "enterDurationMillis",
                        defaults.motion.enterDurationMillis,
                    ),
                exitDurationMillis =
                    motion.optIntOrDefault(
                        "exitDurationMillis",
                        defaults.motion.exitDurationMillis,
                    ),
                expandDurationMillis =
                    motion.optIntOrDefault(
                        "expandDurationMillis",
                        defaults.motion.expandDurationMillis,
                    ),
                easing =
                    motion.enumOrDefault(
                        "easing",
                        defaults.motion.easing,
                    ),
                springBouncinessPercent =
                    motion.optIntOrDefault(
                        "springBouncinessPercent",
                        defaults.motion.springBouncinessPercent,
                    ),
                travelIntensityPercent =
                    motion.optIntOrDefault(
                        "travelIntensityPercent",
                        defaults.motion.travelIntensityPercent,
                    ),
                parallaxIntensityPercent =
                    motion.optIntOrDefault(
                        "parallaxIntensityPercent",
                        defaults.motion.parallaxIntensityPercent,
                    ),
                rotationIntensityPercent =
                    motion.optIntOrDefault(
                        "rotationIntensityPercent",
                        defaults.motion.rotationIntensityPercent,
                    ),
                hapticStrength =
                    motion.enumOrDefault(
                        "hapticStrength",
                        defaults.motion.hapticStrength,
                    ),
                reducedMotion =
                    motion.optBooleanOrDefault(
                        "reducedMotion",
                        defaults.motion.reducedMotion,
                    ),
                reducedTransparency =
                    motion.optBooleanOrDefault(
                        "reducedTransparency",
                        defaults.motion.reducedTransparency,
                    ),
            ),
    ).coerce()
}

private fun JSONObject?.optIntOrDefault(
    name: String,
    default: Int,
): Int = this?.optInt(name, default) ?: default

private fun JSONObject?.optLongOrDefault(
    name: String,
    default: Long,
): Long = this?.optLong(name, default) ?: default

private fun JSONObject?.optBooleanOrDefault(
    name: String,
    default: Boolean,
): Boolean = this?.optBoolean(name, default) ?: default

private inline fun <reified T : Enum<T>> JSONObject?.enumOrDefault(
    name: String,
    default: T,
): T =
    this?.optString(name)?.let { value ->
        enumValues<T>().firstOrNull {
            it.name == value
        }
    } ?: default

private fun JSONObject.toStagePreferencesEntry(): Pair<HomeLayoutKey, AppStagePreferences>? =
    runCatching { LauncherViewMode.valueOf(optString("viewMode")) }.getOrNull()?.let { viewMode ->
        runCatching { HomeLayoutDeviceClass.valueOf(optString("deviceClass")) }.getOrNull()?.let { deviceClass ->
            HomeLayoutKey(viewMode, deviceClass) to
                AppStagePreferences(
                    pinnedStageIds =
                        optJSONArray("pinnedStageIds")
                            ?.let { ids ->
                                (0 until ids.length())
                                    .mapNotNull { index -> ids.optJSONObject(index)?.toAppStageId() }
                                    .distinct()
                            }
                            .orEmpty(),
                    selectedStageId = optJSONObject("selectedStageId")?.toAppStageId(),
                )
        }
    }

private fun JSONObject.toAppStageId(): AppStageId? =
    optString("packageName").takeIf(String::isNotBlank)?.let { packageName ->
        optString("profileId").takeIf(String::isNotBlank)?.let { profileId ->
            AppStageId(AppPackageName(packageName), AppProfileId(profileId))
        }
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
        .put("themeCornerStyle", settings.themeCornerStyle.name)
        .put("themeTypography", settings.themeTypography.name)
        .put("themeColors", encodeThemeColors(settings.themeColors))
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
            themeCornerStyle =
                runCatching { LauncherThemeCornerStyle.valueOf(optString("themeCornerStyle")) }
                    .getOrDefault(defaults.themeCornerStyle),
            themeTypography =
                runCatching { LauncherThemeTypography.valueOf(optString("themeTypography")) }
                    .getOrDefault(defaults.themeTypography),
            themeColors = optJSONObject("themeColors")?.toThemeColors(defaults.themeColors) ?: defaults.themeColors,
            wallpaper = optJSONObject("wallpaper")?.toWallpaper(defaults.wallpaper) ?: defaults.wallpaper,
        ).withHomeSystemBars(homeSystemBars)
}

private fun encodeThemeColors(colors: LauncherThemeColors): JSONObject =
    JSONObject()
        .put("backgroundArgb", colors.backgroundArgb)
        .put("accentArgb", colors.accentArgb)
        .put("dockArgb", colors.dockArgb)
        .put("labelArgb", colors.labelArgb)
        .put("labelBackgroundArgb", colors.labelBackgroundArgb)

private fun JSONObject.toThemeColors(defaults: LauncherThemeColors): LauncherThemeColors =
    defaults.copy(
        backgroundArgb = optNullableInt("backgroundArgb", defaults.backgroundArgb),
        accentArgb = optNullableInt("accentArgb", defaults.accentArgb),
        dockArgb = optNullableInt("dockArgb", defaults.dockArgb),
        labelArgb = optNullableInt("labelArgb", defaults.labelArgb),
        labelBackgroundArgb = optNullableInt("labelBackgroundArgb", defaults.labelBackgroundArgb),
    )

private fun JSONObject.optNullableInt(
    name: String,
    default: Int?,
): Int? =
    if (!has(name)) {
        default
    } else if (isNull(name)) {
        null
    } else {
        optInt(name)
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

internal const val LAUNCHER_SETTINGS_JSON_VERSION = 5
