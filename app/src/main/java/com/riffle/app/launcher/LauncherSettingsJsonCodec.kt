@file:Suppress("TooManyFunctions", "LongMethod", "MaxLineLength")

package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.WallpaperScrollMode
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.library.LibraryView
import com.riffle.core.domain.launcher.notifications.NotificationHideRule
import com.riffle.core.domain.launcher.notifications.NotificationHideRuleId
import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedRefreshIntent
import com.riffle.core.domain.launcher.rss.FeedUrl
import com.riffle.core.domain.launcher.settings.AppDrawerSettings
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.CardActionsPlacement
import com.riffle.core.domain.launcher.settings.FeatureSettings
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import com.riffle.core.domain.launcher.settings.HapticSettings
import com.riffle.core.domain.launcher.settings.HintSettings
import com.riffle.core.domain.launcher.settings.HomeBehaviourSettings
import com.riffle.core.domain.launcher.settings.HomeMode
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherThemeAccent
import com.riffle.core.domain.launcher.settings.LauncherThemeColors
import com.riffle.core.domain.launcher.settings.LauncherThemeCornerStyle
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import com.riffle.core.domain.launcher.settings.LauncherThemeTypography
import com.riffle.core.domain.launcher.settings.LiquidGlassSettings
import com.riffle.core.domain.launcher.settings.NotificationHidingSettings
import com.riffle.core.domain.launcher.settings.OpenCardPlacement
import com.riffle.core.domain.launcher.settings.OverlayDockEdge
import com.riffle.core.domain.launcher.settings.OverlayDockExpandedOrientation
import com.riffle.core.domain.launcher.settings.OverlayDockSettings
import com.riffle.core.domain.launcher.settings.PrivacySettings
import com.riffle.core.domain.launcher.settings.RssSettings
import com.riffle.core.domain.launcher.settings.SearchResultPresentation
import com.riffle.core.domain.launcher.settings.SearchSettings
import com.riffle.core.domain.launcher.settings.StreamSettings
import com.riffle.core.domain.launcher.settings.coerceOverlayDockSettings
import com.riffle.core.domain.launcher.settings.coerced
import com.riffle.core.domain.launcher.settings.homeSystemBars
import com.riffle.core.domain.launcher.settings.withHomeSystemBars
import com.riffle.core.domain.launcher.settings.withReducedTransparency
import com.riffle.core.domain.launcher.workspace.ReturnBehavior
import org.json.JSONArray
import org.json.JSONObject

fun encodeLauncherSettings(settings: LauncherSettings): String =
    JSONObject()
        .put("version", LAUNCHER_SETTINGS_JSON_VERSION)
        .put("appDrawer", encodeAppDrawerSettings(settings.appDrawer))
        .put("appearance", encodeAppearance(settings.appearance))
        .put("features", encodeFeatures(settings.features))
        .put("gestures", encodeGestures(settings.gestures))
        .put("haptics", encodeHaptics(settings.haptics))
        .put("hints", encodeHints(settings.hints))
        .put("home", encodeHomeBehaviour(settings.home))
        .put("liquidGlass", encodeLiquidGlass(settings.liquidGlass))
        .put("motion", encodeMotionSettings(settings.motion))
        .put("notificationHiding", encodeNotificationHiding(settings.notificationHiding))
        .put("overlayDock", encodeOverlayDock(settings.overlayDock))
        .put("privacy", encodePrivacy(settings.privacy))
        .put("rss", encodeRssSettings(settings.rss))
        .put("search", encodeSearchSettings(settings.search))
        .put("stream", encodeStreamSettings(settings.stream))
        .toString()

/**
 * Decodes a stored settings blob. The blob's `version` is read, not assumed: a blob from an older build (missing or
 * lower version) goes through the legacy folds below, and one from a newer build decodes whatever this build knows,
 * keeps the default for anything it does not understand and never throws for a changed section (see [guarded]).
 * Only text that is not a JSON object at all throws; [decodeStoredLauncherSettings] turns that into null.
 */
fun decodeLauncherSettings(value: String): LauncherSettings =
    JSONObject(value).let { json ->
        decodeLauncherSections(json, version = json.optInt("version", 0)).let { decoded ->
            decoded.withReducedTransparency(json.decodeReducedTransparency(decoded))
        }
    }

