package com.riffle.app.launcher.pool

import com.riffle.core.domain.launcher.workspace.pool.PoolRejection

/** User-visible wording of pool editing in the Workspaces (preview). */
internal object PoolEditText {
    const val EDIT = "Edit"
    const val DONE = "Done"
    const val UNDO = "Undo"
    const val REDO = "Redo"
    const val ADD_APP = "Add app"
    const val ADD_PAGE = "Add page"
    const val REMOVE_PAGE = "Remove empty page"
    const val EDITING_TITLE = "Editing preview home"
    const val NOT_STANDARD_NOTICE = "Edits here change this preview only, not your standard home."
    const val ENTER_EDIT_LONG_PRESS = "Edit preview home"

    const val MOVE_LEFT = "Move left"
    const val MOVE_RIGHT = "Move right"
    const val MOVE_UP = "Move up"
    const val MOVE_DOWN = "Move down"
    const val MOVE_PREVIOUS_PAGE = "Move to previous page"
    const val MOVE_NEXT_PAGE = "Move to next page"
    const val REMOVE = "Remove from this workspace"
    const val DELETE_EVERYWHERE = "Delete everywhere"
    const val SEPARATE_COPY = "Add a separate copy"
    const val DESELECT = "Deselect"
    const val PANEL_HINT = "Tap an item to edit it. Touch and hold, then drag, to move it."

    const val ADD_APP_TITLE = "Add an app"
    const val ADD_APP_EMPTY = "No apps to add."
    const val ADD_APP_CLOSE = "Close"

    const val DELETE_CONFIRM_TITLE = "Delete everywhere?"
    const val DELETE_CONFIRM_ACTION = "Delete everywhere"
    const val CANCEL = "Cancel"

    const val REIMPORT_CONFIRM_TITLE = "Replace preview edits?"
    const val REIMPORT_CONFIRM_BODY =
        "Refresh home items replaces everything in this preview with a fresh copy of your standard home. " +
            "Your edits made in the preview are lost."
    const val REIMPORT_CONFIRM_ACTION = "Replace"

    fun selected(label: String) = "$label selected"

    fun editingItem(label: String) = "Move or remove $label"

    fun moved(label: String) = "Moved $label"

    fun removed(label: String) = "Removed $label from this workspace"

    fun deleted(label: String) = "Deleted $label everywhere"

    fun added(label: String) = "Added $label"

    fun addLabel(label: String) = "Add $label"

    fun copied(label: String) = "Added a separate copy of $label"

    const val PAGE_ADDED = "Added a page"
    const val PAGE_REMOVED = "Removed the empty page"

    fun deleteConfirmBody(
        label: String,
        otherWorkspaces: Int,
    ): String =
        if (otherWorkspaces == 0) {
            "$label will be removed from this preview. You can undo until you tap Done."
        } else {
            "$label will be removed from this preview, including $otherWorkspaces other " +
                (if (otherWorkspaces == 1) "workspace" else "workspaces") +
                " that show it. You can undo until you tap Done."
        }

    fun rejected(reason: PoolRejection): String =
        when (reason) {
            PoolRejection.COLLISION -> "That spot is taken."
            PoolRejection.OUT_OF_BOUNDS -> "That is outside the page."
            PoolRejection.NO_AVAILABLE_CELL -> "No room on that page."
            PoolRejection.UNKNOWN_PAGE -> "There is no page there."
            PoolRejection.ALREADY_IN_ARRANGEMENT -> "Already on this home."
            PoolRejection.WIDGET_ALREADY_PLACED -> "A widget can be placed once. Add a separate copy instead."
            PoolRejection.PROVIDER_UNKNOWN -> "This widget cannot be copied."
            PoolRejection.PAGE_NOT_EMPTY -> "Only an empty page can be removed."
            PoolRejection.LAST_PAGE -> "The last page cannot be removed."
            else -> "That edit is not possible."
        }
}
