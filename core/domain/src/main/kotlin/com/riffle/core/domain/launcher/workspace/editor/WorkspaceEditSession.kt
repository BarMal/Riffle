package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceIssue

/** The session after an edit, and what the edit did. A rejected edit leaves [session] untouched. */
data class SessionStep(
    val session: WorkspaceEditSession,
    val result: EditResult,
)

sealed interface CommitResult {
    data class Committed(val session: WorkspaceEditSession) : CommitResult

    /** The draft still has problems the committed workspace did not; nothing was committed. */
    data class Rejected(val issues: List<WorkspaceIssue>) : CommitResult
}

/**
 * Draft versus committed state for one workspace edit, with undo and redo. Immutable: each operation
 * returns the next session. Edits go through [WorkspaceEditor], so the draft is never invalid;
 * [cancel] returns to the last committed workspace.
 */
data class WorkspaceEditSession(
    val committed: Workspace,
    val draft: Workspace = committed,
    private val undoStack: List<Workspace> = emptyList(),
    private val redoStack: List<Workspace> = emptyList(),
) {
    val isDirty: Boolean get() = draft != committed

    val canUndo: Boolean get() = undoStack.isNotEmpty()

    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun apply(
        edit: WorkspaceEdit,
        context: EditContext = EditContext(),
    ): SessionStep =
        when (val result = WorkspaceEditor.apply(draft, edit, context)) {
            is EditResult.Rejected -> SessionStep(this, result)
            is EditResult.Applied ->
                if (result.workspace == draft) {
                    SessionStep(this, result)
                } else {
                    val next =
                        copy(
                            draft = result.workspace,
                            undoStack = (undoStack + draft).takeLast(MAX_HISTORY),
                            redoStack = emptyList(),
                        )
                    SessionStep(next, result)
                }
        }

    fun undo(): WorkspaceEditSession =
        if (undoStack.isEmpty()) {
            this
        } else {
            copy(draft = undoStack.last(), undoStack = undoStack.dropLast(1), redoStack = redoStack + draft)
        }

    fun redo(): WorkspaceEditSession =
        if (redoStack.isEmpty()) {
            this
        } else {
            copy(draft = redoStack.last(), redoStack = redoStack.dropLast(1), undoStack = undoStack + draft)
        }

    /** Discards every uncommitted change. */
    fun cancel(): WorkspaceEditSession = WorkspaceEditSession(committed)

    fun commit(context: EditContext = EditContext()): CommitResult {
        val known = WorkspaceEditor.issues(committed, context).toSet()
        val introduced = WorkspaceEditor.issues(draft, context).filterNot { it in known }
        return if (introduced.isEmpty()) {
            CommitResult.Committed(WorkspaceEditSession(draft))
        } else {
            CommitResult.Rejected(introduced)
        }
    }

    companion object {
        const val MAX_HISTORY = 50
    }
}
