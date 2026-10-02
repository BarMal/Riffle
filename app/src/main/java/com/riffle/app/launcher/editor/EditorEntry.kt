package com.riffle.app.launcher.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.containers.ContainerServices
import com.riffle.app.launcher.containers.PageContainerHost
import com.riffle.app.launcher.containers.PageSetContainerHost
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.workspace.LayoutCapabilities
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.editor.LensScope
import com.riffle.core.domain.launcher.workspace.editor.LensScopeChange
import com.riffle.core.domain.launcher.workspace.editor.SourceChoice

internal const val EDITOR_OFFER_TEST_TAG = "editor-adopt-offer"

/** Windows at least this wide (an unfolded foldable, a tablet) show the editor and its preview side by side. */
private val TwoPaneMinWidth: Dp = 600.dp

/** Height of the preview above the steps in the single-pane layout. */
private val CompactPreviewHeight: Dp = 220.dp

/**
 * The workspace editor, ready to host. The workspace menu's `EditWorkspace(id)` effect resolves to this: look
 * up the workspace, build [sources] from the registry (with each source's access, read without prompting), and
 * show this composable full screen. See docs/product/workspaces-editor.md for the integration contract.
 *
 * - [onSave] receives the committed workspace when the user taps Done with changes, with what changed outside it
 *   (the layout's saved lenses and the other workspaces that adopted one); [onClose] follows every exit.
 * - [scope] is the layout's saved lenses and its other workspaces, for "Use a saved lens", Save as lens and the offer
 *   to use it in identical containers; leave it empty for a host without saved lenses.
 * - [onRequestSourceAccess] is called only when the user taps a source's "Review access" button; map it to the
 *   existing explicit flow (for the calendar, `LauncherShellAction.RequestCalendarAccess`). The editor never
 *   requests a permission itself.
 * - [services] supplies live previews: use the real provider in the app, a static one in tests and previews.
 *
 * Edits are a draft until Done; leaving with unsaved changes asks first. Nothing item-shaped is stored.
 */
@Suppress("LongParameterList")
@Composable
fun EditorEntry(
    workspace: Workspace,
    sources: List<SourceChoice>,
    services: ContainerServices,
    onSave: (Workspace, LensScopeChange) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    capabilities: LayoutCapabilities = LayoutCapabilities(),
    onRequestSourceAccess: (SourceId) -> Unit = {},
    scope: LensScope = LensScope(),
) {
    val environment = remember(sources, capabilities) { EditorEnvironment(sources, capabilities) }
    val reducer = remember(environment) { WorkspaceEditorReducer(environment) }
    var state by remember(workspace, scope, reducer) { mutableStateOf(reducer.start(workspace, scope)) }
    val dispatch: (EditorAction) -> Unit = { action ->
        val transition = reducer.reduce(state, action)
        state = transition.state
        when (val effect = transition.effect) {
            is EditorEffect.Save -> {
                onSave(effect.workspace, effect.change)
                onClose()
            }
            EditorEffect.Close -> onClose()
            null -> Unit
        }
    }
    BackHandler { dispatch(EditorAction.Back) }
    EditorScreen(state, environment, services, dispatch, onRequestSourceAccess, modifier)
}

/**
 * The stateless editor screen: renders [state] and forwards every interaction to [dispatch]. Split from
 * [EditorEntry] so screenshot tests can render any step without driving the flow by hand.
 */
@Composable
internal fun EditorScreen(
    state: WorkspaceEditorUiState,
    environment: EditorEnvironment,
    services: ContainerServices,
    dispatch: (EditorAction) -> Unit,
    onRequestSourceAccess: (SourceId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val twoPane = maxWidth >= TwoPaneMinWidth
            val body: @Composable (Modifier) -> Unit = { bodyModifier ->
                EditorBody(state, environment, services, dispatch, onRequestSourceAccess, !twoPane, bodyModifier)
            }
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = RiffleSpacing.l)) {
                EditorTopBar(state, dispatch)
                state.message?.let {
                    EditorNotice(
                        messageText(it),
                        onDismiss = { dispatch(EditorAction.DismissMessage) },
                        error = messageIsError(it),
                    )
                }
                state.offer?.let {
                    EditorOfferNotice(
                        text = EditorLensMessages.offer(it),
                        actionLabel = EditorLensText.OFFER_ACTION,
                        onAction = { dispatch(EditorAction.AdoptIdentical) },
                        onDismiss = { dispatch(EditorAction.DismissOffer) },
                        modifier = Modifier.testTag(EDITOR_OFFER_TEST_TAG),
                    )
                }
                if (twoPane) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.l),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        body(Modifier.weight(1f))
                        EditorPreviewPane(state, environment, services, Modifier.weight(1f).fillMaxSize())
                    }
                } else {
                    body(Modifier.fillMaxSize())
                }
            }
        }
    }
    if (state.confirmingDiscard) DiscardDialog(dispatch)
}

