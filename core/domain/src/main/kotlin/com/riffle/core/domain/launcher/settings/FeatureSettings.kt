package com.riffle.core.domain.launcher.settings

/**
 * What Home shows. Retired as a choice (ADR 0003, Amendment 8: one Home); only Simple mode still draws [AppLibrary],
 * and a stored [AppLibrary] is read once to build a placed Home from the apps. [PlacedItems] is Home as it always
 * was: the apps, folders and widgets the user placed. [AppLibrary] shows the app library pages (the paged Library
 * View) as Home.
 *
 * [storedId] is a stored contract: never rename one, only add new ids.
 */
enum class HomeMode(
    val storedId: String,
) {
    PlacedItems("placed_items"),
    AppLibrary("app_library"),
    ;

    companion object {
        val DEFAULT: HomeMode = PlacedItems

        fun fromStoredId(id: String?): HomeMode? = entries.firstOrNull { it.storedId == id }

        /** The mode for a stored value that may be anything (the default). Never throws. */
        fun decode(stored: String?): HomeMode = fromStoredId(stored) ?: DEFAULT
    }
}

/**
 * The feature switches (ADR 0003, Amendment 1): the Stream, the Dock's live apps and the Library each on or off, and
 * Simple mode, the locked-down Home: every app A to Z on the paged Library, not arrangeable, with the Stream, the live
 * apps and the Library pull off. The first three default to today's behaviour (on); Simple mode, the Stream handle and
 * the "Show as" button default off (ADR 0003, Amendment 21: nothing permanent is drawn unasked).
 *
 * Simple mode is not a copy of other values: while it is on it overrides the switches ([effective], which reads the
 * Home mode as [HomeMode.AppLibrary]), and the stored switches and the Home mode come back untouched when it is turned
 * off.
 */
data class FeatureSettings(
    val stream: Boolean = true,
    val dockLiveApps: Boolean = true,
    val library: Boolean = true,
    val simpleMode: Boolean = false,
    /**
     * Opt-in: the small handle that stays on the Dock to open the Stream with a tap (ADR 0003, Amendments 12 and 21).
     * Needs the Stream. Off, it still appears once, with the press hint.
     */
    val dockStreamHandle: Boolean = false,
    /** Opt-in: the small "Show as" button on the Stream's page (the menu a long-press of a tile opens anyway). */
    val streamViewButton: Boolean = false,
) {
    /** What is actually on, given the stored Home mode. */
    fun effective(homeMode: HomeMode): EffectiveFeatures =
        if (simpleMode) {
            EffectiveFeatures(
                stream = false,
                dockLiveApps = false,
                library = false,
                homeMode = HomeMode.AppLibrary,
                simpleMode = true,
                dockStreamHandle = false,
                streamViewButton = false,
            )
        } else {
            EffectiveFeatures(
                stream = stream,
                dockLiveApps = dockLiveApps && stream,
                library = library,
                homeMode = homeMode,
                simpleMode = false,
                dockStreamHandle = dockStreamHandle && stream,
                streamViewButton = streamViewButton && stream,
            )
        }
}

/** The switches Settings offers one by one. */
enum class LauncherFeature {
    STREAM,
    DOCK_LIVE_APPS,
    LIBRARY,
    DOCK_STREAM_HANDLE,
    STREAM_VIEW_BUTTON,
}

/** These switches with [feature] set to [enabled]. */
fun FeatureSettings.with(
    feature: LauncherFeature,
    enabled: Boolean,
): FeatureSettings =
    when (feature) {
        LauncherFeature.STREAM -> copy(stream = enabled)
        LauncherFeature.DOCK_LIVE_APPS -> copy(dockLiveApps = enabled)
        LauncherFeature.LIBRARY -> copy(library = enabled)
        LauncherFeature.DOCK_STREAM_HANDLE -> copy(dockStreamHandle = enabled)
        LauncherFeature.STREAM_VIEW_BUTTON -> copy(streamViewButton = enabled)
    }

/** The switches as the surfaces read them: Simple mode already applied. */
data class EffectiveFeatures(
    /** The Stream exists: the Dock's press, its contextual mode, the Stream pages and every route to them. */
    val stream: Boolean = true,
    /** The Dock's dynamic side (live apps and now playing) draws. */
    val dockLiveApps: Boolean = true,
    /** The Library sheet exists: the pull away from the Dock, "Open Library" and the gesture that opens it. */
    val library: Boolean = true,
    val homeMode: HomeMode = HomeMode.PlacedItems,
    val simpleMode: Boolean = false,
    /** The Stream handle stays on the Dock. */
    val dockStreamHandle: Boolean = false,
    /** The "Show as" button draws on the Stream's page. */
    val streamViewButton: Boolean = false,
)

/** The switches in effect for these settings (Simple mode applied). */
fun LauncherSettings.effectiveFeatures(): EffectiveFeatures = features.effective(home.mode)

/**
 * The Home gestures with the ones that lead to a switched-off feature unbound, so no gesture is left that does nothing
 * (a stored binding is kept; it is only not offered while the feature is off).
 */
fun HomeGestureSettings.without(features: EffectiveFeatures): HomeGestureSettings {
    return unbinding { action ->
        (action == LauncherGestureAction.OPEN_STREAM && !features.stream) ||
            (action == LauncherGestureAction.OPEN_LIBRARY && !features.library)
    }
}

/**
 * The Home gestures as they stand in the App library Home mode: the ones that act on the placed pages (the swipes
 * between Home pages, editing a page, the page overview) do nothing there, because the pager is the app library's own.
 */
fun HomeGestureSettings.forAppLibraryHome(): HomeGestureSettings {
    val placedOnly =
        setOf(
            LauncherGestureAction.SELECT_NEXT_HOME_PAGE,
            LauncherGestureAction.SELECT_PREVIOUS_HOME_PAGE,
            LauncherGestureAction.ENTER_HOME_EDIT_MODE,
            LauncherGestureAction.ENTER_HOME_PAGE_OVERVIEW,
        )
    return unbinding { action -> action in placedOnly }
}

/** These gestures with every one whose action matches [dead] bound to nothing (defaults for unstored ones count). */
private fun HomeGestureSettings.unbinding(dead: (LauncherGestureAction) -> Boolean): HomeGestureSettings {
    val gestures = HomeGesture.entries.filter { gesture -> dead(actionFor(gesture)) }
    return if (gestures.isEmpty()) {
        this
    } else {
        copy(
            actions = actions + gestures.associateWith { LauncherGestureAction.NONE },
        )
    }
}
