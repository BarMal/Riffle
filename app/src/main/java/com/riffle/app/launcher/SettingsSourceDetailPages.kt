package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.settings.SourceRow
import com.riffle.core.domain.launcher.workspace.sources.CalendarAccessStatus

internal const val SOURCE_DETAIL_STATUS_TAG = "source-detail-status"
internal const val SOURCE_DETAIL_SWITCH_TAG = "source-detail-switch"
internal const val SOURCE_USED_BY_EMPTY_TAG = "source-used-by-empty"
internal const val SOURCE_DETAIL_ADD_TAG = "source-detail-add"

internal fun sourcePlaceTestTag(index: Int): String = "source-place-$index"

/**
 * A source's detail page (Settings > Sources > a source). Reached from the Sources page
 * (S9: always available). While open it holds the same status subscriptions the Sources page does and
 * releases them on close. It adds no permission, prompt or background work: every action is an existing flow.
 */
@Composable
internal fun SettingsSourceDetailPageContent(
    page: SettingsPage,
    state: SettingsSurfaceState,
    onPageSelected: (SettingsPage) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    val host = LocalWorkspaceSettingsHost.current
    val id = SourceDetailPages.sourceFor(page)
    if (host == null || id == null) {
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
    val row =
        remember(controller, statuses, disabled, id) { controller.rows(statuses, disabled).firstOrNull { it.id == id } }
    if (row == null) {
        Text(
            modifier = Modifier.padding(horizontal = RiffleSpacing.s, vertical = RiffleSpacing.xl),
            text = SourceDetailText.NOT_AVAILABLE,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    SourceDetailScaffold(
        row = row,
        calendarAccess = state.calendarAccessStatus,
        onToggle = { enabled -> controller.setEnabled(id, enabled) },
        onAllow = host.onRequestSourceAccess,
    ) {
        SourceSpecificSections(
            page = page,
            host = host,
            state = state,
            onPageSelected = onPageSelected,
            onAction = onAction,
        )
    }
}

/** The detail page's frame: status and switch, the permission affordance when needed, then [specific] sections. */
@Composable
internal fun SourceDetailScaffold(
    row: SourceRow,
    calendarAccess: CalendarAccessStatus,
    onToggle: (Boolean) -> Unit,
    onAllow: (SourceId) -> Unit,
    modifier: Modifier = Modifier,
    specific: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RiffleSpacing.l)) {
        SourceStatusSection(row = row, calendarAccess = calendarAccess, onToggle = onToggle, onAllow = onAllow)
        specific()
    }
}

@Composable
private fun SourceStatusSection(
    row: SourceRow,
    calendarAccess: CalendarAccessStatus,
    onToggle: (Boolean) -> Unit,
    onAllow: (SourceId) -> Unit,
) {
    SettingsSection(title = row.title) {
        Text(
            modifier = Modifier.padding(horizontal = RiffleSpacing.m),
            text = row.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SourceDetailText.note(row.id)?.let { note ->
            Text(
                modifier = Modifier.padding(horizontal = RiffleSpacing.m, vertical = RiffleSpacing.xs),
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingsListRow(
            modifier =
                Modifier
                    .testTag(SOURCE_DETAIL_STATUS_TAG)
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = SourcesSettingsText.statusDescription(row.title, row.status)
                    },
            title = "Status",
            trailingContent = { SourceStatusChip(row.status) },
        )
        Column(
            modifier = Modifier.padding(horizontal = RiffleSpacing.m),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
        ) {
            SourceAccessAffordance(row = row, calendarAccess = calendarAccess, onAllow = onAllow)
        }
        SettingsSwitchRow(
            modifier = Modifier.testTag(SOURCE_DETAIL_SWITCH_TAG),
            title = SourceDetailText.USE_THIS_SOURCE,
            subtitle = if (row.enabled) "On" else "Off. Pages that use it say it is turned off.",
            checked = row.enabled,
            onCheckedChange = onToggle,
        )
    }
}