/**
 * The version a stored blob declares: 0 when it declares none (saved before versions were read), null when it is not a
 * JSON object.
 */
fun storedLauncherSettingsVersion(value: String): Int? =
    runCatching { JSONObject(value).optInt("version", 0) }.getOrNull()

/** True when [storedBlob] was written by a newer build than this one, so overwriting it must keep a copy first. */
fun isNewerThanThisBuild(storedBlob: String?): Boolean =
    storedBlob?.let(::storedLauncherSettingsVersion)?.let { it > LAUNCHER_SETTINGS_JSON_VERSION } == true

private fun decodeLauncherSections(
    json: JSONObject,
    version: Int,
): LauncherSettings {
    val defaults = LauncherSettings()
    return defaults.copy(
        appDrawer = json.guarded("appDrawer", defaults.appDrawer, JSONObject::toAppDrawerSettings),
        appearance = json.guarded("appearance", defaults.appearance) { toAppearance(it, version) },
        features = json.guarded("features", defaults.features, JSONObject::toFeatures),
        gestures = json.guarded("gestures", defaults.gestures, JSONObject::toGestures),
        haptics = json.guarded("haptics", defaults.haptics, JSONObject::toHaptics),
        hints = json.guarded("hints", defaults.hints, JSONObject::toHints),
        home = json.guarded("home", defaults.home, JSONObject::toHomeBehaviour),
        liquidGlass = json.guarded("liquidGlass", defaults.liquidGlass, JSONObject::toLiquidGlass),
        motion = json.guarded("motion", defaults.motion, JSONObject::toMotionSettings),
        notificationHiding =
            json.guarded("notificationHiding", defaults.notificationHiding, JSONObject::toNotificationHiding),
        overlayDock = json.guarded("overlayDock", defaults.overlayDock, JSONObject::toOverlayDock),
        privacy = json.guarded("privacy", defaults.privacy, JSONObject::toPrivacy),
        rss = json.guarded("rss", defaults.rss, JSONObject::toRssSettings),
        search = json.guarded("search", defaults.search, JSONObject::toSearchSettings),
        stream = json.guarded("stream", defaults.stream, JSONObject::toStreamSettings),
    )
}

/** Decodes the object under [key] with [parse]; a missing, mistyped or unparseable section reads as [default]. */
private fun <T> JSONObject.guarded(
    key: String,
    default: T,
    parse: JSONObject.(T) -> T,
): T = runCatching { optJSONObject(key)?.parse(default) }.getOrNull() ?: default

/**
 * Reduced transparency lives in `appearance`. Settings saved before that carry it only in the removed adaptive stage's
 * motion block (`cards`, either layout), so a missing top-level key reads that old location from the raw JSON.
 */
private fun JSONObject.decodeReducedTransparency(decoded: LauncherSettings): Boolean =
    optJSONObject("appearance")?.takeIf { it.has("reducedTransparency") }?.optBoolean("reducedTransparency")
        ?: optJSONObject("cards")?.let { cards ->
            listOf("timeScapeAppearance", "adaptiveStageUnfoldedAppearance").any { key ->
                cards.optJSONObject(key)?.optJSONObject("motion")?.optBoolean("reducedTransparency", false) == true
            }
        }
        ?: decoded.appearance.reducedTransparency

private fun encodeAppDrawerSettings(settings: AppDrawerSettings): JSONObject =
    JSONObject()
        .put("libraryViews", encodeLibraryViews(settings.libraryViews))
        .put("iconGridColumns", settings.iconGridColumns)

private fun JSONObject.toAppDrawerSettings(defaults: AppDrawerSettings): AppDrawerSettings =
    AppDrawerSettings(
        libraryViews = decodeLibraryViews(defaults.libraryViews),
        iconGridColumns = optInt("iconGridColumns", defaults.iconGridColumns),
    ).coerced()

private fun encodeLibraryViews(views: Map<HomeLayoutDeviceClass, LibraryView>): JSONObject =
    JSONObject().also { json -> views.forEach { (deviceClass, view) -> json.put(deviceClass.name, view.storedId) } }

/**
 * The Library's view per device class. Settings saved before the Library carry the app drawer's `presentation`
 * instead: LIST becomes the A-Z list, ICONS the grid and anything else the default, for every device class. An unknown
 * device class or view in the new object is skipped (the default applies), so nothing here ever throws.
 */
