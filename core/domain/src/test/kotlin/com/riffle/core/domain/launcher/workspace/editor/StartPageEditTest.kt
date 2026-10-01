package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageRole
import com.riffle.core.domain.launcher.workspace.Workspace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class StartPageEditTest {
    private val base = workspace(boundPage("a"), boundPage("b"), boundPage("c"))

    private fun apply(
        ws: Workspace,
        edit: WorkspaceEdit,
    ) = WorkspaceEditor.apply(ws, edit, EDIT_CONTEXT)

    @Test
    fun setsTheStartPage() {
        val result = assertIs<EditResult.Applied>(apply(base, WorkspaceEdit.SetStartPage(cid("b"))))
        assertEquals(cid("b"), result.workspace.startPageId)
    }

    @Test
    fun nullClearsTheStartPage() {
        val set = assertIs<EditResult.Applied>(apply(base, WorkspaceEdit.SetStartPage(cid("b")))).workspace
        val cleared = assertIs<EditResult.Applied>(apply(set, WorkspaceEdit.SetStartPage(null))).workspace
        assertNull(cleared.startPageId)
    }

    @Test
    fun anUnknownPageIsRejected() {
        val result = assertIs<EditResult.Rejected>(apply(base, WorkspaceEdit.SetStartPage(cid("zzz"))))
        assertEquals(EditRejection.UnknownPage(cid("zzz")), result.reason)
    }

    @Test
    fun theFinderCanBeTheStartPage() {
        val finder = PageContainer(cid("f"), PageContent.Bound(binding(ExpressionKind.ALPHA_LIST)), PageRole.FINDER)
        val withFinder = base.copy(pages = base.pages + finder)
        val result = assertIs<EditResult.Applied>(apply(withFinder, WorkspaceEdit.SetStartPage(cid("f"))))
        assertEquals(cid("f"), result.workspace.startPageId)
    }

    @Test
    fun removingTheStartPageClearsIt() {
        val set = assertIs<EditResult.Applied>(apply(base, WorkspaceEdit.SetStartPage(cid("b")))).workspace
        val removed = assertIs<EditResult.Applied>(apply(set, WorkspaceEdit.RemovePage(cid("b")))).workspace
        assertNull(removed.startPageId)
        val other = assertIs<EditResult.Applied>(apply(set, WorkspaceEdit.RemovePage(cid("a")))).workspace
        assertEquals(cid("b"), other.startPageId)
    }

    @Test
    fun movingPagesKeepsTheStartPageByIdentity() {
        val set = assertIs<EditResult.Applied>(apply(base, WorkspaceEdit.SetStartPage(cid("b")))).workspace
        val moved = assertIs<EditResult.Applied>(apply(set, WorkspaceEdit.MovePage(cid("b"), 2))).workspace
        assertEquals(cid("b"), moved.startPageId)
    }

    @Test
    fun undoRevertsTheStartPage() {
        var session = WorkspaceEditSession(base)
        session = session.apply(WorkspaceEdit.SetStartPage(cid("c")), EDIT_CONTEXT).session
        assertEquals(cid("c"), session.draft.startPageId)
        session = session.undo()
        assertNull(session.draft.startPageId)
        session = session.redo()
        assertEquals(cid("c"), session.draft.startPageId)
    }
}