/** The left or only pane: the workspace overview, or the running flow (with a preview above it when compact). */
@Composable
private fun EditorBody(
    state: WorkspaceEditorUiState,
    environment: EditorEnvironment,
    services: ContainerServices,
    dispatch: (EditorAction) -> Unit,
    onRequestSourceAccess: (SourceId) -> Unit,
    inlinePreview: Boolean,
    modifier: Modifier,
) {
    val flow = state.flow
    if (flow == null) {
        EditorOverview(state.session.draft, state.session.scope.library, dispatch, modifier)
    } else {
        val context = environment.flowContext(state.session, flow.mode)
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(RiffleSpacing.m)) {
            if (inlinePreview) {
                EditorPreview(
                    previewTargetFor(flow.state, context),
                    services,
                    Modifier.fillMaxWidth().height(CompactPreviewHeight),
                )
            }
            EditorFlowPane(
                flow = flow,
                context = context,
                onAction = dispatch,
                onRequestSourceAccess = onRequestSourceAccess,
                reducedMotion = services.environment.reducedMotion,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The right pane of the two-pane layout: the flow's live preview, or the workspace's first page. */
@Composable
private fun EditorPreviewPane(
    state: WorkspaceEditorUiState,
    environment: EditorEnvironment,
    services: ContainerServices,
    modifier: Modifier,
) {
    val flow = state.flow
    if (flow != null) {
        val context = environment.flowContext(state.session, flow.mode)
        EditorPreview(previewTargetFor(flow.state, context), services, modifier)
    } else {
        when (val page = state.session.draft.pages.firstOrNull()) {
            is PageContainer -> PageContainerHost(page, services, modifier)
            is PageSetContainer -> PageSetContainerHost(page, services, modifier)
            null -> Text(EditorText.EMPTY_WORKSPACE, modifier = modifier)
        }
    }
}

@Composable
private fun EditorTopBar(
    state: WorkspaceEditorUiState,
    dispatch: (EditorAction) -> Unit,
) {
    val session = state.session
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
        ) {
            IconButton(onClick = { dispatch(EditorAction.RequestClose) }) {
                Icon(Icons.Filled.Close, contentDescription = EditorText.CLOSE)
            }
            Text(EditorText.TITLE, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = { dispatch(EditorAction.Save) }) { Text(EditorText.DONE) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.xs)) {
            TextButton(onClick = { dispatch(EditorAction.Undo) }, enabled = session.canUndo) { Text(EditorText.UNDO) }
            TextButton(onClick = { dispatch(EditorAction.Redo) }, enabled = session.canRedo) { Text(EditorText.REDO) }
            TextButton(onClick = { dispatch(EditorAction.RevertChanges) }, enabled = session.isDirty) {
                Text(EditorText.REVERT)
            }
        }
    }
}

@Composable
private fun DiscardDialog(dispatch: (EditorAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { dispatch(EditorAction.KeepEditing) },
        title = { Text(EditorText.DISCARD_TITLE) },
        text = { Text(EditorText.DISCARD_BODY) },
        confirmButton = {
            TextButton(
                onClick = { dispatch(EditorAction.DiscardAndClose) },
            ) { Text(EditorText.DISCARD) }
        },
        dismissButton = {
            TextButton(
                onClick = { dispatch(EditorAction.KeepEditing) },
            ) { Text(EditorText.KEEP_EDITING) }
        },
    )
}
