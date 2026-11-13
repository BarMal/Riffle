package com.riffle.core.domain.launcher.dockpull

/**
 * Where a Dock pull leads. A pull away from the Dock's edge opens the Library (ADR 0003, Amendment 3); the Stream and
 * Home are reached by pressing the Dock down (`dock/DockPress.kt`) or by the Dock's accessibility actions, which settle
 * the same transition from rest.
 */
enum class DockPullDestination {
    /** The Library (from Home or from the Stream). */
    LIBRARY,

    /** The Stream, from Home. */
    STREAM,

    /** Home, from the Stream. */
    HOME,
}

/** One entry of the dynamic side as a touch sees it: its [key] and its extent along the Dock's run (px). */
data class DockPullEntryExtent(
    val key: String,
    val start: Float,
    val end: Float,
)

/**
 * The entry under a touch that landed at [position] along the Dock's run, in the same space as the [entries] extents,
 * or null when it landed on none of them (the gap between entries, the Dock's padding, the pinned side). The last
 * entry listed wins where two overlap, which is the one drawn on top.
 */
fun dockPullEntryAt(
    position: Float,
    entries: List<DockPullEntryExtent>,
): String? = entries.lastOrNull { position >= it.start && position < it.end }?.key
