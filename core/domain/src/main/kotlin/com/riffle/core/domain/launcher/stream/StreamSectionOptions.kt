package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleSet
import com.riffle.core.domain.launcher.workspace.lens.DayBucketer
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevels
import com.riffle.core.domain.launcher.workspace.lens.ZoneDayBucketer
import java.time.ZoneId

/** The few per-section options ADR 0003 allows. P4 stores them in the Stream layout; defaults match the plan. */
data class StreamSectionOptions(
    val now: NowOptions = NowOptions(),
    val notifications: NotificationsOptions = NotificationsOptions(),
    val recents: RecentsOptions = RecentsOptions(),
    val agenda: AgendaOptions = AgendaOptions(),
    val tasks: TasksOptions = TasksOptions(),
    val feeds: FeedsOptions = FeedsOptions(),
)

data class NowOptions(
    /** How many media cards (several players can be active). */
    val maxMedia: Int = 1,
    val showNextEvent: Boolean = true,
    val showAlarm: Boolean = true,
) {
    init {
        require(maxMedia >= 0) { "maxMedia cannot be negative." }
    }
}

data class NotificationsOptions(
    val maxApps: Int = 8,
    val maxPerApp: Int = 5,
    /** Only notifications posted within this many milliseconds; null keeps every active one. */
    val maxAgeMillis: Long? = null,
) {
    init {
        require(maxApps >= 0 && maxPerApp >= 0) { "Limits cannot be negative." }
        require(maxAgeMillis == null || maxAgeMillis > 0L) { "maxAgeMillis must be positive." }
    }
}

data class RecentsOptions(
    val limit: Int = 10,
    /** Only apps used within this many milliseconds; null keeps every recent one. */
    val maxAgeMillis: Long? = null,
) {
    init {
        require(limit >= 0) { "limit cannot be negative." }
        require(maxAgeMillis == null || maxAgeMillis > 0L) { "maxAgeMillis must be positive." }
    }
}

data class AgendaOptions(
    val daysAhead: Int = 7,
    val maxEvents: Int = 20,
    val includeCalendarFeeds: Boolean = true,
) {
    init {
        require(daysAhead > 0) { "daysAhead must be positive." }
        require(maxEvents >= 0) { "maxEvents cannot be negative." }
    }
}

data class TasksOptions(
    val limit: Int = 10,
    val showCompleted: Boolean = false,
) {
    init {
        require(limit >= 0) { "limit cannot be negative." }
    }
}

data class FeedsOptions(
    val maxFeeds: Int = 5,
    val perFeed: Int = 5,
) {
    init {
        require(maxFeeds >= 0 && perFeed >= 0) { "Limits cannot be negative." }
    }
}

/**
 * Everything a section evaluation needs besides the sources, all injected so evaluation stays a pure function.
 * [exclusions] and [contentLevels] are the same values the lens pipeline reads (`LensEvaluationContext`), so a
 * Hide rule or a Settings > Privacy level applies to the Stream exactly as it does to items.
 */
data class StreamContext(
    val nowEpochMillis: Long,
    val options: StreamSectionOptions = StreamSectionOptions(),
    val exclusions: ExclusionRuleSet = ExclusionRuleSet.EMPTY,
    val contentLevels: SourceContentLevels = SourceContentLevels.ALL_SHOWN,
    val dayBucketer: DayBucketer = ZoneDayBucketer(ZoneId.of("UTC")),
)
