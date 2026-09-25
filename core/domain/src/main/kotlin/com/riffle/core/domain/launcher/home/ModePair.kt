package com.riffle.core.domain.launcher.home

/**
 * The two modes one device class moves between with the dock pull: its Home mode and Library
 * (#1241, docs/product/modes-dock-handle-and-cards-plan.md Decision 1).
 *
 * The pair is fixed: Library is always one side, as the app drawer, and [home] is the other --
 * Cards, or Standard until Cards covers widgets and standard app pages. There is nothing to enable
 * or order; the only choice is which mode Home is. This replaces the user-configured mode ring of
 * #1225, which stored data still migrates from (see [fromLegacyRing], [migrated]).
 *
 * Which side is showing is not stored here. It is the layout set's active mode, and every device
 * class's current mode is one of its pair's two (see [HomeLayoutSet.modePairFor]).
 */
data class ModePair(
    val home: LauncherViewMode = DEFAULT_HOME_MODE,
) {
    init {
        require(home != LIBRARY_MODE) { "Library is always the other side of a mode pair, never its Home" }
    }

    val library: LauncherViewMode
        get() = LIBRARY_MODE

    operator fun contains(mode: LauncherViewMode): Boolean = mode == home || mode == library

    /** The mode [surface] shows in this pair. */
    fun modeFor(surface: ModeSurface): LauncherViewMode =
        when (surface) {
            ModeSurface.HOME -> home
            ModeSurface.LIBRARY -> library
        }

    /**
     * The mode on the other side of the pair from [mode]: [home] for Library, Library for anything
     * else. This is where a dock pull (or its accessibility equivalents) leads from [mode].
     */
    fun counterpart(mode: LauncherViewMode): LauncherViewMode = modeFor(mode.modeSurface.other)

    companion object {
        /** The mode that is always the Library side of a pair: the app drawer. */
        val LIBRARY_MODE: LauncherViewMode = LauncherViewMode.HOME_SCREEN_LIBRARY

        /** Home's mode when nothing has chosen one. */
        val DEFAULT_HOME_MODE: LauncherViewMode = LauncherViewMode.CARD_INTERFACE

        /** Cards and Library: the pair a device class starts with. */
        val DEFAULT: ModePair = ModePair()

        /** The modes a pair's Home can be, in the order Settings offers them. */
        val HOME_MODES: List<LauncherViewMode> =
            listOf(LauncherViewMode.CARD_INTERFACE, LauncherViewMode.STANDARD_APP_DRAWER)

        /** A pair with [home] as Home, or null when [home] is Library or absent. */
        fun of(home: LauncherViewMode?): ModePair? = home?.takeIf { mode -> mode != LIBRARY_MODE }?.let(::ModePair)

        /**
         * The pair of a device class that shows [currentMode] and has recorded no Home:
         * [currentMode] when it is a Home mode, otherwise [DEFAULT].
         */
        fun fallbackFor(currentMode: LauncherViewMode?): ModePair = of(currentMode) ?: DEFAULT

        /**
         * The pair for a device class stored with a mode ring (#1225; [ringModes] in ring order).
         * The mode it shows now when that is a Home mode, so nothing on screen changes; otherwise
         * the first non-Library mode of the ring; otherwise [DEFAULT].
         */
        fun fromLegacyRing(
            ringModes: List<LauncherViewMode>,
            currentMode: LauncherViewMode?,
        ): ModePair = of(currentMode) ?: ringModes.firstNotNullOfOrNull(::of) ?: DEFAULT

        /**
         * The pair for a device class stored before mode rings, from the two modes it remembered:
         * [preferredMode] (what it last showed) and [lastNonCardsMode] (where leaving Cards
         * returned to). The first of those that is a Home mode, otherwise [DEFAULT].
         */
        fun migrated(
            preferredMode: LauncherViewMode?,
            lastNonCardsMode: LauncherViewMode?,
        ): ModePair = of(preferredMode) ?: of(lastNonCardsMode) ?: DEFAULT
    }
}
