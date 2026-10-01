package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.designsystem.RiffleElevation
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.exclusions.ExclusionsAnnouncements
import com.riffle.app.launcher.exclusions.ExclusionsSettingsController
import com.riffle.app.launcher.exclusions.ExclusionsSettingsText
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleId
import com.riffle.core.domain.launcher.workspace.settings.ExclusionRuleRow
import com.riffle.core.domain.launcher.workspace.settings.ExclusionRuleSection
import com.riffle.core.domain.launcher.workspace.settings.ExclusionsSettingsAction
import com.riffle.core.domain.launcher.workspace.settings.ExclusionsSettingsModel
import com.riffle.core.domain.launcher.workspace.settings.TextRuleProblem

internal const val EXCLUSIONS_EMPTY_TEST_TAG = "exclusions-empty"
internal const val EXCLUSIONS_ADD_TEST_TAG = "exclusions-add-text-rule"
internal const val EXCLUSIONS_SUMMARY_TEST_TAG = "exclusions-summary"

/**
 * Settings > Sources > Hidden items and rules. Reachable only from the Sources page while Workspaces (preview) is
 * on. While it is open the sources the viewed layout's rules can hide from are read to count what each rule hides;
 * closing the page releases them.
 */
@Composable
internal fun SettingsExclusionsPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    val host = LocalWorkspaceSettingsHost.current
    val controller = host?.exclusions
    if (host == null) {
        SettingsPreviewOffNote()
    } else if (controller == null) {
        Text(
            modifier = Modifier.padding(horizontal = RiffleSpacing.s, vertical = RiffleSpacing.xl),
            text = ExclusionsSettingsText.NOT_LOADED,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        HostedExclusionsPage(controller = controller, state = state, onAction = onAction)
    }
}

@Composable
private fun HostedExclusionsPage(
    controller: ExclusionsSettingsController,
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    val viewed = state.selectedLayoutDeviceClass
    val tabs =
        remember(state.availableLayoutDeviceClasses) { settingsLayoutDeviceTabs(state.availableLayoutDeviceClasses) }
    DisposableEffect(controller) {
        controller.open()
        onDispose { controller.close() }
    }
    LaunchedEffect(controller, viewed) { controller.watch(viewed) }
    val version by controller.version.collectAsState()
    val counts by controller.counts.collectAsState()
    val model =
        remember(controller, version, counts, viewed, tabs) {
            controller.model(viewed, tabs.map { it.deviceClass }, counts)
        }
    ExclusionsFeedbackEffect(controller)
    ExclusionsSettingsContent(
        model = model,
        viewed = viewed,
        tabs = tabs,
        callbacks =
            ExclusionsPageCallbacks(
                onAction = { action -> controller.dispatch(viewed, action) },
                onSelectLayout = { layout ->
                    onAction(LauncherShellAction.SelectSettingsLayoutDeviceClass(layout))
                },
                problemWith = { draft -> controller.problemWith(viewed, draft) },
            ),
    )
}

/**
 * Announces each outcome in the Settings snackbar (read out by TalkBack), with Undo for deleting. Leaving the page
 * drops whatever is pending so a stale Undo can never reappear.
 */
