package com.riffle.app.launcher.pool

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.AppIconLoader
import com.riffle.app.launcher.LauncherAppIcon
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool
import com.riffle.core.domain.launcher.workspace.pool.PoolApp
import com.riffle.core.domain.launcher.workspace.pool.PoolHomeEditing
import com.riffle.core.domain.launcher.workspace.pool.PoolHomeTarget
import com.riffle.core.domain.launcher.workspace.pool.PoolReferences

internal const val POOL_EDIT_ADD_DIALOG_TEST_TAG = "workspace-preview-pool-edit-add-dialog"
internal const val POOL_EDIT_DELETE_DIALOG_TEST_TAG = "workspace-preview-pool-edit-delete-dialog"
internal const val POOL_EDIT_REIMPORT_DIALOG_TEST_TAG = "workspace-preview-pool-edit-reimport-dialog"

private val ActionHeight = 48.dp
private val AddAppIcon = 40.dp

@Composable
internal fun AddAppDialog(
    edit: PoolEditUi,
    iconLoader: AppIconLoader,
    onClose: () -> Unit,
) {
    val pool = edit.pool
    val workspace = edit.workspaceId
    val shown = remember(pool, workspace) { identitiesShown(pool, workspace) }
    val candidates =
        remember(edit.installedApps, shown) {
            edit.installedApps
                .filter { it.enabled && it.visibility == AppVisibility.VISIBLE && it.identity !in shown }
                .distinctBy { it.identity }
                .sortedBy { it.label.lowercase() }
        }
    val preferredPage = pool?.arrangements?.get(workspace)?.pages?.firstOrNull()?.id
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.testTag(POOL_EDIT_ADD_DIALOG_TEST_TAG),
        title = { Text(PoolEditText.ADD_APP_TITLE) },
        text = {
            if (candidates.isEmpty()) {
                Text(PoolEditText.ADD_APP_EMPTY)
            } else {
                LazyColumn {
                    items(candidates, key = { it.identity.toString() }) { app ->
                        AddAppRow(app, iconLoader) {
                            if (workspace != null && preferredPage != null) {
                                edit.controller.addApp(
                                    PoolHomeTarget(workspace, preferredPage),
                                    app.identity,
                                    app.label,
                                )
                            }
                            onClose()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(PoolEditText.ADD_APP_CLOSE) } },
    )
}

@Composable
private fun AddAppRow(
    app: InstalledApp,
    iconLoader: AppIconLoader,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ActionHeight)
                .clickable(onClickLabel = PoolEditText.addLabel(app.label), role = Role.Button, onClick = onClick)
                .padding(vertical = RiffleSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
    ) {
        LauncherAppIcon(app.identity, app.label, iconLoader, Modifier.size(AddAppIcon))
        Text(app.label, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun identitiesShown(
    pool: PlacedItemPool?,
    workspace: WorkspaceId?,
): Set<AppIdentity> {
    val arrangement = pool?.arrangements?.get(workspace) ?: return emptySet()
    return PoolReferences.referencedIn(arrangement)
        .mapNotNull { (pool.items[it] as? PoolApp)?.takeIf { app -> app.appShortcutId == null }?.appIdentity }
        .toSet()
}

@Composable
internal fun PoolEditDialogs(
    edit: PoolEditUi,
    state: PoolEditState,
) {
    val controller = edit.controller
    val pool = edit.pool
    val request = state.deleteRequest
    val item = request?.let { pool?.items?.get(it) }
    if (request != null && item != null && pool != null) {
        val impact = edit.workspaceId?.let { PoolHomeEditing.everywhereImpact(pool, it, request) }
        AlertDialog(
            onDismissRequest = controller::dismissDelete,
            modifier = Modifier.testTag(POOL_EDIT_DELETE_DIALOG_TEST_TAG),
            title = { Text(PoolEditText.DELETE_CONFIRM_TITLE) },
            text = { Text(PoolEditText.deleteConfirmBody(item.label, impact?.otherWorkspaces?.size ?: 0)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        controller.dismissDelete()
                        controller.deleteEverywhere(request, item.label)
                    },
                ) { Text(PoolEditText.DELETE_CONFIRM_ACTION) }
            },
            dismissButton = { TextButton(onClick = controller::dismissDelete) { Text(PoolEditText.CANCEL) } },
        )
    }
    if (state.reimportRequested) {
        AlertDialog(
            onDismissRequest = controller::dismissReimport,
            modifier = Modifier.testTag(POOL_EDIT_REIMPORT_DIALOG_TEST_TAG),
            title = { Text(PoolEditText.REIMPORT_CONFIRM_TITLE) },
            text = { Text(PoolEditText.REIMPORT_CONFIRM_BODY) },
            confirmButton = {
                TextButton(
                    onClick = {
                        controller.dismissReimport()
                        controller.exit()
                        edit.onReimportConfirmed()
                    },
                ) { Text(PoolEditText.REIMPORT_CONFIRM_ACTION) }
            },
            dismissButton = { TextButton(onClick = controller::dismissReimport) { Text(PoolEditText.CANCEL) } },
        )
    }
}
