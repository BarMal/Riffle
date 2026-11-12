package com.riffle.app.screenshots

import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.settings.SourceRow
import com.riffle.core.domain.launcher.workspace.settings.SourceStatus
import com.riffle.core.domain.launcher.workspace.settings.SourcesSettingsPlanner

/** Fixed sources for the Settings > Sources screenshots (fakes only). */
internal object SourcesFixtures {
    private val statuses =
        mapOf(
            SourceIds.ALL_APPS to SourceStatus.READY,
            SourceIds.RECENT_APPS to SourceStatus.NEEDS_PERMISSION,
            SourceIds.QUICK_ACTIONS to SourceStatus.READY,
            SourceIds.NOTIFICATIONS to SourceStatus.NEEDS_PERMISSION,
            SourceIds.MEDIA to SourceStatus.NEEDS_PERMISSION,
            SourceIds.RSS to SourceStatus.READY,
        )

    val sources: List<SourceRow> =
        SourcesSettingsPlanner.plan(
            descriptors = SourceIds.BUILT_IN.filterNot { it == SourceIds.SEARCH }.map { SourceDescriptor(it) },
            statuses = statuses,
            disabled = setOf(SourceIds.CALENDAR),
        )
}
