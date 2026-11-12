package com.riffle.core.domain.launcher.workspace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceValidationTest {
    private val flat = Lens(sources = listOf(SourceId("apps")))

    private fun page(
        id: String,
        expression: ExpressionKind = ExpressionKind.LIST,
    ) = PageContainer(ContainerId(id), PageContent.Bound(LensBinding(flat, expression)))

    private fun workspace(
        id: String,
        vararg pages: PageHost,
    ) = Workspace(WorkspaceId(id), id, pages.toList())

    @Test
    fun emptyWorkspaceHasNoPagesIssue() {
        assertEquals(listOf(WorkspaceIssue.NoPages), WorkspaceValidation.validate(workspace("w")))
    }

    @Test
    fun onlyOneFinderPageIsAllowed() {
        fun finder(id: String) =
            PageContainer(
                ContainerId(id),
                PageContent.Bound(LensBinding(flat, ExpressionKind.ALPHA_LIST)),
                PageRole.FINDER,
            )
        val issues = WorkspaceValidation.validate(workspace("w", finder("a"), finder("b")))
        assertTrue(WorkspaceIssue.MultipleFinderPages in issues)
    }

    @Test
    fun dockDynamicSectionIsPairingChecked() {
        val bad = workspace("w", page("p")).copy(dock = WorkspaceDock(LensBinding(flat, ExpressionKind.CATEGORIES)))
        assertTrue(WorkspaceValidation.validate(bad).any { it is WorkspaceIssue.DockPairing })
    }

    @Test
    fun layoutThatCannotDrawAnExpressionIsFlagged() {
        val ws = workspace("w", page("p", ExpressionKind.CARD_STACK))
        val caps = LayoutCapabilities(ExpressionKind.entries.toSet() - ExpressionKind.CARD_STACK)
        assertEquals(
            listOf(WorkspaceIssue.UnsupportedExpression(ExpressionKind.CARD_STACK)),
            WorkspaceValidation.validate(ws, caps),
        )
    }
}
