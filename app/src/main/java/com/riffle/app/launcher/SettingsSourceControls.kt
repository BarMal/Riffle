package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleElevation
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.workspace.sourceAccessRouteFor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.settings.SourceRow
import com.riffle.core.domain.launcher.workspace.settings.SourceStatus
import com.riffle.core.domain.launcher.workspace.sources.CalendarAccessStatus

internal const val SOURCE_DETAIL_STATUS_TAG = "source-detail-status"
internal const val SOURCE_DETAIL_SWITCH_TAG = "source-detail-switch"

internal fun sourceSwitchTestTag(id: SourceId): String = "source-switch-${id.value}"

/**
 * The sources behind a Stream section ([ids]): for each, what it shows, its state in words, the Allow button with its
 * reason when it needs access (only ever a tap away, never automatic) and its on/off switch. This is where a source is
 * turned off; the section itself is turned off on the Stream page. Without the sources host a note shows.
 */
@Composable
internal fun StreamSourceSections(
    ids: List<SourceId>,
    anchor: SettingKey?,
    calendarAccess: CalendarAccessStatus,
) {
    val host = LocalWorkspaceSettingsHost.current
    if (host == null) {
        SettingsPreviewOffNote()
        return
    }
    val controller = host.sources
    DisposableEffect(controller) {
        controller.open()
        onDispose { controller.close() }
    }
    val statuses by controller.statuses.collectAsState()
    val disabled by controller.disabled.collectAsState()
    val rows =
        remember(controller, statuses, disabled, ids) { controller.rows(statuses, disabled).filter { it.id in ids } }
    rows.forEachIndexed { index, row ->
        StreamSourceSection(
            row = row,
            calendarAccess = calendarAccess,
            onToggle = { enabled -> controller.setEnabled(row.id, enabled) },
            onAllow = host.onRequestSourceAccess,
            modifier = if (index == 0 && anchor != null) Modifier.settingAnchor(anchor) else Modifier,
        )
    }
}

@Composable
internal fun StreamSourceSection(
    row: SourceRow,
    calendarAccess: CalendarAccessStatus,
    onToggle: (Boolean) -> Unit,
    onAllow: (SourceId) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(title = row.title) {
        Text(
            modifier = Modifier.padding(horizontal = RiffleSpacing.m),
            text = row.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsListRow(
            modifier =
                Modifier
                    .testTag(SOURCE_DETAIL_STATUS_TAG)
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = SourcesSettingsText.statusDescription(row.title, row.status)
                    },
            title = stringResource(R.string.settings_source_status),
            trailingContent = { SourceStatusChip(row.status) },
        )
        Column(
            modifier = Modifier.padding(horizontal = RiffleSpacing.m),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
        ) {
            SourceAccessAffordance(row = row, calendarAccess = calendarAccess, onAllow = onAllow)
        }
        SettingsSwitchRow(
            modifier = modifier.testTag(SOURCE_DETAIL_SWITCH_TAG).testTag(sourceSwitchTestTag(row.id)),
            title = stringResource(R.string.settings_source_use, row.title),
            subtitle =
                if (row.enabled) {
                    stringResource(R.string.settings_source_on)
                } else {
                    stringResource(R.string.settings_source_off)
                },
            checked = row.enabled,
            onCheckedChange = onToggle,
        )
    }
}

/** The Allow button and its rationale, shown only when an enabled source needs permission; never automatic. */
@Composable
internal fun SourceAccessAffordance(
    row: SourceRow,
    calendarAccess: CalendarAccessStatus,
    onAllow: (SourceId) -> Unit,
) {
    val route = sourceAccessRouteFor(row.id)
    val needsPermission = row.enabled && row.status == SourceStatus.NEEDS_PERMISSION
    val allowLabel = SourcesSettingsText.allowLabel(route, calendarAccess).takeIf { needsPermission } ?: return
    SourcesSettingsText.rationale(route, calendarAccess)?.let { rationale ->
        Text(
            text = rationale,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    FilledTonalButton(
        onClick = { onAllow(row.id) },
        modifier = Modifier.heightIn(min = RiffleSpacing.xxxl),
    ) {
        Text(text = allowLabel)
    }
}

/** The status as words in a small chip: never colour alone. */
@Composable
internal fun SourceStatusChip(status: SourceStatus) {
    val color =
        when (status) {
            SourceStatus.READY -> MaterialTheme.colorScheme.secondaryContainer
            SourceStatus.NEEDS_PERMISSION -> MaterialTheme.colorScheme.tertiaryContainer
            SourceStatus.UNAVAILABLE -> MaterialTheme.colorScheme.errorContainer
            SourceStatus.LOADING, SourceStatus.OFF -> MaterialTheme.colorScheme.surfaceVariant
        }
    Surface(
        shape = RiffleShapes.small,
        color = color,
        tonalElevation = RiffleElevation.level0,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = RiffleSpacing.s, vertical = RiffleSpacing.xxs),
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Splits [this] into [columns] consecutive parts of near-equal size (one part when [columns] is 1). */
internal fun <T> List<T>.chunkedBy(columns: Int): List<List<T>> {
    if (columns <= 1 || size <= 1) return listOf(this)
    val first = (size + 1) / 2
    return listOf(take(first), drop(first))
}
