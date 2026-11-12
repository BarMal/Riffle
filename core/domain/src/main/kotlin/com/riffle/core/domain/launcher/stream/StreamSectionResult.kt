package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceSubscription

/**
 * A run of content that belongs together: an app's notifications, a feed's articles, one day of the agenda.
 * [key] is stable (`package:profile`, a feed id, an ISO `yyyy-MM-dd` day); [label] is the display name when the
 * source has one (an app or feed name) and null for a day, which the UI formats from [key]. [totalCount] is how
 * many items the group had before the section's limit, so a card can say "+3 more".
 */
data class StreamGroup(
    val key: String,
    val label: String?,
    val items: List<StreamContent>,
    val totalCount: Int = items.size,
) {
    val hiddenCount: Int get() = totalCount - items.size
}

/** What a Stream section can draw right now. Transient; never persisted. */
sealed interface StreamSectionResult {
    data object Loading : StreamSectionResult

    /** At least one item, grouped, sorted and limited. Sections that do not group have one group. */
    data class Ready(val groups: List<StreamGroup>) : StreamSectionResult {
        init {
            require(groups.isNotEmpty() && groups.all { it.items.isNotEmpty() }) { "A ready section has content." }
        }

        val items: List<StreamContent> get() = groups.flatMap(StreamGroup::items)
    }

    /** Readable, but nothing to show (or everything was hidden by exclusions or the section's options). */
    data object Empty : StreamSectionResult

    /** The user has not granted what these sources need; offer the existing access flow, never prompt. */
    data class PermissionRequired(val sources: List<SourceId>) : StreamSectionResult

    /** Tasks only: no task account is connected, or its token was rejected ([reason]). */
    data class NotConnected(val reason: NotConnectedReason = NotConnectedReason.NO_ACCOUNT) : StreamSectionResult

    data object Unavailable : StreamSectionResult

    /** The user turned every source of this section off (Settings > Sources). */
    data object Off : StreamSectionResult
}

/** Receives a section's results; may be called from any thread (never the main thread), never after cancel. */
fun interface StreamSectionListener {
    fun onResult(result: StreamSectionResult)
}

typealias StreamSubscription = SourceSubscription
