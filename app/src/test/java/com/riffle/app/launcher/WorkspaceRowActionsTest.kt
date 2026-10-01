package com.riffle.app.launcher

import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.settings.WorkspaceRow
import com.riffle.core.domain.launcher.workspace.settings.WorkspacesSettingsAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The route table of the Workspaces page: which actions a row offers and what each one does. */
class WorkspaceRowActionsTest {
    private fun row(
        isDefault: Boolean = false,
        preset: String? = null,
        canDelete: Boolean = true,
        up: Boolean = true,
        down: Boolean = true,
    ) = WorkspaceRow(
        id = WorkspaceId("w"),
        name = "Work",
        isActive = false,
        isDefault = isDefault,
        pageCount = 2,
        presetName = preset,
        canDelete = canDelete,
        canMoveUp = up,
        canMoveDown = down,
    )

    private fun List<WorkspaceRowAction>.of(kind: WorkspaceRowActionKind) = first { it.kind == kind }

    @Test
    fun theMenuOrderIsStable() {
        assertEquals(
            listOf(
                WorkspaceRowActionKind.EDIT,
                WorkspaceRowActionKind.RENAME,
                WorkspaceRowActionKind.DUPLICATE,
                WorkspaceRowActionKind.MAKE_DEFAULT,
                WorkspaceRowActionKind.MOVE_UP,
                WorkspaceRowActionKind.MOVE_DOWN,
                WorkspaceRowActionKind.INSTALL_PRESET,
                WorkspaceRowActionKind.DELETE,
            ),
            workspaceRowActions(row(), canEdit = true).map { it.kind },
        )
    }

    @Test
    fun resetNamesTheRecordedPresetAndReplacesInstallPreset() {
        val actions = workspaceRowActions(row(preset = "Nova"), canEdit = true)

        assertEquals("Reset to Nova preset", actions.of(WorkspaceRowActionKind.RESET).label)
        assertTrue(actions.none { it.kind == WorkspaceRowActionKind.INSTALL_PRESET })
    }

    @Test
    fun withoutARecordedPresetResetIsHiddenAndInstallPresetIsOffered() {
        val actions = workspaceRowActions(row(preset = null), canEdit = true)

        assertTrue(actions.none { it.kind == WorkspaceRowActionKind.RESET })
        assertEquals("Install preset...", actions.of(WorkspaceRowActionKind.INSTALL_PRESET).label)
    }

    @Test
    fun deleteIsDisabledOnTheLastWorkspaceWithTheReason() {
        val last = workspaceRowActions(row(canDelete = false), canEdit = true).of(WorkspaceRowActionKind.DELETE)

        assertFalse(last.enabled)
        assertEquals("The last workspace on a layout can't be deleted.", last.disabledReason)
        assertTrue(workspaceRowActions(row(), canEdit = true).of(WorkspaceRowActionKind.DELETE).enabled)
    }

    @Test
    fun editNeedsTheLayoutThisDeviceIsShowing() {
        val other = workspaceRowActions(row(), canEdit = false).of(WorkspaceRowActionKind.EDIT)

        assertFalse(other.enabled)
        assertEquals(WorkspacesSettingsText.EDIT_OTHER_LAYOUT_REASON, other.disabledReason)
        assertNull(workspaceRowActions(row(), canEdit = true).of(WorkspaceRowActionKind.EDIT).disabledReason)
    }

    @Test
    fun moveAndMakeDefaultFollowTheRow() {
        val edge = workspaceRowActions(row(isDefault = true, up = false, down = false), canEdit = true)

        assertFalse(edge.of(WorkspaceRowActionKind.MAKE_DEFAULT).enabled)
        assertFalse(edge.of(WorkspaceRowActionKind.MOVE_UP).enabled)
        assertFalse(edge.of(WorkspaceRowActionKind.MOVE_DOWN).enabled)
    }

    @Test
    fun destructiveActionsOpenAConfirmationAndOthersApplyAtOnce() {
        val applied = mutableListOf<WorkspacesSettingsAction>()
        val edited = mutableListOf<WorkspaceId>()
        val callbacks = WorkspacesPageCallbacks(onAction = { applied += it }, onEdit = { edited += it })
        val opened = mutableListOf<WorkspaceDialog>()
        val workspace = row(preset = "Nova")

        WorkspaceRowActionKind.entries.forEach { kind -> callbacks.runRowAction(workspace, kind) { opened += it } }

        val id = workspace.id
        assertEquals(listOf(id), edited)
        assertEquals(
            listOf(
                WorkspacesSettingsAction.Duplicate(id),
                WorkspacesSettingsAction.MakeDefault(id),
                WorkspacesSettingsAction.Move(id, -1),
                WorkspacesSettingsAction.Move(id, 1),
            ),
            applied,
        )
        assertEquals(
            listOf(
                WorkspaceDialog.Rename(id, "Work"),
                WorkspaceDialog.ConfirmReset(id, "Work", "Nova"),
                WorkspaceDialog.PickPreset,
                WorkspaceDialog.ConfirmDelete(id, "Work"),
            ),
            opened,
        )
    }
}
