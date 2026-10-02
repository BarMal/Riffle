package com.riffle.app.launcher.ics

import com.riffle.core.domain.launcher.workspace.sources.ics.DefaultIcsEngine
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsEngine
import com.riffle.core.recurrence.Ical4jRecurrenceExpander

/** A feed body larger than this is refused by the transport before any parsing (RSS allows 1 MiB). */
internal const val ICS_MAX_RESPONSE_BYTES = 2 * 1024 * 1024

/**
 * The one place the app names the recurrence library: the bounded in-repo parser reads the feed and
 * `core/recurrence-ical4j` (the only module that imports ical4j) expands its recurrence rules.
 * See docs/product/workspaces-ics-source.md.
 */
internal fun defaultIcsEngine(): IcsEngine = DefaultIcsEngine(Ical4jRecurrenceExpander())
