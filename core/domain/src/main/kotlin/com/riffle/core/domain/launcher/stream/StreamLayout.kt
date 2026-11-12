package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.split.SplitMode
import com.riffle.core.domain.launcher.workspace.workspaceLayoutClass

/**
 * How one Stream section is drawn (ADR 0003, Views): a Stack of cards or a List of rows, chosen per section.
 * [storedId] is a stored contract: never rename one, only add new ids.
 */
enum class StreamView(val storedId: String) {
    STACK("stack"),
    LIST("list"),
    ;

    companion object {
        fun fromStoredId(id: String?): StreamView? = entries.firstOrNull { it.storedId == id }
    }
}

/** One section's place in a [StreamLayout]: whether it is shown and how it is drawn. */
data class StreamSectionLayout(
    val section: StreamSection,
    val enabled: Boolean = true,
    val view: StreamView = StreamLayout.defaultViewOf(section),
)

/**
 * How Home and the Stream share an expanded posture (ADR 0003, Folded and unfolded), remembered per posture: the
 * Stream's [fraction] of the room when split, and the [lastShown] state the Stream button returns to ([SplitMode.SPLIT]
 * or [SplitMode.STREAM_ONLY], never Home only). Folded and phone postures store the defaults and never read them.
 */
data class StreamSplit(
    val fraction: Float = DEFAULT_FRACTION,
    val lastShown: SplitMode = SplitMode.SPLIT,
) {
    init {
        require(fraction in MIN_FRACTION..MAX_FRACTION) {
            "A split fraction is within $MIN_FRACTION..$MAX_FRACTION: $fraction"
        }
        require(lastShown != SplitMode.HOME_ONLY) { "The last-used shown state is a split or Stream only." }
    }

    companion object {
        /** The Stream may take at least this much of the room in a split, and at most [MAX_FRACTION]. */
        const val MIN_FRACTION = 0.25f
        const val MAX_FRACTION = 0.75f

        /** The Stream takes a little less than half: Home stays the larger, the start page. */
        const val DEFAULT_FRACTION = 0.4f

        /** A split with any [fraction] (clamped into range) and [lastShown] (Home only reads as split). */
        fun of(
            fraction: Float,
            lastShown: SplitMode,
        ): StreamSplit =
            StreamSplit(
                fraction = if (fraction.isNaN()) DEFAULT_FRACTION else fraction.coerceIn(MIN_FRACTION, MAX_FRACTION),
                lastShown = if (lastShown == SplitMode.HOME_ONLY) SplitMode.SPLIT else lastShown,
            )
    }
}

/**
 * What the Stream shows on one posture: the six fixed sections (each exactly once) in the user's order, each on or
 * off and drawn as a Stack or a List, plus the few per-section [options] the ADR allows. Holds no content. The
 * sections are fixed by [StreamSection]; the user only chooses which are on, their order and their view.
 */
data class StreamLayout(
    val sections: List<StreamSectionLayout> = defaultSections(),
    val options: StreamSectionOptions = StreamSectionOptions(),
    /** How an expanded posture splits the screen with Home; the defaults elsewhere. */
    val split: StreamSplit = StreamSplit(),
) {
    init {
        require(
            sections.map {
                it.section
            }.toSet() == StreamSection.entries.toSet() && sections.size == StreamSection.entries.size,
        ) {
            "A Stream layout holds every section exactly once."
        }
    }

    /** The sections that are on, in order. */
    val enabledSections: List<StreamSectionLayout> get() = sections.filter { it.enabled }

    fun layoutOf(section: StreamSection): StreamSectionLayout = sections.first { it.section == section }

    fun viewOf(section: StreamSection): StreamView = layoutOf(section).view

    companion object {
        /** Now, Notifications and Feeds read as cards, Recents as an icon row, Agenda and Tasks as rows. */
        fun defaultViewOf(section: StreamSection): StreamView =
            when (section) {
                StreamSection.NOW,
                StreamSection.NOTIFICATIONS,
                StreamSection.RECENTS,
                StreamSection.FEEDS,
                -> StreamView.STACK
                StreamSection.AGENDA,
                StreamSection.TASKS,
                -> StreamView.LIST
            }

        fun defaultSections(): List<StreamSectionLayout> = StreamSection.entries.map { StreamSectionLayout(it) }

        val DEFAULT = StreamLayout()
    }
}

/**
 * Every posture's [StreamLayout]. A posture with nothing stored uses [StreamLayout.DEFAULT]; a landscape phone shares
 * the phone's layout, as the workspace layouts do ([workspaceLayoutClass]).
 */
data class StreamLayouts(val byPosture: Map<HomeLayoutDeviceClass, StreamLayout> = emptyMap()) {
    fun forPosture(deviceClass: HomeLayoutDeviceClass): StreamLayout =
        byPosture[deviceClass.workspaceLayoutClass()] ?: StreamLayout.DEFAULT

    fun with(
        deviceClass: HomeLayoutDeviceClass,
        layout: StreamLayout,
    ): StreamLayouts = StreamLayouts(byPosture + (deviceClass.workspaceLayoutClass() to layout))

    /** True when every stored layout is the default, so there is nothing worth writing to a backup. */
    val isDefault: Boolean get() = byPosture.values.all { it == StreamLayout.DEFAULT }
}
