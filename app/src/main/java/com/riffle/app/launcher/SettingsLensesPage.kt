package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
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
import com.riffle.app.launcher.containers.ContainerServices
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.workspace.LensesSettingsController
import com.riffle.app.launcher.workspace.SourcesSettingsController
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LibraryProblem
import com.riffle.core.domain.launcher.workspace.SavedLens
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.editor.LensDraftAction
import com.riffle.core.domain.launcher.workspace.settings.LensCopyTarget
import com.riffle.core.domain.launcher.workspace.settings.LensDetailModel
import com.riffle.core.domain.launcher.workspace.settings.LensRow
import com.riffle.core.domain.launcher.workspace.settings.LensesSettingsAction
import com.riffle.core.domain.launcher.workspace.settings.LensesSettingsModel

internal const val LENSES_NEW_TEST_TAG = "lenses-new"
internal const val LENSES_EMPTY_TEST_TAG = "lenses-empty"
internal const val LENSES_HEADING_TEST_TAG = "lenses-heading"
internal const val LENSES_DETAIL_PLACEHOLDER_TEST_TAG = "lenses-detail-placeholder"

/** What the page asks the host to do. A plain bundle of the page's events so the stateless page needs no controller. */
internal class LensesPageCallbacks(
    val onSelectLayout: (HomeLayoutDeviceClass) -> Unit = {},
    val onOpen: (LensId) -> Unit = {},
    val onNew: () -> Unit = {},
    val onBuilderAction: (LensDraftAction) -> Unit = {},
    val onDispatch: (LensesSettingsAction) -> Unit = {},
    val onCloseDetail: () -> Unit = {},
    val onEditWorkspace: (WorkspaceId) -> Unit = {},
    val onRequestSourceAccess: (SourceId) -> Unit = {},
    val queries: LensesPageQueries = LensesPageQueries(),
)

/** What the page reads while it is drawn: pure answers from the domain's planners over the stored workspaces. */
internal class LensesPageQueries(
    val detailFor: (String) -> LensDetailModel? = { null },
    val copyNameFor: (String) -> String = { it.trim() },
    val nameProblem: (LensId?, String) -> LibraryProblem? = { _, _ -> null },
    val replacements: (LensId) -> List<SavedLens> = { emptyList() },
    val replacementImpact: (LensId, LensId) -> Int = { _, _ -> 0 },
    val copyTargets: (LensId) -> List<LensCopyTarget> = { emptyList() },
)

/** Settings > Saved lenses. Reachable only from the Developer section while Workspaces (preview) is on. */
@Composable
internal fun SettingsLensesPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    val host = LocalWorkspaceSettingsHost.current
    val lenses = host?.lenses
    if (host == null || lenses == null) {
        SettingsPreviewOffNote()
    } else {
        HostedLensesPage(host = host, lenses = lenses, state = state, onAction = onAction)
    }
}

@Composable
private fun HostedLensesPage(
    host: WorkspaceSettingsHost,
    lenses: LensesSettingsController,
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    val version by host.version.collectAsState()
    val session by lenses.builder.session.collectAsState()
    val statuses by host.sources.statuses.collectAsState()
    val disabled by host.sources.disabled.collectAsState()
    val viewed = state.selectedLayoutDeviceClass
    val tabs =
        remember(state.availableLayoutDeviceClasses) { settingsLayoutDeviceTabs(state.availableLayoutDeviceClasses) }
    val others = remember(tabs, viewed) { tabs.map { it.deviceClass }.filter { it != viewed } }
    LensesFeedbackEffect(lenses)
    SourceStatusEffect(sources = host.sources, active = session != null)
    // A lens being built belongs to the layout it was opened on; switching the tab leaves it.
    LaunchedEffect(viewed) {
        val open = lenses.builder.layout
        if (open != null && open != viewed) lenses.builder.close()
    }
    val model = remember(lenses, version, viewed) { lenses.queries.model(viewed) }
    val choices = remember(lenses, statuses, disabled) { lenses.queries.sourceChoices(statuses, disabled) }
    val builder =
        session?.let { open ->
            LensBuilderData(
                session = open,
                sources = choices,
                layout = viewed,
                canEditWorkspaces = viewed == host.currentLayout,
            )
        }
    val callbacks =
        LensesPageCallbacks(
            onSelectLayout = { layout -> onAction(LauncherShellAction.SelectSettingsLayoutDeviceClass(layout)) },
            onOpen = { id -> lenses.builder.open(viewed, id) },
            onNew = { lenses.builder.startNew(viewed) },
            onBuilderAction = { action -> lenses.builder.edit(action, choices) },
            onDispatch = { action -> lenses.dispatch(viewed, action) },
            onCloseDetail = lenses.builder::close,
            onEditWorkspace = host.onEdit,
            onRequestSourceAccess = host.onRequestSourceAccess,
            queries =
                LensesPageQueries(
                    detailFor = { name -> lenses.queries.detail(viewed, session, name) },
                    copyNameFor = { name -> lenses.queries.copyName(viewed, session, name) },
                    nameProblem = { id, name -> lenses.queries.nameProblem(viewed, id, name) },
                    replacements = { id -> lenses.queries.replacements(viewed, id) },
                    replacementImpact = { id, other -> lenses.queries.replacementImpact(viewed, id, other) },
                    copyTargets = { id -> lenses.queries.copyTargets(viewed, id, others) },
                ),
        )
    val reducedMotion = state.settings.motion.reducedMotion
    val services = remember(host, reducedMotion) { host.previewServices?.invoke(reducedMotion) }
    LensesSettingsContent(
        model = model,
        viewed = viewed,
        tabs = tabs,
        builder = builder,
        services = services,
        callbacks = callbacks,
    )
}

