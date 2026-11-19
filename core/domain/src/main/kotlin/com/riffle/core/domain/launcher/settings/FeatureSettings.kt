package com.riffle.core.domain.launcher.settings

/**
 * What Home shows (ADR 0003, Amendment 1, "Home mode"). [PlacedItems] is Home as it always was: the apps, folders and
 * widgets the user placed. [AppLibrary] shows the app library pages (the paged Library View) as Home. The placed layout
 * is kept either way, so it is back when the mode is.
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
 * Simple mode, the preset that leaves only the app library (as Home) and the Dock. Each switch defaults to today's
 * behaviour (on); Simple mode defaults off.
 *
 * Simple mode is not a copy of other values: while it is on it overrides the switches ([effective]), and the stored
 * switches and the Home mode come back untouched when it is turned off.
 */
data class FeatureSettings(
    val stream: Boolean = true,
    val dockLiveApps: Boolean = true,
    val library: Boolean = true,
    val simpleMode: Boolean = false,
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
            )
        } else {
            EffectiveFeatures(
                stream = stream,
                dockLiveApps = dockLiveApps && stream,
                library = library,
                homeMode = homeMode,
                simpleMode = false,
            )
        }
}

/** The three switches Settings offers one by one. */
enum class LauncherFeature {
    STREAM,
    DOCK_LIVE_APPS,
    LIBRARY,
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