private fun JSONObject.decodeLibraryViews(
    defaults: Map<HomeLayoutDeviceClass, LibraryView>,
): Map<HomeLayoutDeviceClass, LibraryView> {
    val stored = optJSONObject("libraryViews")
    return when {
        stored != null ->
            stored.keys().asSequence().mapNotNull { name ->
                val deviceClass = HomeLayoutDeviceClass.entries.firstOrNull { it.name == name }
                val view = LibraryView.fromStoredId(stored.optString(name))
                if (deviceClass == null || view == null) null else deviceClass to view
            }.toMap()

        has("presentation") -> {
            val legacy = LibraryView.decode(optString("presentation"))
            HomeLayoutDeviceClass.entries.associateWith { legacy }
        }

        else -> defaults
    }
}

private fun encodeHomeBehaviour(settings: HomeBehaviourSettings): JSONObject =
    JSONObject()
        .put("returnBehavior", settings.returnBehavior.name)
        .put("mode", settings.mode.storedId)

private fun encodeFeatures(settings: FeatureSettings): JSONObject =
    JSONObject()
        .put("stream", settings.stream)
        .put("dockLiveApps", settings.dockLiveApps)
        .put("library", settings.library)
        .put("simpleMode", settings.simpleMode)

// Absent (settings saved before the switches) or not a boolean: the default, today's behaviour.
private fun JSONObject.toFeatures(defaults: FeatureSettings): FeatureSettings =
    FeatureSettings(
        stream = (opt("stream") as? Boolean) ?: defaults.stream,
        dockLiveApps = (opt("dockLiveApps") as? Boolean) ?: defaults.dockLiveApps,
        library = (opt("library") as? Boolean) ?: defaults.library,
        simpleMode = (opt("simpleMode") as? Boolean) ?: defaults.simpleMode,
    )

private fun JSONObject.toHomeBehaviour(defaults: HomeBehaviourSettings): HomeBehaviourSettings =
    HomeBehaviourSettings(
        returnBehavior =
            optString("returnBehavior")
                .takeIf(String::isNotEmpty)
                ?.let { name -> ReturnBehavior.entries.firstOrNull { it.name == name } }
                ?: defaults.returnBehavior,
        // Absent (settings saved before Home mode) or a name this build does not know: Placed items.
        mode = HomeMode.fromStoredId(optString("mode").takeIf(String::isNotEmpty)) ?: defaults.mode,
    )

private fun encodePrivacy(settings: PrivacySettings): JSONObject =
    JSONObject().put("protectScreenContent", settings.protectScreenContent)

// Absent (settings saved before S11) or not a boolean: off, the default.
private fun JSONObject.toPrivacy(defaults: PrivacySettings): PrivacySettings =
    PrivacySettings(
        protectScreenContent = (opt("protectScreenContent") as? Boolean) ?: defaults.protectScreenContent,
    )

private fun encodeSearchSettings(settings: SearchSettings): JSONObject =
    JSONObject().put("resultPresentation", settings.resultPresentation.name)

private fun JSONObject.toSearchSettings(defaults: SearchSettings): SearchSettings =
    SearchSettings(
        resultPresentation =
            optString("resultPresentation")
                .takeIf(String::isNotEmpty)
                ?.let { name -> SearchResultPresentation.entries.firstOrNull { it.name == name } }
                ?: defaults.resultPresentation,
    )

private inline fun <reified T : Enum<T>> JSONObject?.enumOrDefault(
    name: String,
    default: T,
): T = enumOrNull<T>(name) ?: default

private inline fun <reified T : Enum<T>> JSONObject?.enumOrNull(name: String): T? =
    this?.optString(name)?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

private fun encodeAppearance(settings: AppearanceSettings): JSONObject =
    JSONObject()
        .put("hideStatusBarOnHome", settings.homeSystemBars.hideStatusBarOnHome)
        .put("hideNavigationBarOnHome", settings.homeSystemBars.hideNavigationBarOnHome)
        .put("reducedTransparency", settings.reducedTransparency)
        .put("themeMode", settings.themeMode.name)
        .put("themeAccent", settings.themeAccent.name)
        .put("themeCornerStyle", settings.themeCornerStyle.name)
        .put("themeTypography", settings.themeTypography.name)
        .put("themeColors", encodeThemeColors(settings.themeColors))
        .put("wallpaper", encodeWallpaper(settings.wallpaper))