@Composable
private fun ExclusionsFeedbackEffect(controller: ExclusionsSettingsController) {
    val snackbar = LocalSettingsSnackbarHostState.current
    val feedback by controller.feedback.collectAsState()
    LaunchedEffect(feedback?.id) {
        val current = feedback
        if (current != null && snackbar != null) {
            val result =
                snackbar.showSnackbar(
                    message = current.message,
                    actionLabel = if (current.canUndo) ExclusionsSettingsText.UNDO else null,
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
 * The page itself, with no controller: [model] is null until the rules have loaded. At medium and expanded widths the
 * kinds run in two columns (as on the Sources page); every row is the same one-element switch at any width.
 */
@Composable
internal fun ExclusionsSettingsContent(
    model: ExclusionsSettingsModel?,
    viewed: HomeLayoutDeviceClass,
    tabs: List<SettingsLayoutDeviceTab>,
    callbacks: ExclusionsPageCallbacks,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf<ExclusionDialog?>(null) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RiffleSpacing.l),
    ) {
        WorkspacesLayoutTabs(tabs = tabs, selected = viewed, onSelect = callbacks.onSelectLayout)
        if (model == null) {
            Text(
                modifier = Modifier.padding(horizontal = RiffleSpacing.s),
                text = ExclusionsSettingsText.NOT_LOADED,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ExclusionsNotes(model)
            if (model.isEmpty) ExclusionsEmptyState()
            ExclusionsSections(model = model, callbacks = callbacks, openDialog = { dialog = it })
            ExclusionsAddSection(model = model, openDialog = { dialog = it })
        }
    }
    dialog?.let { open ->
        ExclusionsDialogs(
            dialog = open,
            rowFor = { id -> model?.row(id) },
            callbacks = callbacks,
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun ExclusionsNotes(model: ExclusionsSettingsModel) {
    Column(
        modifier = Modifier.padding(horizontal = RiffleSpacing.s),
        verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
    ) {
        Text(
            modifier =
                Modifier
                    .testTag(EXCLUSIONS_SUMMARY_TEST_TAG)
                    .semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
            text =
                ExclusionsSettingsText.layoutSummary(
                    WorkspacesSettingsText.layoutName(model.layout),
                    model.ruleCount,
                ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = ExclusionsSettingsText.INTRO,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExclusionsEmptyState() {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(EXCLUSIONS_EMPTY_TEST_TAG),
        shape = RiffleShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = RiffleElevation.level0,
    ) {
        Column(
            modifier = Modifier.padding(RiffleSpacing.m),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
        ) {
            Text(text = ExclusionsSettingsText.EMPTY_TITLE, style = MaterialTheme.typography.titleSmall)
            Text(
                text = ExclusionsSettingsText.EMPTY_BODY,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExclusionsSections(
    model: ExclusionsSettingsModel,
    callbacks: ExclusionsPageCallbacks,
    openDialog: (ExclusionDialog) -> Unit,
) {
    if (model.isEmpty) return
    val total = model.ruleCount
    val positions = remember(model) { model.rows.withIndex().associate { it.value.id to it.index + 1 } }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= TWO_PANE_MIN_WIDTH_DP.dp) 2 else 1
        Row(horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.l)) {
            model.sections.chunkedBy(columns).forEach { part ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(RiffleSpacing.l),
                ) {
                    part.forEach { section ->
                        ExclusionSection(
                            section = section,
                            positions = positions,
                            total = total,
                            canApplyToOtherLayouts = model.canApplyToOtherLayouts,
                            callbacks = callbacks,
                            openDialog = openDialog,
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun ExclusionSection(
    section: ExclusionRuleSection,
    positions: Map<ExclusionRuleId, Int>,
    total: Int,
    canApplyToOtherLayouts: Boolean,
    callbacks: ExclusionsPageCallbacks,
    openDialog: (ExclusionDialog) -> Unit,
) {
    SettingsSection(title = ExclusionsSettingsText.kindTitle(section.kind)) {
        section.rows.forEach { row ->
            ExclusionRuleListItem(
                row = row,
                position = positions[row.id] ?: 1,
                total = total,
                actions = exclusionRowActions(row, canApplyToOtherLayouts),
                onToggle = { enabled -> callbacks.onAction(ExclusionsSettingsAction.SetEnabled(row.id, enabled)) },
                onRun = { kind -> callbacks.runRowAction(row, kind, openDialog) },
            )
        }
    }
}

/** Runs the chosen row action: turning on or off and applying to all layouts act at once; Delete asks first. */
internal fun ExclusionsPageCallbacks.runRowAction(
    row: ExclusionRuleRow,
    kind: ExclusionRowActionKind,
    openDialog: (ExclusionDialog) -> Unit,
) {
    when (kind) {
        ExclusionRowActionKind.TOGGLE -> onAction(ExclusionsSettingsAction.SetEnabled(row.id, !row.enabled))
        ExclusionRowActionKind.APPLY_TO_ALL -> onAction(ExclusionsSettingsAction.ApplyToAllLayouts(row.id))
        ExclusionRowActionKind.DELETE -> openDialog(ExclusionDialog.ConfirmDelete(row.id))
    }
}

@Composable
private fun ExclusionsAddSection(
    model: ExclusionsSettingsModel,
    openDialog: (ExclusionDialog) -> Unit,
) {
    SettingsSection(title = "Add") {
        if (model.canAdd) {
            SettingsClickableRow(
                modifier = Modifier.testTag(EXCLUSIONS_ADD_TEST_TAG),
                title = ExclusionsSettingsText.ADD_TEXT_RULE,
                subtitle = ExclusionsSettingsText.ADD_TEXT_RULE_BODY,
                onClick = { openDialog(ExclusionDialog.AddText) },
            )
        } else {
            SettingsListRow(
                title = ExclusionsSettingsText.ADD_TEXT_RULE,
                subtitle = ExclusionsAnnouncements.problem(TextRuleProblem.LIMIT_REACHED),
            )
        }
    }
}
