package com.riffle.app.launcher.pool

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.riffle.app.launcher.AppIconLoader
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool
import com.riffle.core.domain.launcher.workspace.pool.PoolHomeEditing
import com.riffle.core.domain.launcher.workspace.pool.PoolHomeTarget
import com.riffle.core.domain.launcher.workspace.pool.PoolItem
import com.riffle.core.domain.launcher.workspace.pool.PoolItemId
import com.riffle.core.domain.launcher.workspace.pool.PoolWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal const val POOL_EDIT_BAR_TEST_TAG = "workspace-preview-pool-edit-bar"
internal const val POOL_EDIT_DONE_TEST_TAG = "workspace-preview-pool-edit-done"
internal const val POOL_EDIT_ENTER_TEST_TAG = "workspace-preview-pool-edit-enter"
internal const val POOL_EDIT_UNDO_TEST_TAG = "workspace-preview-pool-edit-undo"
internal const val POOL_EDIT_REDO_TEST_TAG = "workspace-preview-pool-edit-redo"
internal const val POOL_EDIT_ADD_APP_TEST_TAG = "workspace-preview-pool-edit-add-app"
internal const val POOL_EDIT_ADD_PAGE_TEST_TAG = "workspace-preview-pool-edit-add-page"
internal const val POOL_EDIT_PANEL_TEST_TAG = "workspace-preview-pool-edit-panel"
internal const val POOL_EDIT_NOTICE_TEST_TAG = "workspace-preview-pool-edit-notice"

private val ActionHeight = 48.dp

/** Space the edit bar and the item panel take, so the page's grid is laid out between them (nothing under them). */
internal val PoolEditTopChrome = 76.dp
internal val PoolEditBottomChrome = 84.dp

/**
 * What the preview's home pages need to edit the pool: the [controller], the pool [pool] as drawn right now (a
 * snapshot, rebuilt whenever the repository publishes), the arrangement being edited, the installed apps to
 * offer in "Add app", and the provider of the standard home's host ids. Built only when the preview has a pool.
 */
internal class PoolEditUi(
    val controller: PoolEditController,
    val deviceClass: HomeLayoutDeviceClass,
    val pool: PlacedItemPool?,
    val workspaceId: WorkspaceId?,
    val installedApps: List<InstalledApp>,
    val standardHostIds: () -> Set<HostedWidgetId>,
    val onReimportConfirmed: () -> Unit,
) {
    /** Starts edit mode (no-op when there is nothing to edit or it is already on). */
    fun enter(): Boolean = controller.enter(deviceClass, standardHostIds)

    fun requestReimport() = controller.requestReimport()
}

/** A stand-in state for a host without editing, so composables can collect unconditionally. */
internal val NoPoolEditState: StateFlow<PoolEditState> = MutableStateFlow(PoolEditState())

/**
 * The chrome of pool editing, drawn over the preview: the edit bar (Done, Undo, Redo, Add app, Add page, the "does
 * not change your standard home" notice), the selected item's action panel, the snackbar and the dialogs. It draws
 * nothing and registers no Back handler outside edit mode, so with editing off the preview is exactly as before.
 *
 * Every action here is a button (nothing is drag-only), 48dp tall, with plain text labels. Nothing animates, so
 * reduced motion needs no special case.
 */
@Composable
internal fun PoolEditOverlay(
    edit: PoolEditUi,
    iconLoader: AppIconLoader,
    topInset: Dp,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val controller = edit.controller
    val state by controller.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    EditLifecycleEffects(edit, state.editing)
    NoticeEffect(controller, state.notice, snackbar)
    LaunchedEffect(state.editing) { if (!state.editing) snackbar.currentSnackbarData?.dismiss() }
    Box(modifier = modifier.fillMaxSize()) {
        if (state.editing) {
            BackHandler { controller.exit() }
            Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = topInset)) {
                PoolEditBar(edit, state, iconLoader)
            }
            val selected = state.selected?.let { id -> edit.pool?.items?.get(id) }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset),
                verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
            ) {
                SnackbarHost(snackbar)
                PoolItemPanel(edit, selected)
            }
        } else {
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset))
        }
        PoolEditDialogs(edit, state)
    }
}

/** Leaves edit mode when the device class changes or the preview closes; writes pending edits when the app stops. */
@Composable
private fun EditLifecycleEffects(
    edit: PoolEditUi,
    editing: Boolean,
) {
    val controller = edit.controller
    LaunchedEffect(edit.deviceClass, editing) {
        if (editing && controller.editingDeviceClass != edit.deviceClass) controller.exit()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer =
            LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) controller.flushNow() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.exit()
        }
    }
}