private fun JSONObject.toAppearance(
    defaults: AppearanceSettings,
    version: Int,
): AppearanceSettings {
    // Before version 11 an all-in-one "fullscreenHome" flag meant both bars hidden; it is folded into the two.
    val legacyFullscreen = version < LAUNCHER_SETTINGS_JSON_VERSION && optBoolean("fullscreenHome", false)
    val homeSystemBars =
        defaults.homeSystemBars.copy(
            hideStatusBarOnHome = legacyFullscreen || optBoolean("hideStatusBarOnHome", false),
            hideNavigationBarOnHome = legacyFullscreen || optBoolean("hideNavigationBarOnHome", false),
        )
    return defaults
        .copy(
            reducedTransparency = optBoolean("reducedTransparency", defaults.reducedTransparency),
            themeMode =
                runCatching { LauncherThemeMode.valueOf(optString("themeMode")) }
                    .getOrDefault(defaults.themeMode),
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

// Only the accent is stored. Older data also carried backgroundArgb, dockArgb, labelArgb and
// labelBackgroundArgb, and a `themePreset`; those are ignored here, so removed values decode to glass.
private fun encodeThemeColors(colors: LauncherThemeColors): JSONObject =
    JSONObject().put("accentArgb", colors.accentArgb)

private fun JSONObject.toThemeColors(defaults: LauncherThemeColors): LauncherThemeColors =
    defaults.copy(accentArgb = optNullableInt("accentArgb", defaults.accentArgb))

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

private fun encodeLiquidGlass(settings: LiquidGlassSettings): JSONObject =
    JSONObject()
        .put("spotlightFlowMillis", settings.spotlightFlowMillis)

// Blobs written while glass blurred and refracted carry frostStrengthPercent and
// refractionStrengthPercent; those keys are simply not read any more.
private fun JSONObject.toLiquidGlass(defaults: LiquidGlassSettings): LiquidGlassSettings =
    LiquidGlassSettings(
        spotlightFlowMillis = optInt("spotlightFlowMillis", defaults.spotlightFlowMillis),
    ).coerced()

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

private fun encodeNotificationHiding(settings: NotificationHidingSettings): JSONObject =
    JSONObject().put("rules", JSONArray(settings.rules.map(::encodeNotificationHideRule)))

private fun encodeNotificationHideRule(rule: NotificationHideRule): JSONObject =
    JSONObject()
        .put("id", rule.id.value)
        .put("packageName", rule.packageName.value)
        .put("profileId", rule.profileId.value)
        .put("kind", rule.kind.name)
        .put("value", rule.value)
        .put("matchMode", rule.matchMode.name)

private fun JSONObject.toNotificationHiding(defaults: NotificationHidingSettings): NotificationHidingSettings =
    NotificationHidingSettings(
        rules =
            optJSONArray("rules")
                ?.let { entries ->
                    (0 until entries.length())
                        .mapNotNull { index -> entries.optJSONObject(index)?.toNotificationHideRule() }
                }
                ?: defaults.rules,
    )

/**
 * Drops the entry (rather than throwing or substituting a default) when the id or kind/matchMode
 * enum is missing or unrecognized.
 */
@Suppress("ReturnCount")
private fun JSONObject.toNotificationHideRule(): NotificationHideRule? {
    val id = optString("id").takeIf(String::isNotBlank)?.let(::NotificationHideRuleId) ?: return null
    val packageName = optString("packageName").takeIf(String::isNotBlank)?.let(::AppPackageName) ?: return null
    val profileId = optString("profileId").takeIf(String::isNotBlank)?.let(::AppProfileId) ?: return null
    val kind = enumOrNull<NotificationHideRule.Kind>("kind") ?: return null
    return NotificationHideRule(
        id = id,
        packageName = packageName,
        profileId = profileId,
        kind = kind,
        value = optString("value"),
        matchMode = enumOrDefault("matchMode", NotificationHideRule.MatchMode.EXACT),
    )
}

private fun encodeRssSettings(settings: RssSettings): JSONObject =
    JSONObject()
        .put("feeds", JSONArray(settings.feeds.map(::encodeFeedConfiguration)))
        .put("refreshInterval", settings.refreshInterval.name)
        .put("backgroundWifiOnly", settings.backgroundWifiOnly)
        .put("backgroundChargingOnly", settings.backgroundChargingOnly)

private fun encodeFeedConfiguration(feed: FeedConfiguration): JSONObject =
    JSONObject()
        .put("id", feed.id.value)
        .put("url", feed.url.value)
        .put("profileId", feed.profile.id.value)
        .put("profileType", feed.profile.type.name)
        .put("enabled", feed.enabled)
        .put("refreshIntent", feed.refreshIntent.name)

private fun JSONObject.toRssSettings(defaults: RssSettings): RssSettings =
    RssSettings(
        feeds =
            optJSONArray("feeds")
                ?.let { entries ->
                    (0 until entries.length())
                        .mapNotNull { index -> entries.optJSONObject(index)?.toFeedConfiguration() }
                }
                ?: defaults.feeds,
        // Names written before #1393 (MINUTES_*) no longer exist and fall back to Off: nobody is opted in.
        refreshInterval = enumOrDefault("refreshInterval", defaults.refreshInterval),
        backgroundWifiOnly = optBoolean("backgroundWifiOnly", defaults.backgroundWifiOnly),
        backgroundChargingOnly = optBoolean("backgroundChargingOnly", defaults.backgroundChargingOnly),
    )

/**
 * Drops the entry (rather than throwing or substituting a default) when the URL fails
 * [FeedUrl.parse] -- e.g. non-HTTPS scheme, embedded userinfo, or malformed host -- or when the
 * profile/enum fields are missing or unrecognized. This is the single point where both
 * DataStore-loaded settings and backup-restored settings validate each feed URL and drop invalid
 * or unsupported entries, per the ADR's restore contract.
 */
private fun JSONObject.toFeedConfiguration(): FeedConfiguration? =
    optString("id").takeIf(String::isNotBlank)?.let(::FeedId)?.let { id ->
        FeedUrl.parse(optString("url")).getOrNull()?.let { url ->
            toFeedProfile()?.let { profile -> feedConfiguration(id, url, profile) }
        }
    }

private fun JSONObject.feedConfiguration(
    id: FeedId,
    url: FeedUrl,
    profile: AppProfile,
): FeedConfiguration =
    FeedConfiguration(
        id = id,
        url = url,
        profile = profile,
        enabled = optBoolean("enabled", true),
        refreshIntent =
            runCatching { FeedRefreshIntent.valueOf(optString("refreshIntent")) }
                .getOrDefault(FeedRefreshIntent.MANUAL),
    )

private fun JSONObject.toFeedProfile(): AppProfile? =
    optString("profileId").takeIf(String::isNotBlank)?.let(::AppProfileId)?.let { profileId ->
        runCatching { AppProfileType.valueOf(optString("profileType")) }.getOrNull()?.let { profileType ->
            AppProfile(id = profileId, type = profileType)
        }
    }

/** 11: P9 removed the stage cards settings, contextual behaviour, the after-leaving-Library target and fullscreenHome. */
internal const val LAUNCHER_SETTINGS_JSON_VERSION = 11

private fun encodeHints(settings: HintSettings): JSONObject = JSONObject().put("dockPressSeen", settings.dockPressSeen)

private fun JSONObject.toHints(defaults: HintSettings): HintSettings =
    HintSettings(dockPressSeen = optBoolean("dockPressSeen", defaults.dockPressSeen))

private fun encodeStreamSettings(settings: StreamSettings): JSONObject =
    JSONObject()
        .put("cardTintPercent", settings.cardTintPercent)
        .put("cardTopGlow", settings.cardTopGlow)
        .put("openCardPlacement", settings.openCardPlacement.name)
        .put("cardActionsPlacement", settings.cardActionsPlacement.name)

// A stored name this build does not know (a newer build's placement) reads as the default; the tint is held to its range.
private fun JSONObject.toStreamSettings(defaults: StreamSettings): StreamSettings =
    StreamSettings(
        cardTintPercent = optInt("cardTintPercent", defaults.cardTintPercent),
        cardTopGlow = optBoolean("cardTopGlow", defaults.cardTopGlow),
        openCardPlacement =
            runCatching { OpenCardPlacement.valueOf(optString("openCardPlacement")) }
                .getOrDefault(defaults.openCardPlacement),
        cardActionsPlacement =
            runCatching { CardActionsPlacement.valueOf(optString("cardActionsPlacement")) }
                .getOrDefault(defaults.cardActionsPlacement),
    ).coerced()
