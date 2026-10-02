package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.settings.SourcePlace
import com.riffle.core.domain.launcher.workspace.settings.SourceRow
import com.riffle.core.domain.launcher.workspace.sources.CalendarAccessStatus

internal const val SOURCE_DETAIL_STATUS_TAG = "source-detail-status"
internal const val SOURCE_DETAIL_SWITCH_TAG = "source-detail-switch"
internal const val SOURCE_USED_BY_EMPTY_TAG = "source-used-by-empty"

internal fun sourcePlaceTestTag(index: Int): String = "source-place-$index"

/**
 * A source's detail page (Settings > Sources > a source). Developer pages like the Sources page: only reachable
 * while Workspaces (preview) is on. While open it holds the same status subscriptions the Sources page does and
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
    val version by host.version.collectAsState()
    val usage =
        remember(host, version, state.availableLayoutDeviceClasses) {
            host.workspaces.sourceUsage(state.availableLayoutDeviceClasses)
        }
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
        places = usage?.placesOf(id),
        showLayoutNames = (usage?.layoutsOf(id)?.size ?: 0) > 1,
        currentLayout = host.currentLayout,
        onToggle = { enabled -> controller.setEnabled(id, enabled) },
        onAllow = host.onRequestSourceAccess,
        onEdit = host.onEdit,
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

/**
 * The detail page's frame: status and switch, the permission affordance when needed, [specific] sections, and the
 * Used by list. Wide windows (600 dp and up) put the source's own sections beside the Used by list.
 */
@Suppress("LongParameterList") // Fixed-state page content: one source's row, its places and the callbacks.
@Composable
internal fun SourceDetailScaffold(
    row: SourceRow,
    calendarAccess: CalendarAccessStatus,
    places: List<SourcePlace>?,
    showLayoutNames: Boolean,
    currentLayout: HomeLayoutDeviceClass,
    onToggle: (Boolean) -> Unit,
    onAllow: (SourceId) -> Unit,
    onEdit: (WorkspaceId) -> Unit,
    modifier: Modifier = Modifier,
    specific: @Composable ColumnScope.() -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val wide = maxWidth >= TWO_PANE_MIN_WIDTH_DP.dp
        val main: @Composable ColumnScope.() -> Unit = {
            SourceStatusSection(row = row, calendarAccess = calendarAccess, onToggle = onToggle, onAllow = onAllow)
            specific()
        }
        val usedBy: @Composable ColumnScope.() -> Unit = {
            SourceUsedBySection(
                places = places,
                showLayoutNames = showLayoutNames,
                currentLayout = currentLayout,
                onEdit = onEdit,
            )
        }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.l)) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(RiffleSpacing.l),
                    content = main,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(RiffleSpacing.l),
                    content = usedBy,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.l)) {
                main()
                usedBy()
            }
        }
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

/** Where this source is read: one tappable row per place; a place on this device's layout opens its workspace. */
@Composable
internal fun SourceUsedBySection(
    places: List<SourcePlace>?,
    showLayoutNames: Boolean,
    currentLayout: HomeLayoutDeviceClass,
    onEdit: (WorkspaceId) -> Unit,
) {
    SettingsSection(title = SourceDetailText.usedByHeading(places?.size ?: 0)) {
        when {
            places == null ->
                SettingsListRow(title = SourceDetailText.LOADING_LAYOUTS)
            places.isEmpty() ->
                SettingsListRow(
                    modifier = Modifier.testTag(SOURCE_USED_BY_EMPTY_TAG),
                    title = SourceDetailText.NOT_USED,
                    subtitle = SourceDetailText.NOT_USED_HELP,
                )
            else -> {
                Text(
                    modifier = Modifier.padding(horizontal = RiffleSpacing.m),
                    text = SourceDetailText.USED_BY_NOTE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                places.forEachIndexed { index, place ->
                    SourcePlaceRow(
                        index = index,
                        place = place,
                        showLayoutName = showLayoutNames,
                        editable = place.layout == currentLayout,
                        onEdit = onEdit,
                    )
                }
            }
        }
    }
}

@Composable
private fun SourcePlaceRow(
    index: Int,
    place: SourcePlace,
    showLayoutName: Boolean,
    editable: Boolean,
    onEdit: (WorkspaceId) -> Unit,
) {
    val layoutName = if (showLayoutName) WorkspacesSettingsText.layoutName(place.layout) else null
    val label = SourceDetailText.placeLabel(place, layoutName)
    val spoken = SourceDetailText.placeSpoken(place, layoutName)
    if (editable) {
        SettingsClickableRow(
            modifier =
                Modifier
                    .testTag(sourcePlaceTestTag(index))
                    .semantics { contentDescription = "$spoken. Double tap to ${SourceDetailText.OPEN_WORKSPACE}" },
            title = label,
            onClick = { onEdit(place.workspaceId) },
            trailingContent = { SettingsButtonText(text = "Edit") },
        )
    } else {
        SettingsListRow(
            modifier =
                Modifier
                    .testTag(sourcePlaceTestTag(index))
                    .semantics { contentDescription = "$spoken. ${WorkspacesSettingsText.EDIT_OTHER_LAYOUT_REASON}" },
            title = label,
            subtitle = WorkspacesSettingsText.EDIT_OTHER_LAYOUT_REASON,
        )
    }
}