@Composable
private fun NoticeEffect(
    controller: PoolEditController,
    notice: PoolEditNotice?,
    snackbar: SnackbarHostState,
) {
    LaunchedEffect(notice) {
        if (notice != null) {
            val result =
                snackbar.showSnackbar(
                    message = notice.text,
                    actionLabel = if (notice.undoable) PoolEditText.UNDO else null,
                    duration = SnackbarDuration.Short,
                )
            if (result == SnackbarResult.ActionPerformed) controller.undo()
            controller.noticeShown(notice)
        }
    }
}

@Composable
private fun PoolEditBar(
    edit: PoolEditUi,
    state: PoolEditState,
    iconLoader: AppIconLoader,
) {
    val controller = edit.controller
    Surface(
        modifier = Modifier.fillMaxWidth().height(PoolEditTopChrome).testTag(POOL_EDIT_BAR_TEST_TAG),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = RiffleSpacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = PoolEditText.EDITING_TITLE,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(end = RiffleSpacing.s).semantics { heading() },
                    )
                    ActionButton(PoolEditText.UNDO, POOL_EDIT_UNDO_TEST_TAG, enabled = state.canUndo) {
                        controller.undo()
                    }
                    ActionButton(PoolEditText.REDO, POOL_EDIT_REDO_TEST_TAG, enabled = state.canRedo) {
                        controller.redo()
                    }
                    AddAppButton(edit, iconLoader)
                    ActionButton(
                        PoolEditText.ADD_PAGE,
                        POOL_EDIT_ADD_PAGE_TEST_TAG,
                        enabled = edit.workspaceId != null,
                    ) { edit.workspaceId?.let(controller::addPage) }
                }
                // Done stays on screen however narrow the window is.
                ActionButton(PoolEditText.DONE, POOL_EDIT_DONE_TEST_TAG) { controller.exit() }
            }
            Text(
                text = PoolEditText.NOT_STANDARD_NOTICE,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(POOL_EDIT_NOTICE_TEST_TAG),
            )
        }
    }
}

@Composable
private fun AddAppButton(
    edit: PoolEditUi,
    iconLoader: AppIconLoader,
) {
    var open by remember { mutableStateOf(false) }
    ActionButton(PoolEditText.ADD_APP, POOL_EDIT_ADD_APP_TEST_TAG, enabled = edit.workspaceId != null) { open = true }
    if (open) {
        AddAppDialog(edit, iconLoader, onClose = { open = false })
    }
}

@Composable
private fun ActionButton(
    text: String,
    tag: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = ActionHeight).testTag(tag),
    ) {
        Text(text)
    }
}

/** The selected item's actions, or a hint. Moves, page changes, remove and delete are all plain buttons. */
@Composable
private fun PoolItemPanel(
    edit: PoolEditUi,
    selected: PoolItem?,
) {
    val controller = edit.controller
    val workspace = edit.workspaceId
    val pool = edit.pool
    val site = if (workspace == null || pool == null || selected == null) null else siteOf(pool, workspace, selected.id)
    val target = if (workspace == null || site == null) null else PoolHomeTarget(workspace, site.page.id)
    Surface(
        modifier = Modifier.fillMaxWidth().height(PoolEditBottomChrome).testTag(POOL_EDIT_PANEL_TEST_TAG),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(horizontal = RiffleSpacing.m)) {
            Text(
                text = if (selected == null) PoolEditText.PANEL_HINT else PoolEditText.editingItem(selected.label),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier.padding(top = RiffleSpacing.xs).semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
            )
            if (selected != null && target != null) {
                val id = selected.id
                val label = selected.label
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    PanelButton(PoolEditText.MOVE_LEFT) { controller.move(target, id, label, -1, 0) }
                    PanelButton(PoolEditText.MOVE_RIGHT) { controller.move(target, id, label, 1, 0) }
                    PanelButton(PoolEditText.MOVE_UP) { controller.move(target, id, label, 0, -1) }
                    PanelButton(PoolEditText.MOVE_DOWN) { controller.move(target, id, label, 0, 1) }
                    PanelButton(PoolEditText.MOVE_PREVIOUS_PAGE) { controller.moveToPage(target, id, label, -1) }
                    PanelButton(PoolEditText.MOVE_NEXT_PAGE) { controller.moveToPage(target, id, label, 1) }
                    if ((selected as? PoolWidget)?.provider != null) {
                        PanelButton(PoolEditText.SEPARATE_COPY) { controller.separateCopy(target, id, label) }
                    }
                    PanelButton(PoolEditText.REMOVE) { controller.remove(target, id, label) }
                    PanelButton(PoolEditText.DELETE_EVERYWHERE) { controller.requestDelete(id) }
                    PanelButton(PoolEditText.DESELECT) { controller.select(null) }
                }
            }
        }
    }
}

private fun siteOf(
    pool: PlacedItemPool,
    workspace: WorkspaceId,
    id: PoolItemId,
) = PoolHomeEditing.siteOf(pool, workspace, id)

@Composable
private fun PanelButton(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = ActionHeight)) { Text(text) }
}
