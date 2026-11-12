package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
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
 * What the Stream shows on one posture: the six fixed sections (each exactly once) in the user's order, each on or
 * off and drawn as a Stack or a List, plus the few per-section [options] the ADR allows. Holds no content. The
 * sections are fixed by [StreamSection]; the user only chooses which are on, their order and their view.
 */
data class StreamLayout(
    val sections: List<StreamSectionLayout> = defaultSections(),
    val options: StreamSectionOptions = StreamSectionOptions(),
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
