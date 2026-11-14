package com.riffle.core.domain.launcher.notifications

/**
 * Which posted notifications are a group's summary with children. The platform's shade draws such a summary as the
 * group's header and its children as the rows; a launcher that listed both would show the same content twice. So a
 * summary whose group has at least one other notification posted is left out, and each child is its own card (one per
 * conversation). A summary on its own (an app that posts only a summary) stays: it is all there is.
 */
object NotificationGroupSummaries {
    data class Posted(
        val key: String,
        /** The platform's group key (user, package and group); null when the notification is in no group. */
        val groupKey: String?,
        val isSummary: Boolean,
    )

    /** The keys of the summaries in [posted] that have a child posted beside them. */
    fun redundant(posted: List<Posted>): Set<String> {
        val groupsWithChildren = posted.filter { !it.isSummary }.mapNotNull { it.groupKey }.toSet()
        return posted.filter { it.isSummary && it.groupKey in groupsWithChildren }.map { it.key }.toSet()
    }
}
