package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.designsystem.RiffleElevation
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.workspace.WorkspacesSettingsController
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.settings.LayoutFallbackNotice
import com.riffle.core.domain.launcher.workspace.settings.WorkspaceRow
import com.riffle.core.domain.launcher.workspace.settings.WorkspacesSettingsAction
import com.riffle.core.domain.launcher.workspace.settings.WorkspacesSettingsModel

/** Settings > Workspaces. Reachable only from the Developer section while Workspaces (preview) is on. */
@Composable
internal fun SettingsWorkspacesPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    val host = LocalWorkspaceSettingsHost.current
    if (host == null) {
        SettingsPreviewOffNote()
    } else {
        HostedWorkspacesPage(host = host, state = state, onAction = onAction)
    }
}

@Composable
private fun HostedWorkspacesPage(
    host: WorkspaceSettingsHost,
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    val version by host.version.collectAsState()
    val viewed = state.selectedLayoutDeviceClass
    val tabs =
        remember(state.availableLayoutDeviceClasses) { settingsLayoutDeviceTabs(state.availableLayoutDeviceClasses) }
    val model =
        remember(host, version, viewed, tabs) {
            host.workspaces.model(viewed, host.currentLayout, tabs.map { it.deviceClass })
        }
    WorkspacesFeedbackEffect(host.workspaces)
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.l)) {
        WorkspacesSettingsContent(
            model = model,
            viewed = viewed,
            tabs = tabs,
            callbacks =
                WorkspacesPageCallbacks(
                    onAction = { action -> host.workspaces.dispatch(viewed, action) },
                    onEdit = host.onEdit,
                    onSelectLayout = { layout ->
                        onAction(LauncherShellAction.SelectSettingsLayoutDeviceClass(layout))
                    },
                ),
        )
        SettingsReturnBehaviorSection(
            current = state.settings.home.returnBehavior,
            onSelect = { behavior -> onAction(LauncherShellAction.SelectReturnBehavior(behavior)) },
        )
    }
}

/**
 * Announces each action's outcome in the Settings snackbar (read out by TalkBack), with Undo for destructive
 * ones. Leaving the page drops whatever is pending so a stale Undo can never reappear.
 */
@Composable
private fun WorkspacesFeedbackEffect(controller: WorkspacesSettingsController) {
    val snackbar = LocalSettingsSnackbarHostState.current
    val feedback by controller.feedback.collectAsState()
    LaunchedEffect(feedback?.id) {
        val current = feedback
        if (current != null && snackbar != null) {
            val result =
                snackbar.showSnackbar(
                    message = current.message,
                    actionLabel = if (current.canUndo) WorkspacesSettingsText.UNDO else null,
                    withDismissAction = current.canUndo,
                    duration = if (current.canUndo) SnackbarDuration.Long else SnackbarDuration.Short,
                )
            if (result == SnackbarResult.ActionPerformed) controller.undo()
            controller.feedbackShown(current.id)
        }
    }
    DisposableEffect(controller) { onDispose { controller.leave() } }
}

/**
 * The page itself, with no controller: [model] is null until the workspaces have loaded. Compact widths show one
 * list with a per-row menu; at medium and expanded widths it is list-detail (the list on the left, the selected
 * workspace's actions on the right), keeping the selected row by id.
 */
