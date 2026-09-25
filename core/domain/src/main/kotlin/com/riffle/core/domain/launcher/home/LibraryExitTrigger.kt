package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.settings.LibraryReturnTarget

/**
 * The moments the launcher may leave Library, the app drawer (Decision 10, #1243).
 *
 * The dock pull is not one of them: it is the user choosing a surface, not leaving one.
 */
enum class LibraryExitTrigger {
    /** An app or app shortcut was launched while Library was showing. */
    APP_LAUNCH,

    /** Home was pressed (the launcher was re-entered through a home intent). */
    HOME_PRESS,

    /** System Back, including predictive back, while Library was showing. */
    BACK,

    /** The launcher activity started from nothing, with Library as the stored mode. */
    COLD_START,
}

/**
 * Whether this trigger takes the launcher from [current] to Home under [returnTarget].
 *
 * Only Library is ever left: on Home every trigger leaves the surface alone. From Library, every
 * trigger returns Home when the user chose [LibraryReturnTarget.HOME] (the default) and none does
 * when they chose [LibraryReturnTarget.LIBRARY] -- so Library is never the cold-start surface unless
 * that setting says so.
 */
fun LibraryExitTrigger.returnsHome(
    current: ModeSurface,
    returnTarget: LibraryReturnTarget,
): Boolean = current == ModeSurface.LIBRARY && returnTarget == LibraryReturnTarget.HOME

/** The mode Home falls back to when nothing names one. */
val DEFAULT_HOME_MODE: LauncherViewMode = LauncherViewMode.CARD_INTERFACE

/**
 * Which of [modes] is Home: the first one shown as [ModeSurface.HOME], else [DEFAULT_HOME_MODE].
 *
 * Kept deliberately small: it is the one place "the user's Home mode" is resolved, so it can follow
 * the Home <-> Library pair (#1241) without touching its callers.
 */
fun homeModeAmong(modes: List<LauncherViewMode>): LauncherViewMode =
    modes.firstOrNull { mode -> mode.modeSurface == ModeSurface.HOME } ?: DEFAULT_HOME_MODE

/** [deviceClass]'s Home mode, resolved from the modes it moves between (see [homeModeAmong]). */
fun HomeLayoutSet.homeModeFor(deviceClass: HomeLayoutDeviceClass): LauncherViewMode =
    homeModeAmong(modeRingFor(deviceClass).modes)

/**
 * The mode the active device class switches to when [trigger] happens under [returnTarget], or null
 * when it stays where it is.
 */
fun HomeLayoutSet.modeAfterLeavingLibrary(
    trigger: LibraryExitTrigger,
    returnTarget: LibraryReturnTarget,
): LauncherViewMode? =
    homeModeFor(activeKey.deviceClass)
        .takeIf { trigger.returnsHome(current = activeKey.viewMode.modeSurface, returnTarget = returnTarget) }
