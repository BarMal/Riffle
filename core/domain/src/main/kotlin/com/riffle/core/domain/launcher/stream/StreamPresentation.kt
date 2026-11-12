package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemAction
import com.riffle.core.domain.launcher.workspace.ItemId
import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import kotlin.math.abs

/** Where an event is relative to now; the card says "Starts in 12 min", "Now" or "Ended". Pure so it is testable. */
sealed interface EventTiming {
    /** Starts in [minutes] whole minutes (at least 1; "less than a minute" reads as 1). */
    data class Upcoming(val minutes: Long) : EventTiming

    /** In progress; [minutesLeft] is null when the event has no end. */
    data class Ongoing(val minutesLeft: Long?) : EventTiming

    data object Ended : EventTiming

    data object AllDay : EventTiming

    companion object {
        private const val MINUTE = 60_000L

        fun of(
            event: EventContent,
            nowEpochMillis: Long,
        ): EventTiming {
            if (event.allDay) return AllDay
            val start = event.startEpochMillis
            val end = event.endEpochMillis
            return when {
                nowEpochMillis < start -> Upcoming(((start - nowEpochMillis + MINUTE - 1) / MINUTE).coerceAtLeast(1))
                end == null -> if (event.kind == EventKind.ALARM) Ended else Ongoing(null)
                nowEpochMillis < end -> Ongoing(((end - nowEpochMillis + MINUTE - 1) / MINUTE).coerceAtLeast(1))
                else -> Ended
            }
        }
    }
}

/** The media scrubber's arithmetic, kept pure so the drag maps to a position the same way everywhere. */
object MediaProgress {
    /** 0..1 progress, or null when the session reports no usable position and duration. */
    fun fraction(
        positionMillis: Long?,
        durationMillis: Long?,
    ): Float? {
        if (positionMillis == null || durationMillis == null || durationMillis <= 0L) return null
        return (positionMillis.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
    }

    /** The position a scrubber at [fraction] of [durationMillis] means; clamped into the track. */
    fun positionAt(
        fraction: Float,
        durationMillis: Long,
    ): Long = (fraction.coerceIn(0f, 1f) * durationMillis).toLong().coerceIn(0L, durationMillis.coerceAtLeast(0L))

    /** `m:ss`, or `h:mm:ss` from an hour up. Negative values read as zero. */
    fun clock(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0L) / 1000L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}

/**
 * The `Item` the existing Hide flow ([com.riffle.core.domain.launcher.workspace.settings.ExclusionHideActions]) is
 * built on, rebuilt from the typed content the Stream drew, so "Hide" in the Stream writes exactly the exclusion rules
 * the lens pages do (ADR 0003: Hide is not a stream action). Only identity, target, group and the text a rule can
 * match are carried; a redacted value carries none of its text (it is already null) and is marked sensitive.
 * Null for content with nothing to hide by (the next alarm, a task). [groupKey] is the notification group (the app)
 * the content was drawn under; other types hide by app, feed or item only.
 */
object StreamHideItems {
    fun itemFor(
        content: StreamContent,
        groupKey: String? = null,
    ): Item? {
        val privacy = if (content.privacy == StreamPrivacy.REDACTED) ItemPrivacy.SENSITIVE else ItemPrivacy.VISIBLE
        return when (content) {
            is NotificationContent ->
                item(SourceIds.NOTIFICATIONS, content, content.title, content.text, groupKey, content.appLabel, privacy)
            is MediaContent -> item(SourceIds.MEDIA, content, content.title, null, null, content.appLabel, privacy)
            is EventContent ->
                when (content.kind) {
                    EventKind.CALENDAR -> item(SourceIds.CALENDAR, content, content.title, null, null, null, privacy)
                    EventKind.CALENDAR_FEED ->
                        item(SourceIds.ICS, content, content.title, null, null, content.calendarName, privacy)
                    EventKind.ALARM -> null
                }
            is ArticleContent ->
                item(SourceIds.RSS, content, content.title, content.summary, content.feedId, content.feedName, privacy)
            is RecentAppContent -> item(SourceIds.RECENT_APPS, content, content.label, null, null, null, privacy)
            is TaskContent -> null
        }
    }

    private fun item(
        source: SourceId,
        content: StreamContent,
        title: String?,
        body: String?,
        groupKey: String?,
        groupLabel: String?,
        privacy: ItemPrivacy,
    ): Item {
        val target =
            when (content) {
                is NotificationContent -> content.target
                is MediaContent -> content.target
                is EventContent -> content.target
                is ArticleContent -> content.target
                is RecentAppContent -> content.target
                is TaskContent -> content.target
            }
        return Item(
            id = ItemId(content.id.value),
            sourceId = source,
            target = target,
            title = title,
            body = body,
            timeEpochMillis = content.timeEpochMillis?.let { abs(it) },
            groupKey = groupKey,
            groupLabel = groupLabel,
            actions = listOf(ItemAction.Open()),
            privacy = privacy,
        )
    }
}