@Composable
internal fun WorkspacesSettingsContent(
    model: WorkspacesSettingsModel?,
    viewed: HomeLayoutDeviceClass,
    tabs: List<SettingsLayoutDeviceTab>,
    callbacks: WorkspacesPageCallbacks,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf<WorkspaceDialog?>(null) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RiffleSpacing.l),
    ) {
        WorkspacesLayoutTabs(tabs = tabs, selected = viewed, onSelect = callbacks.onSelectLayout)
        if (model == null) {
            Text(
                modifier = Modifier.padding(horizontal = RiffleSpacing.s),
                text = WorkspacesSettingsText.NOT_LOADED,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            WorkspacesNotes(model)
            WorkspacesPanes(model = model, callbacks = callbacks, openDialog = { dialog = it })
        }
    }
    dialog?.let { open ->
        WorkspacesDialogs(dialog = open, model = model, callbacks = callbacks, onDismiss = { dialog = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspacesLayoutTabs(
    tabs: List<SettingsLayoutDeviceTab>,
    selected: HomeLayoutDeviceClass,
    onSelect: (HomeLayoutDeviceClass) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, tab ->
            SegmentedButton(
                selected = tab.deviceClass == selected,
                onClick = { onSelect(tab.deviceClass) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                modifier =
                    Modifier.semantics {
                        contentDescription = WorkspacesSettingsText.layoutName(tab.deviceClass)
                    },
                label = { Text(text = tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
private fun WorkspacesNotes(model: WorkspacesSettingsModel) {
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
        Column(
            modifier = Modifier.padding(horizontal = RiffleSpacing.s),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
        ) {
            Text(
                modifier = Modifier.semantics { heading() },
                text = WorkspacesSettingsText.editingLayout(model.layout, model.canEdit),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = WorkspacesSettingsText.EDITING_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        model.fallback?.let { notice -> WorkspacesFallbackNotice(notice) }
    }
}

/** The stored active workspace cannot be drawn on this layout; says which default is shown and why. */
@Composable
private fun WorkspacesFallbackNotice(notice: LayoutFallbackNotice) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(WORKSPACES_FALLBACK_TEST_TAG),
        shape = RiffleShapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = RiffleElevation.level0,
    ) {
        Text(
            modifier = Modifier.padding(RiffleSpacing.m).semantics { liveRegion = LiveRegionMode.Polite },
            text = WorkspacesSettingsText.fallbackNotice(notice),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun WorkspacesPanes(
    model: WorkspacesSettingsModel,
    callbacks: WorkspacesPageCallbacks,
    openDialog: (WorkspaceDialog) -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val wide = maxWidth >= TWO_PANE_MIN_WIDTH_DP.dp
        val selectedRow =
            model.rows.firstOrNull { it.id.value == selectedId } ?: model.rows.firstOrNull { it.isActive }
                ?: model.rows.first()
        val run = { row: WorkspaceRow, kind: WorkspaceRowActionKind ->
            callbacks.runRowAction(row, kind, openDialog)
        }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.l)) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(RiffleSpacing.l),
                ) {
                    WorkspaceListSection(
                        model = model,
                        wide = true,
                        highlightedId = selectedRow.id,
                        onSelect = { selectedId = it.value },
                        callbacks = callbacks,
                        run = run,
                    )
                    WorkspacesAddSection(model = model, openDialog = openDialog)
                }
                Column(modifier = Modifier.weight(1f)) {
                    WorkspaceDetailPanel(
                        row = selectedRow,
                        actions = workspaceRowActions(selectedRow, model.canEdit),
                        onActivate = { callbacks.onAction(WorkspacesSettingsAction.Activate(selectedRow.id)) },
                        onRun = { kind -> run(selectedRow, kind) },
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.l)) {
                WorkspaceListSection(
                    model = model,
                    wide = false,
                    highlightedId = null,
                    onSelect = {},
                    callbacks = callbacks,
                    run = run,
                )
                WorkspacesAddSection(model = model, openDialog = openDialog)
            }
        }
    }
}

@Composable
private fun WorkspaceListSection(
    model: WorkspacesSettingsModel,
    wide: Boolean,
    highlightedId: WorkspaceId?,
    onSelect: (WorkspaceId) -> Unit,
    callbacks: WorkspacesPageCallbacks,
    run: (WorkspaceRow, WorkspaceRowActionKind) -> Unit,
) {
    SettingsSection(title = WorkspacesSettingsText.WORKSPACES_TITLE) {
        model.rows.forEach { row ->
            WorkspaceListItem(
                row = row,
                actions = workspaceRowActions(row, model.canEdit),
                wide = wide,
                highlighted = row.id == highlightedId,
                onClick = {
                    if (wide) onSelect(row.id) else callbacks.onAction(WorkspacesSettingsAction.Activate(row.id))
                },
                onActivate = { callbacks.onAction(WorkspacesSettingsAction.Activate(row.id)) },
                onRun = { kind -> run(row, kind) },
            )
        }
    }
}

@Composable
private fun WorkspacesAddSection(
    model: WorkspacesSettingsModel,
    openDialog: (WorkspaceDialog) -> Unit,
) {
    SettingsSection(title = "Add or copy") {
        SettingsClickableRow(
            title = WorkspacesSettingsText.INSTALL_PRESET,
            subtitle = "Add a workspace from a preset",
            onClick = { openDialog(WorkspaceDialog.PickPreset) },
        )
        if (model.copySources.isNotEmpty()) {
            SettingsClickableRow(
                title = WorkspacesSettingsText.COPY_FROM_OTHER,
                subtitle = "A one-time copy of another layout",
                onClick = { openDialog(WorkspaceDialog.PickCopySource) },
            )
        }
    }
}

internal const val WORKSPACES_FALLBACK_TEST_TAG = "workspaces-settings-fallback"

/** At and above this width the Workspaces and Sources pages use two panes (an unfolded foldable, a tablet). */
internal const val TWO_PANE_MIN_WIDTH_DP = 600