/**
 * Reads each source's status (Settings > Sources' own monitor, one subscription per source) only while a lens is
 * being built, and releases every one of them when the builder closes or the page does. Statuses only, never items.
 */
@Composable
private fun SourceStatusEffect(
    sources: SourcesSettingsController,
    active: Boolean,
) {
    DisposableEffect(sources, active) {
        if (active) sources.open()
        onDispose { if (active) sources.close() }
    }
}

/**
 * Announces each action's outcome in the Settings snackbar (read out by TalkBack), with Undo for destructive ones.
 * Leaving the page drops whatever is pending so a stale Undo can never reappear.
 */
@Composable
private fun LensesFeedbackEffect(controller: LensesSettingsController) {
    val snackbar = LocalSettingsSnackbarHostState.current
    val feedback by controller.feedback.collectAsState()
    LaunchedEffect(feedback?.id) {
        val current = feedback
        if (current != null && snackbar != null) {
            val result =
                snackbar.showSnackbar(
                    message = current.message,
                    actionLabel = if (current.canUndo) LensesSettingsText.UNDO else null,
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
 * The page itself, with no controller: [model] is null until the workspaces have loaded, [builder] is the lens being
 * built (null while the list shows). Compact widths show the list or the builder; at 600 dp and up (an unfolded
 * foldable, a tablet) it is list-detail, the list on the left and the builder on the right, keeping the open lens
 * by id across rotation and folding.
 */
@Composable
internal fun LensesSettingsContent(
    model: LensesSettingsModel?,
    viewed: HomeLayoutDeviceClass,
    tabs: List<SettingsLayoutDeviceTab>,
    builder: LensBuilderData?,
    services: ContainerServices?,
    callbacks: LensesPageCallbacks,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf<LensDialog?>(null) }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val wide = maxWidth >= TWO_PANE_MIN_WIDTH_DP.dp
        val tabRow: @Composable () -> Unit = {
            WorkspacesLayoutTabs(tabs = tabs, selected = viewed, onSelect = callbacks.onSelectLayout)
        }
        val detail: @Composable (Boolean) -> Unit = { showBack ->
            if (builder != null) {
                LensDetailPane(builder, services, callbacks, showBack, { dialog = it })
            }
        }
        when {
            model == null -> LensesNotLoaded(tabRow)
            wide -> LensesTwoPane(model, viewed, builder, tabs, callbacks, tabRow, detail) { dialog = it }
            builder != null -> detail(true)
            else ->
                Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.l)) {
                    tabRow()
                    LensListSection(model, viewed, null, tabs, callbacks) { dialog = it }
                }
        }
    }
    dialog?.let { open -> LensesDialogs(dialog = open, callbacks = callbacks, onDismiss = { dialog = null }) }
}

