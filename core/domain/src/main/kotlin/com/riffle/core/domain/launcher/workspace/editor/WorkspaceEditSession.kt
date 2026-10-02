package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
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

/** One point of the undo history: the draft workspace and the [LensScope] around it. */
data class EditSnapshot(
    val workspace: Workspace,
    val scope: LensScope,
)

/**
 * Draft versus committed state for one workspace edit, with undo and redo. Immutable: each operation
 * returns the next session. Edits go through [WorkspaceEditor], so the draft is never invalid;
 * [cancel] returns to the last committed workspace.
 *
 * [scope] is what saved-lens operations change next to the workspace (the layout's library and its other
 * workspaces). It is part of every history step, so Undo reverts a Save as lens or an adoption exactly. It stays
 * empty for a host that does not use saved lenses.
 */
data class WorkspaceEditSession(
    val committed: Workspace,
    val draft: Workspace = committed,
    private val undoStack: List<EditSnapshot> = emptyList(),
    private val redoStack: List<EditSnapshot> = emptyList(),
    val scope: LensScope = LensScope(),
    val committedScope: LensScope = scope,
) {
    val isDirty: Boolean get() = draft != committed || scope != committedScope

    val canUndo: Boolean get() = undoStack.isNotEmpty()

    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /**
     * Applies [edit] to the draft. [nextScope] is the scope the same step leaves behind (a Save as lens adds the
     * library entry and the binding that references it as one edit); it defaults to the current one.
     */
    fun apply(
        edit: WorkspaceEdit,
        context: EditContext = EditContext(),
        nextScope: LensScope = scope,
    ): SessionStep =
        when (val result = WorkspaceEditor.apply(draft, edit, context)) {
            is EditResult.Rejected -> SessionStep(this, result)
            is EditResult.Applied -> SessionStep(step(result.workspace, nextScope), result)
        }

    /**
     * Takes [layout] (a [LensScope.layoutWith] view of this session, changed by a library operation) as the next
     * draft and scope in one history step. A layout that does not hold the draft's workspace changes nothing.
     */
    fun applyLayout(layout: LayoutWorkspaces): WorkspaceEditSession {
        val next = layout.find(draft.id) ?: return this
        return step(next, LensScope.of(layout, draft.id))
    }

    private fun step(
        workspace: Workspace,
        nextScope: LensScope,
    ): WorkspaceEditSession =
        if (workspace == draft && nextScope == scope) {
            this
        } else {
            copy(
                draft = workspace,
                scope = nextScope,
                undoStack = (undoStack + EditSnapshot(draft, scope)).takeLast(MAX_HISTORY),
                redoStack = emptyList(),
            )
        }

    fun undo(): WorkspaceEditSession =
        if (undoStack.isEmpty()) {
            this
        } else {
            val previous = undoStack.last()
            copy(
                draft = previous.workspace,
                scope = previous.scope,
                undoStack = undoStack.dropLast(1),
                redoStack = redoStack + EditSnapshot(draft, scope),
            )
        }

    fun redo(): WorkspaceEditSession =
        if (redoStack.isEmpty()) {
            this
        } else {
            val next = redoStack.last()
            copy(
                draft = next.workspace,
                scope = next.scope,
                redoStack = redoStack.dropLast(1),
                undoStack = undoStack + EditSnapshot(draft, scope),
            )
        }

    /** Discards every uncommitted change. */
    fun cancel(): WorkspaceEditSession = WorkspaceEditSession(committed, scope = committedScope)

    fun commit(context: EditContext = EditContext()): CommitResult {
        val known = WorkspaceEditor.issues(committed, context).toSet()
        val introduced =
            WorkspaceEditor.issues(draft, context).filterNot { it in known } + introducedInOthers(context)
        return if (introduced.isEmpty()) {
            CommitResult.Committed(WorkspaceEditSession(draft, scope = scope))
        } else {
            CommitResult.Rejected(introduced)
        }
    }

    /** Issues the other workspaces gained during the session (adopting a lens must never add one). */
    private fun introducedInOthers(context: EditContext): List<WorkspaceIssue> {
        val before = committedScope.others.associateBy { it.id }
        return scope.changedOthers(committedScope).flatMap { other ->
            val known = before[other.id]?.let { WorkspaceEditor.issues(it, context) }.orEmpty().toSet()
            WorkspaceEditor.issues(other, context).filterNot { it in known }
        }
    }

    companion object {
        const val MAX_HISTORY = 50
    }
}
