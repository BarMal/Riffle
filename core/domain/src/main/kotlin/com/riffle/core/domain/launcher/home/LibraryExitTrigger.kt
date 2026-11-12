package com.riffle.core.domain.launcher.home

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

/** [deviceClass]'s Home mode: the Home side of its Home <-> Library pair (#1241). */
fun HomeLayoutSet.homeModeFor(deviceClass: HomeLayoutDeviceClass): LauncherViewMode = modePairFor(deviceClass).home