@Composable
private fun LensesNotLoaded(tabRow: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.l)) {
        tabRow()
        Text(
            modifier = Modifier.padding(horizontal = RiffleSpacing.s),
            text = LensesSettingsText.NOT_LOADED,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun LensesTwoPane(
    model: LensesSettingsModel,
    viewed: HomeLayoutDeviceClass,
    builder: LensBuilderData?,
    tabs: List<SettingsLayoutDeviceTab>,
    callbacks: LensesPageCallbacks,
    tabRow: @Composable () -> Unit,
    detail: @Composable (Boolean) -> Unit,
    openDialog: (LensDialog) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.l)) {
        tabRow()
        Row(horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.l)) {
            Column(modifier = Modifier.weight(1f)) {
                LensListSection(model, viewed, builder?.session?.id, tabs, callbacks, openDialog)
            }
            Column(modifier = Modifier.weight(1f)) {
                if (builder == null) {
                    Text(
                        modifier = Modifier.padding(RiffleSpacing.l).testTag(LENSES_DETAIL_PLACEHOLDER_TEST_TAG),
                        text = LensesSettingsText.DETAIL_PLACEHOLDER,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    detail(false)
                }
            }
        }
    }
}

/**
 * The layout's lenses by name, with the heading that says which layout and how many (a polite live region), the
 * explanation, and + New (disabled with the reason when the layout is full). [tabs] are the layouts there are: every
 * other one is a "Copy to" target of each row. [openId] marks the row whose lens is open.
 */
@Composable
private fun LensListSection(
    model: LensesSettingsModel,
    viewed: HomeLayoutDeviceClass,
    openId: LensId?,
    tabs: List<SettingsLayoutDeviceTab>,
    callbacks: LensesPageCallbacks,
    openDialog: (LensDialog) -> Unit,
) {
    val copyLayouts = tabs.map { it.deviceClass }.filter { it != viewed }
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
        LensesHeading(model)
        NewLensButton(model, callbacks)
        if (model.rows.isEmpty()) {
            Text(
                modifier = Modifier.padding(horizontal = RiffleSpacing.s).testTag(LENSES_EMPTY_TEST_TAG),
                text = LensesSettingsText.EMPTY,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            SettingsSection(title = LensesSettingsText.LIST_HEADING) {
                model.rows.forEachIndexed { index, row ->
                    LensListItem(
                        row = row,
                        position = index + 1,
                        total = model.rows.size,
                        actions = lensRowActions(model.isFull, copyLayouts),
                        highlighted = row.id == openId,
                        onClick = { callbacks.onOpen(row.id) },
                        onRun = { action -> runLensRowAction(row, action, callbacks, openDialog) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LensesHeading(model: LensesSettingsModel) {
    Column(
        modifier = Modifier.padding(horizontal = RiffleSpacing.s),
        verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
    ) {
        Text(
            modifier =
                Modifier
                    .testTag(LENSES_HEADING_TEST_TAG)
                    .semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
            text = LensesSettingsText.listHeading(model.layout, model.rows.size),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = LensesSettingsText.EXPLAINER,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NewLensButton(
    model: LensesSettingsModel,
    callbacks: LensesPageCallbacks,
) {
    Column(
        modifier = Modifier.padding(horizontal = RiffleSpacing.s),
        verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xxs),
    ) {
        Button(
            onClick = callbacks.onNew,
            enabled = !model.isFull,
            modifier = Modifier.heightIn(min = RiffleSpacing.xxxl).testTag(LENSES_NEW_TEST_TAG),
        ) { Text("+ ${LensesSettingsText.NEW}") }
        if (model.isFull) {
            Text(
                text = LensesSettingsText.FULL_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** What each overflow-menu or TalkBack action on a lens row does. Delete and Rename only open their dialogs. */
private fun runLensRowAction(
    row: LensRow,
    action: LensRowAction,
    callbacks: LensesPageCallbacks,
    openDialog: (LensDialog) -> Unit,
) {
    when (action.kind) {
        LensRowActionKind.RENAME -> openDialog(LensDialog.Rename(row.id, row.name))
        LensRowActionKind.DUPLICATE -> callbacks.onDispatch(LensesSettingsAction.Duplicate(row.id))
        LensRowActionKind.COPY ->
            callbacks.queries.copyTargets(row.id).firstOrNull { it.layout == action.layout }?.let { target ->
                openDialog(LensDialog.CopyTo(row.id, row.name, target))
            }
        LensRowActionKind.DELETE -> openDialog(LensDialog.ConfirmDelete(row.id, row.name, row.usedIn))
    }
}
