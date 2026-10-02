package com.riffle.app.launcher.rss

import com.riffle.core.domain.launcher.settings.FeedBackgroundRunRecord
import com.riffle.core.domain.launcher.settings.FeedRefreshIntervalOption

/** Chip text for the background refresh interval choice. */
fun FeedRefreshIntervalOption.chipLabel(): String =
    when (this) {
        FeedRefreshIntervalOption.OFF -> "Off"
        FeedRefreshIntervalOption.HOURS_1 -> "1 hour"
        FeedRefreshIntervalOption.HOURS_3 -> "3 hours"
        FeedRefreshIntervalOption.HOURS_6 -> "6 hours"
        FeedRefreshIntervalOption.HOURS_12 -> "12 hours"
        FeedRefreshIntervalOption.HOURS_24 -> "24 hours"
    }

/** Plain-language statement of what the chosen interval does, shown under the choice. */
fun FeedRefreshIntervalOption.explanation(): String =
    when (this) {
        FeedRefreshIntervalOption.OFF ->
            "Off. Riffle only fetches your feeds when you tap Refresh. Nothing runs in the background."
        else ->
            "Riffle fetches your enabled feeds about every ${chipLabel()} in the background, " +
                "when the device allows it. Android may delay runs to save battery."
    }

/** Short result wording for the status line; carries no URLs, feed names or article text. */
fun FeedBackgroundRunRecord.Kind.label(): String =
    when (this) {
        FeedBackgroundRunRecord.Kind.UPDATED -> "new articles"
        FeedBackgroundRunRecord.Kind.UNCHANGED -> "nothing new"
        FeedBackgroundRunRecord.Kind.FAILED -> "failed"
        FeedBackgroundRunRecord.Kind.SKIPPED -> "skipped (Wi-Fi or battery saver)"
    }

/** Status line for the last background run; [relativeTime] formats the timestamp for display. */
fun backgroundRefreshStatus(
    record: FeedBackgroundRunRecord?,
    relativeTime: (Long) -> CharSequence,
): String =
    if (record == null) {
        "No background refresh has run yet."
    } else {
        "Last background refresh: ${relativeTime(record.atEpochMillis)} (${record.kind.label()})"
    }
