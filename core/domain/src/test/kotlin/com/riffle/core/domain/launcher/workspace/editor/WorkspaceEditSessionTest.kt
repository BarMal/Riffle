package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.ExpressionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WorkspaceEditSessionTest {
    private val start = workspace(boundPage("a"))

    @Test
    fun editsChangeOnlyTheDraft() {
        val step = WorkspaceEditSession(start).apply(WorkspaceEdit.Rename("New"), EDIT_CONTEXT)
        assertEquals("New", step.session.draft.name)
        assertEquals("Main", step.session.committed.name)
        assertTrue(step.session.isDirty)
        assertTrue(step.session.canUndo)
    }

    @Test
    fun rejectedEditsLeaveTheSessionUntouched() {
        val session = WorkspaceEditSession(start)
        val step = session.apply(WorkspaceEdit.RemovePage(cid("a")), EDIT_CONTEXT)
        assertIs<EditResult.Rejected>(step.result)
        assertSame(session, step.session)
    }

    @Test
    fun undoAndRedoWalkTheHistory() {
        var session = WorkspaceEditSession(start)
        session = session.apply(WorkspaceEdit.Rename("One"), EDIT_CONTEXT).session
        session = session.apply(WorkspaceEdit.Rename("Two"), EDIT_CONTEXT).session

        session = session.undo()
        assertEquals("One", session.draft.name)
        assertTrue(session.canRedo)
        session = session.undo()
        assertEquals("Main", session.draft.name)
        assertFalse(session.isDirty)
        assertFalse(session.canUndo)
        assertSame(session, session.undo())

        session = session.redo().redo()
        assertEquals("Two", session.draft.name)
        assertSame(session, session.redo())
    }

    @Test
    fun aNewEditClearsRedo() {
        var session = WorkspaceEditSession(start)
        session = session.apply(WorkspaceEdit.Rename("One"), EDIT_CONTEXT).session.undo()
        session = session.apply(WorkspaceEdit.Rename("Other"), EDIT_CONTEXT).session
        assertFalse(session.canRedo)
    }

    @Test
    fun anEditThatChangesNothingIsNotRecorded() {
        val session = WorkspaceEditSession(start)
        val step = session.apply(WorkspaceEdit.Rename("Main"), EDIT_CONTEXT)
        assertIs<EditResult.Applied>(step.result)
        assertFalse(step.session.canUndo)
    }

    @Test
    fun cancelReturnsToTheLastCommit() {
        var session = WorkspaceEditSession(start)
        session = session.apply(WorkspaceEdit.Rename("One"), EDIT_CONTEXT).session
        session = assertIs<CommitResult.Committed>(session.commit(EDIT_CONTEXT)).session
        assertEquals("One", session.committed.name)
        assertFalse(session.isDirty)

        session = session.apply(WorkspaceEdit.AddPage(boundPage("b")), EDIT_CONTEXT).session
        session = session.cancel()
        assertEquals(session.committed, session.draft)
        assertEquals(1, session.draft.pages.size)
        assertFalse(session.canUndo)
    }

    @Test
    fun historyIsBounded() {
        var session = WorkspaceEditSession(start)
        repeat(WorkspaceEditSession.MAX_HISTORY + 20) { i ->
            session = session.apply(WorkspaceEdit.Rename("n$i"), EDIT_CONTEXT).session
        }
        var undone = 0
        while (session.canUndo) {
            session = session.undo()
            undone++
        }
        assertEquals(WorkspaceEditSession.MAX_HISTORY, undone)
    }

    @Test
    fun commitRejectsADraftWithNewProblems() {
        // A draft can only be invalid if the session was built that way; commit still re-checks.
        val bad = workspace(boundPage("a", binding(ExpressionKind.CARD)))
        val session = WorkspaceEditSession(committed = start, draft = bad)
        assertIs<CommitResult.Rejected>(session.commit(EDIT_CONTEXT))
    }
}
