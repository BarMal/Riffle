package com.riffle.core.domain.launcher.stream

/** What one Stream page draws. Transient, like the content in it: never persisted. */
sealed interface StreamPageBody {
    /** Content: one app's or feed's [group], or every group of a section that shows as one page. */
    data class Content(val groups: List<StreamGroup>) : StreamPageBody {
        val items: List<StreamContent> get() = groups.flatMap(StreamGroup::items)
    }

    /** The section has nothing to show or cannot show it: the existing honest [result] and its action. */
    data class State(val result: StreamSectionResult) : StreamPageBody
}

/**
 * One page of the Stream's pager (ADR 0003, Views): a vertically scrolling stack or list of one [section]'s content.
 *
 * - [key] is stable across updates (see [StreamPagePlanner]) so the pager never jumps when content arrives.
 * - [groupKey] is the app (`package:profile`) or feed this page is for; null for a section's single page.
 * - [label] is the app or feed name; null means the page is titled by its section.
 * - [count] is how many items the page has, counting those a section limit left out of the group.
 * - [lead] is the first item, whose app gives the page its icon and colour.
 * - [activityEpochMillis] is when the page's newest item happened, which orders the Stage rail's recent app pages.
 */
data class StreamPage(
    val key: String,
    val section: StreamSection,
    val groupKey: String?,
    val label: String?,
    val count: Int,
    val view: StreamView,
    val body: StreamPageBody,
    val lead: StreamContent? = null,
    val activityEpochMillis: Long? = null,
) {
    /** True for the pages of the sections that split into one page per app or feed. */
    val isGroupPage: Boolean get() = groupKey != null
}

/**
 * Plans the Stream's pages from the layout and what each section delivered (ADR 0003, Views):
 *
 * - **Now**: one page (media with its queue, the next event, the alarm).
 * - **Notifications**: one page per app that has notifications, newest app first (the order the section delivers).
 * - **Recents**: one page. **Agenda**: one page. **Tasks**: one page.
 * - **Feeds**: one page per feed with unread items, the feed with the newest article first.
 * - A section with nothing to show (empty, loading, no access, not connected, unavailable, off) is one page holding
 *   that state, so the Stream is never an empty pager and every state keeps its action.
 *
 * Pages follow the layout's section order; a section that is off contributes none. Keys: `section` for a single page
 * and `section/group` for an app or feed page. Both are independent of order and of what else is on screen, so a new
 * notification never changes another page's key.
 */
object StreamPagePlanner {
    fun plan(
        layout: StreamLayout,
        results: Map<StreamSection, StreamSectionResult>,
    ): List<StreamPage> =
        layout.enabledSections.flatMap { entry ->
            pagesOf(entry.section, entry.view, results[entry.section] ?: StreamSectionResult.Loading)
        }

    fun keyOf(
        section: StreamSection,
        groupKey: String? = null,
    ): String = if (groupKey == null) section.storedId else "${section.storedId}/$groupKey"

    private fun pagesOf(
        section: StreamSection,
        view: StreamView,
        result: StreamSectionResult,
    ): List<StreamPage> {
        if (result !is StreamSectionResult.Ready) return listOf(statePage(section, view, result))
        return when (section) {
            StreamSection.NOTIFICATIONS -> result.groups.map { groupPage(section, view, it) }
            StreamSection.FEEDS -> {
                val withUnread = result.groups.filter { group -> group.items.any(::isUnread) }
                if (withUnread.isEmpty()) {
                    listOf(statePage(section, view, StreamSectionResult.Empty))
                } else {
                    withUnread.map { groupPage(section, view, it) }
                }
            }
            StreamSection.NOW,
            StreamSection.RECENTS,
            StreamSection.AGENDA,
            StreamSection.TASKS,
            -> listOf(sectionPage(section, view, result))
        }
    }

    private fun isUnread(content: StreamContent): Boolean = content !is ArticleContent || !content.read

    private fun statePage(
        section: StreamSection,
        view: StreamView,
        result: StreamSectionResult,
    ) = StreamPage(
        key = keyOf(section),
        section = section,
        groupKey = null,
        label = null,
        count = 0,
        view = view,
        body = StreamPageBody.State(result),
    )

    private fun sectionPage(
        section: StreamSection,
        view: StreamView,
        result: StreamSectionResult.Ready,
    ) = StreamPage(
        key = keyOf(section),
        section = section,
        groupKey = null,
        label = null,
        count = result.groups.sumOf { it.totalCount },
        view = view,
        body = StreamPageBody.Content(result.groups),
        lead = result.items.firstOrNull(),
        activityEpochMillis = result.items.mapNotNull { it.timeEpochMillis }.maxOrNull(),
    )

    private fun groupPage(
        section: StreamSection,
        view: StreamView,
        group: StreamGroup,
    ) = StreamPage(
        key = keyOf(section, group.key),
        section = section,
        groupKey = group.key,
        label = group.label?.takeIf { it.isNotBlank() },
        count = group.totalCount,
        view = view,
        body = StreamPageBody.Content(listOf(group)),
        lead = group.items.firstOrNull(),
        activityEpochMillis = group.items.mapNotNull { it.timeEpochMillis }.maxOrNull(),
    )
}

/**
 * Which page stays current when the page list changes (ADR 0003): a new notification must not jump the pager, and a
 * page that goes away hands over to its nearest neighbour.
 */
object StreamPageNavigation {
    /**
     * The key that should be current after the pages changed from [previousKeys] to [newKeys], given that [currentKey]
     * was current. The current page stays current while it exists, wherever it moved to; when it is gone the nearest
     * page that survived is taken (the next one on a tie, so the pager moves on rather than back); when none of the
     * old pages survived, the first. Null only when there are no pages.
     */
    fun currentAfter(
        previousKeys: List<String>,
        currentKey: String?,
        newKeys: List<String>,
    ): String? =
        when {
            newKeys.isEmpty() -> null
            currentKey != null && currentKey in newKeys -> currentKey
            else -> nearestSurvivor(previousKeys, currentKey, newKeys.toSet()) ?: newKeys.first()
        }

    private fun nearestSurvivor(
        previousKeys: List<String>,
        currentKey: String?,
        survivors: Set<String>,
    ): String? {
        val from = previousKeys.indexOf(currentKey)
        if (from < 0) return null
        return (1..previousKeys.size).firstNotNullOfOrNull { distance ->
            previousKeys.getOrNull(from + distance)?.takeIf { it in survivors }
                ?: previousKeys.getOrNull(from - distance)?.takeIf { it in survivors }
        }
    }

    /** The index of [key] in [keys], or [fallback] when it is not there. */
    fun indexOf(
        keys: List<String>,
        key: String?,
        fallback: Int = 0,
    ): Int = keys.indexOf(key).takeIf { it >= 0 } ?: fallback.coerceIn(0, (keys.size - 1).coerceAtLeast(0))

    /** The index [delta] pages from [index], clamped to the pages; the same index when there is nowhere to go. */
    fun step(
        index: Int,
        delta: Int,
        pageCount: Int,
    ): Int = (index + delta).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
}
