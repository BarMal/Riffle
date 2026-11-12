package com.riffle.core.domain.launcher.stream

/**
 * One marker on the spine (ADR 0003, Views): the Stream's page indicator, a strip of one marker per page that scrolls
 * with the pager. [label] is the app or feed name, or null when the marker is named by its [section];
 * [startsSection] says a separator belongs before it (the first page of a section after another section's).
 */
data class StreamSpineMarker(
    val pageKey: String,
    val section: StreamSection,
    val groupKey: String?,
    val label: String?,
    val selected: Boolean,
    /** One-based position and total, for the spoken "3 of 7". */
    val position: Int,
    val total: Int,
    val startsSection: Boolean,
)

/** The spine's model: pure, so the order, the selection and the separators are tested without a screen. */
object StreamSpine {
    /** One marker per page, in page order, with the page at [currentIndex] selected. */
    fun markers(
        pages: List<StreamPage>,
        currentIndex: Int,
    ): List<StreamSpineMarker> =
        pages.mapIndexed { index, page ->
            StreamSpineMarker(
                pageKey = page.key,
                section = page.section,
                groupKey = page.groupKey,
                label = page.label,
                selected = index == currentIndex,
                position = index + 1,
                total = pages.size,
                startsSection = index > 0 && pages[index - 1].section != page.section,
            )
        }

    /**
     * The spine's scroll position as a fractional page index while a swipe is in flight: [page] plus how far into the
     * next page it is ([offset] -0.5 to 0.5, as a pager reports it), kept inside the pages. The carousel centres the
     * marker at this index, as Calm's carousel follows its pager.
     */
    fun focusIndex(
        page: Int,
        offset: Float,
        pageCount: Int,
    ): Float = (page + offset).coerceIn(0f, (pageCount - 1).coerceAtLeast(0).toFloat())
}
