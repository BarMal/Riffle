package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.settings.WorkspaceRow
import com.riffle.core.domain.launcher.workspace.settings.WorkspacesSettingsAction

internal enum class WorkspaceRowActionKind {
    EDIT,
    RENAME,
    DUPLICATE,
    MAKE_DEFAULT,
    MOVE_UP,
    MOVE_DOWN,
    RESET,
    INSTALL_PRESET,
    DELETE,
}

/**
 * One action on a workspace row. A disabled action stays listed with its [disabledReason], so people learn why
 * something is unavailable instead of wondering where it went.
 */
internal data class WorkspaceRowAction(
    val kind: WorkspaceRowActionKind,
    val label: String,
    val enabled: Boolean = true,
    val disabledReason: String? = null,
)

/**
 * The actions a workspace row offers, in menu order. Reset to preset needs the preset the workspace came from:
 * without it that slot offers "Install preset..." instead (hidden, never guessed). Edit needs the layout this
 * device is showing; Delete is never enabled on the last workspace.
 */
internal fun workspaceRowActions(
    row: WorkspaceRow,
    canEdit: Boolean,
): List<WorkspaceRowAction> =
    listOf(
        WorkspaceRowAction(
            WorkspaceRowActionKind.EDIT,
            WorkspacesSettingsText.EDIT,
            enabled = canEdit,
            disabledReason = WorkspacesSettingsText.EDIT_OTHER_LAYOUT_REASON.takeUnless { canEdit },
        ),
        WorkspaceRowAction(WorkspaceRowActionKind.RENAME, WorkspacesSettingsText.RENAME),
        WorkspaceRowAction(WorkspaceRowActionKind.DUPLICATE, WorkspacesSettingsText.DUPLICATE),
        WorkspaceRowAction(WorkspaceRowActionKind.MAKE_DEFAULT, WorkspacesSettingsText.MAKE_DEFAULT, !row.isDefault),
        WorkspaceRowAction(WorkspaceRowActionKind.MOVE_UP, WorkspacesSettingsText.MOVE_UP, row.canMoveUp),
        WorkspaceRowAction(WorkspaceRowActionKind.MOVE_DOWN, WorkspacesSettingsText.MOVE_DOWN, row.canMoveDown),
        row.presetName
            ?.let { WorkspaceRowAction(WorkspaceRowActionKind.RESET, WorkspacesSettingsText.resetLabel(it)) }
            ?: WorkspaceRowAction(WorkspaceRowActionKind.INSTALL_PRESET, WorkspacesSettingsText.INSTALL_PRESET),
        WorkspaceRowAction(
            WorkspaceRowActionKind.DELETE,
            WorkspacesSettingsText.DELETE,
            enabled = row.canDelete,
            disabledReason = WorkspacesDialogText.ONLY_ONE_REASON.takeUnless { row.canDelete },
        ),
    )

/** The dialogs the Workspaces page can have open. Every destructive action asks first. */
internal sealed interface WorkspaceDialog {
    data class Rename(val id: WorkspaceId, val name: String) : WorkspaceDialog

    data class ConfirmDelete(val id: WorkspaceId, val name: String) : WorkspaceDialog

    data class ConfirmReset(val id: WorkspaceId, val name: String, val presetName: String) : WorkspaceDialog

    data object PickPreset : WorkspaceDialog

    data object PickCopySource : WorkspaceDialog
}

/**
 * Runs the chosen row action: non-destructive ones apply at once; Rename, Reset, Delete and the preset picker
 * open their dialog (Reset and Delete confirm and offer Undo); Edit goes to the existing editor route.
 */
internal fun WorkspacesPageCallbacks.runRowAction(
    row: WorkspaceRow,
    kind: WorkspaceRowActionKind,
    openDialog: (WorkspaceDialog) -> Unit,
) {
    val id = row.id
    when (kind) {
        WorkspaceRowActionKind.EDIT -> onEdit(id)
        WorkspaceRowActionKind.RENAME -> openDialog(WorkspaceDialog.Rename(id, row.name))
        WorkspaceRowActionKind.DUPLICATE -> onAction(WorkspacesSettingsAction.Duplicate(id))
        WorkspaceRowActionKind.MAKE_DEFAULT -> onAction(WorkspacesSettingsAction.MakeDefault(id))
        WorkspaceRowActionKind.MOVE_UP -> onAction(WorkspacesSettingsAction.Move(id, -1))
        WorkspaceRowActionKind.MOVE_DOWN -> onAction(WorkspacesSettingsAction.Move(id, 1))
        WorkspaceRowActionKind.RESET ->
            openDialog(WorkspaceDialog.ConfirmReset(id, row.name, row.presetName.orEmpty()))
        WorkspaceRowActionKind.INSTALL_PRESET -> openDialog(WorkspaceDialog.PickPreset)
        WorkspaceRowActionKind.DELETE -> openDialog(WorkspaceDialog.ConfirmDelete(id, row.name))
    }
}

/** What the Workspaces page can ask for; the stateful wrapper wires them to the controller and shell. */
internal data class WorkspacesPageCallbacks(
    val onAction: (WorkspacesSettingsAction) -> Unit = {},
    val onEdit: (WorkspaceId) -> Unit = {},
    val onSelectLayout: (HomeLayoutDeviceClass) -> Unit = {},
)
