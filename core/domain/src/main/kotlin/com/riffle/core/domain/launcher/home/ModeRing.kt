package com.riffle.core.domain.launcher.home

/**
 * The modes one device class moves between, in the order the user chose (#1225).
 *
 * A ring holds two or three distinct [LauncherViewMode]s. "Next" and "previous" walk it and wrap,
 * so with two modes each is the other's next and previous. Leaving Cards is not a special case: it
 * is "previous mode in the ring", like any other mode change.
 *
 * Which mode is active is not stored here. It is the layout set's active mode (held in memory, see
 * #1198); [indexOf] turns it into a position in the ring. [HomeLayoutSet] keeps the active mode in
 * the ring: selecting a mode outside it adds it, and removing the active mode moves to a neighbour.
 */
data class ModeRing(
    val modes: List<LauncherViewMode>,
) {
    init {
        require(modes.size in MIN_MODES..MAX_MODES) { "A mode ring holds $MIN_MODES to $MAX_MODES modes: $modes" }
        require(modes.distinct().size == modes.size) { "A mode ring's modes are distinct: $modes" }
    }

    operator fun contains(mode: LauncherViewMode): Boolean = mode in modes

    /** [mode]'s position in the ring, or -1 when it is not in it. */
    fun indexOf(mode: LauncherViewMode): Int = modes.indexOf(mode)

    /** The mode after [from], wrapping; the first mode when [from] is not in the ring. */
    fun next(from: LauncherViewMode): LauncherViewMode =
        indexOf(from)
            .takeIf { index -> index >= 0 }
            ?.let { index -> modes[(index + 1) % modes.size] }
            ?: modes.first()

    /** The mode before [from], wrapping; the last mode when [from] is not in the ring. */
    fun previous(from: LauncherViewMode): LauncherViewMode =
        indexOf(from)
            .takeIf { index -> index >= 0 }
            ?.let { index -> modes[(index - 1 + modes.size) % modes.size] }
            ?: modes.last()

    /**
     * Enable or disable [mode]. An enabled mode joins the end of the ring. A change that would leave
     * fewer than [MIN_MODES] (or more than [MAX_MODES]) modes is refused and the ring is unchanged.
     */
    fun withModeEnabled(
        mode: LauncherViewMode,
        enabled: Boolean,
    ): ModeRing =
        when {
            enabled == (mode in this) -> this
            enabled -> of(modes + mode) ?: this
            else -> of(modes - mode) ?: this
        }

    /** Move [mode] [offset] places (negative is earlier), clamped to the ring's ends. */
    fun withModeMoved(
        mode: LauncherViewMode,
        offset: Int,
    ): ModeRing {
        val from = indexOf(mode)
        if (from < 0) return this
        val to = (from + offset).coerceIn(0, modes.lastIndex)
        return if (from == to) this else copy(modes = modes.toMutableList().apply { add(to, removeAt(from)) })
    }

    /**
     * This ring with [mode] in it. A missing mode is inserted straight after [after] when that is in
     * the ring, otherwise at the end. A full ring is returned unchanged -- with three modes in all,
     * a full ring already holds every mode.
     */
    fun including(
        mode: LauncherViewMode,
        after: LauncherViewMode? = null,
    ): ModeRing {
        if (mode in this || modes.size >= MAX_MODES) return this
        val insertAt = after?.let(::indexOf)?.takeIf { index -> index >= 0 }?.plus(1) ?: modes.size
        return copy(modes = modes.toMutableList().apply { add(insertAt, mode) })
    }

    /**
     * Where to go when [removed] leaves this ring and [updated] is what is left: the mode that slides
     * into its place (the one after it), or, when it was last, the one before it.
     */
    fun neighbourAfterRemoving(
        removed: LauncherViewMode,
        updated: ModeRing,
    ): LauncherViewMode {
        val index = indexOf(removed)
        if (index < 0) return updated.modes.first()
        return (modes.drop(index + 1) + modes.take(index).reversed())
            .firstOrNull { mode -> mode in updated }
            ?: updated.modes.first()
    }

    companion object {
        const val MIN_MODES = 2
        const val MAX_MODES = 3

        /** Library then Cards: the ring a device class starts with. */
        val DEFAULT: ModeRing = ModeRing(listOf(LauncherViewMode.HOME_SCREEN_LIBRARY, LauncherViewMode.CARD_INTERFACE))

        /** A ring of [modes], or null when they are not 2-3 distinct modes. */
        fun of(modes: List<LauncherViewMode>): ModeRing? =
            modes.takeIf { candidate ->
                candidate.size in MIN_MODES..MAX_MODES && candidate.distinct().size == candidate.size
            }?.let(::ModeRing)

        /**
         * The ring for a device class nothing has configured yet, which must hold [activeMode]: the
         * [DEFAULT] ring, with [activeMode] put in front of it when it is not already there (so a
         * device on Standard gets Standard, Library, Cards).
         */
        fun fallbackFor(activeMode: LauncherViewMode?): ModeRing =
            when {
                activeMode == null || activeMode in DEFAULT -> DEFAULT
                else -> ModeRing(listOf(activeMode) + DEFAULT.modes)
            }

        /**
         * The ring for a device class stored before rings existed, built from the two modes it did
         * remember: [preferredMode] (what it last showed, Cards included) and [lastNonCardsMode]
         * (where leaving Cards returned to). Deduplicated, non-Cards first; when that leaves fewer
         * than two modes it falls back to [fallbackFor] the preferred mode.
         */
        fun migrated(
            preferredMode: LauncherViewMode?,
            lastNonCardsMode: LauncherViewMode?,
        ): ModeRing =
            listOfNotNull(preferredMode, lastNonCardsMode)
                .distinct()
                .sortedBy { mode -> mode == LauncherViewMode.CARD_INTERFACE }
                .let(::of)
                ?: fallbackFor(preferredMode ?: lastNonCardsMode)

        /** [migrated] for every device class either legacy map mentions. */
        fun migratedByDeviceClass(
            preferredModes: Map<HomeLayoutDeviceClass, LauncherViewMode>,
            lastNonCardsModes: Map<HomeLayoutDeviceClass, LauncherViewMode>,
        ): Map<HomeLayoutDeviceClass, ModeRing> =
            (preferredModes.keys + lastNonCardsModes.keys).associateWith { deviceClass ->
                migrated(preferredModes[deviceClass], lastNonCardsModes[deviceClass])
            }
    }
}
