package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutCapabilities
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.WidgetPlacement
import com.riffle.core.domain.launcher.workspace.WidgetSpan
import com.riffle.core.domain.launcher.workspace.WorkspaceDock
import com.riffle.core.domain.launcher.workspace.WorkspaceIssue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WorkspaceEditorTest {
    private fun apply(
        ws: com.riffle.core.domain.launcher.workspace.Workspace,
        edit: WorkspaceEdit,
        context: EditContext = EDIT_CONTEXT,
    ) = WorkspaceEditor.apply(ws, edit, context)

    private fun applied(result: EditResult) = assertIs<EditResult.Applied>(result).workspace

    private fun rejection(result: EditResult) = assertIs<EditResult.Rejected>(result).reason

    @Test
    fun addMoveAndRemovePagesKeepOrder() {
        var ws = workspace(boundPage("a"))
        ws = applied(apply(ws, WorkspaceEdit.AddPage(boundPage("b"))))
        ws = applied(apply(ws, WorkspaceEdit.AddPage(boundPage("c"), index = 0)))
        assertEquals(listOf("c", "a", "b"), ws.pages.map { it.id.value })

        ws = applied(apply(ws, WorkspaceEdit.MovePage(cid("c"), 99)))
        assertEquals(listOf("a", "b", "c"), ws.pages.map { it.id.value })
        ws = applied(apply(ws, WorkspaceEdit.MovePage(cid("c"), -5)))
        assertEquals(listOf("c", "a", "b"), ws.pages.map { it.id.value })

        ws = applied(apply(ws, WorkspaceEdit.RemovePage(cid("a"))))
        assertEquals(listOf("c", "b"), ws.pages.map { it.id.value })
    }

    @Test
    fun removingTheLastPageIsRejected() {
        val ws = workspace(boundPage("a"))
        val reason = assertIs<EditRejection.Invalid>(rejection(apply(ws, WorkspaceEdit.RemovePage(cid("a")))))
        assertTrue(WorkspaceIssue.NoPages in reason.issues)
    }

    @Test
    fun unknownTargetsAreRejected() {
        val ws = workspace(boundPage("a"), gridPage("g"))
        assertEquals(EditRejection.UnknownPage(cid("x")), rejection(apply(ws, WorkspaceEdit.RemovePage(cid("x")))))
        assertEquals(EditRejection.UnknownPage(cid("x")), rejection(apply(ws, WorkspaceEdit.MovePage(cid("x"), 0))))
        assertEquals(
            EditRejection.UnknownWidget(cid("w")),
            rejection(apply(ws, WorkspaceEdit.RemoveWidget(cid("g"), cid("w")))),
        )
        assertEquals(
            EditRejection.NotAGridPage(cid("a")),
            rejection(apply(ws, WorkspaceEdit.AddWidget(cid("a"), widget("w"), 0, 0))),
        )
        assertEquals(
            EditRejection.NotABoundPage(cid("g")),
            rejection(apply(ws, WorkspaceEdit.SetPageBinding(cid("g"), binding()))),
        )
    }

    @Test
    fun invalidPairingsAreRejectedWithReasons() {
        val ws = workspace(boundPage("a"))
        // A card draws a single item, so an unlimited flat lens cannot be a card page.
        val edit = WorkspaceEdit.SetPageBinding(cid("a"), binding(ExpressionKind.CARD))
        assertIs<EditRejection.Invalid>(rejection(apply(ws, edit)))
        // Limited to one item it can.
        val ok = WorkspaceEdit.SetPageBinding(cid("a"), binding(ExpressionKind.CARD, limit = 1))
        applied(apply(ws, ok))
    }

    @Test
    fun pageSetNeedsAGroupedLensAndAVerticalPerGroupExpression() {
        val ws = workspace(boundPage("a"))
        val flat = WorkspaceEdit.AddPage(PageSetContainer(cid("s"), binding(ExpressionKind.LIST)))
        assertIs<EditRejection.Invalid>(rejection(apply(ws, flat)))

        val grouped = binding(ExpressionKind.CARD_STACK, NOTES, LensGroup.ByGroupKey)
        applied(apply(ws, WorkspaceEdit.AddPage(PageSetContainer(cid("s"), grouped))))

        val sideways = binding(ExpressionKind.ICON_ROW, NOTES, LensGroup.ByGroupKey)
        assertIs<EditRejection.Invalid>(
            rejection(apply(ws, WorkspaceEdit.AddPage(PageSetContainer(cid("s"), sideways)))),
        )
    }

    @Test
    fun groupingANonGroupableSourceIsRejected() {
        val ws = workspace(boundPage("a"))
        val edit = WorkspaceEdit.SetPageBinding(cid("a"), binding(ExpressionKind.INDEX, CAL, LensGroup.ByGroupKey))
        assertIs<EditRejection.Invalid>(rejection(apply(ws, edit)))
    }

    @Test
    fun widgetEditsRespectBoundsAndOverlap() {
        var ws = workspace(gridPage("g", 4, 4))
        ws = applied(apply(ws, WorkspaceEdit.AddWidget(cid("g"), widget("w1", 2, 2), 0, 0)))
        assertIs<EditRejection.Invalid>(rejection(apply(ws, WorkspaceEdit.AddWidget(cid("g"), widget("w2"), 1, 1))))
        assertIs<EditRejection.Invalid>(rejection(apply(ws, WorkspaceEdit.AddWidget(cid("g"), widget("w2"), 4, 0))))
        assertIs<EditRejection.Invalid>(rejection(apply(ws, WorkspaceEdit.AddWidget(cid("g"), widget("w1"), 3, 3))))

        ws = applied(apply(ws, WorkspaceEdit.AddWidget(cid("g"), widget("w2"), 2, 0)))
        assertIs<EditRejection.Invalid>(rejection(apply(ws, WorkspaceEdit.MoveWidget(cid("g"), cid("w2"), 1, 0))))
        ws = applied(apply(ws, WorkspaceEdit.MoveWidget(cid("g"), cid("w2"), 3, 3)))
        assertIs<EditRejection.Invalid>(
            rejection(apply(ws, WorkspaceEdit.ResizeWidget(cid("g"), cid("w2"), WidgetSpan(2, 1)))),
        )
        ws = applied(apply(ws, WorkspaceEdit.ResizeWidget(cid("g"), cid("w1"), WidgetSpan(3, 3))))
        ws = applied(apply(ws, WorkspaceEdit.SetWidgetBinding(cid("g"), cid("w1"), binding(ExpressionKind.ICON_GRID))))
        ws = applied(apply(ws, WorkspaceEdit.RemoveWidget(cid("g"), cid("w1"))))
        val grid = (ws.pages.single() as com.riffle.core.domain.launcher.workspace.PageContainer).content
        assertEquals(listOf("w2"), (grid as PageContent.WidgetGrid).placements.map { it.widget.id.value })
    }

    @Test
    fun finderRoleNeedsCategoriesOrAlphaListAndIsUnique() {
        val ws = workspace(boundPage("a"))
        val finder = { id: String, kind: ExpressionKind ->
            WorkspaceEdit.AddPage(
                com.riffle.core.domain.launcher.workspace.PageContainer(
                    cid(id),
                    PageContent.Bound(
                        binding(
                            kind,
                            group = if (kind == ExpressionKind.CATEGORIES) LensGroup.ByGroupKey else LensGroup.None,
                        ),
                    ),
                    com.riffle.core.domain.launcher.workspace.PageRole.FINDER,
                ),
            )
        }
        assertIs<EditRejection.Invalid>(rejection(apply(ws, finder("f", ExpressionKind.LIST))))
        val withFinder = applied(apply(ws, finder("f", ExpressionKind.CATEGORIES)))
        val second = rejection(apply(withFinder, finder("f2", ExpressionKind.ALPHA_LIST)))
        assertTrue(WorkspaceIssue.MultipleFinderPages in assertIs<EditRejection.Invalid>(second).issues)
        // Re-binding the finder to a non-finder expression is rejected too.
        val rebind = WorkspaceEdit.SetPageBinding(cid("f"), binding(ExpressionKind.LIST))
        assertIs<EditRejection.Invalid>(rejection(apply(withFinder, rebind)))
    }

    @Test
    fun renameSkinAndDock() {
        val ws = workspace(boundPage("a"))
        assertEquals(EditRejection.BlankName, rejection(apply(ws, WorkspaceEdit.Rename("  "))))
        assertEquals("Work", applied(apply(ws, WorkspaceEdit.Rename("  Work "))).name)

        assertEquals(EditRejection.BlankSkinId, rejection(apply(ws, WorkspaceEdit.SetSkinOverride(" "))))
        val skinned = applied(apply(ws, WorkspaceEdit.SetSkinOverride("midnight")))
        assertEquals("midnight", skinned.skinOverrideId)
        assertEquals(null, applied(apply(skinned, WorkspaceEdit.SetSkinOverride(null))).skinOverrideId)

        val dock = binding(ExpressionKind.ICON_ROW, NOTES)
        val withDock = applied(apply(ws, WorkspaceEdit.SetDockSection(dock)))
        assertEquals(WorkspaceDock(dock), withDock.dock)
        // A card cannot be the dock section of an unlimited lens.
        assertIs<EditRejection.Invalid>(
            rejection(apply(ws, WorkspaceEdit.SetDockSection(binding(ExpressionKind.CARD)))),
        )
        assertEquals(null, applied(apply(withDock, WorkspaceEdit.SetDockSection(null))).dock.dynamicSection)
    }

    @Test
    fun layoutCapabilitiesAreEnforced() {
        val ws = workspace(boundPage("a"))
        val context = EDIT_CONTEXT.copy(capabilities = LayoutCapabilities(setOf(ExpressionKind.LIST)))
        val edit = WorkspaceEdit.AddPage(boundPage("b", binding(ExpressionKind.ICON_GRID)))
        val reason = assertIs<EditRejection.Invalid>(rejection(apply(ws, edit, context)))
        assertTrue(WorkspaceIssue.UnsupportedExpression(ExpressionKind.ICON_GRID) in reason.issues)
    }

    @Test
    fun anAlreadyBrokenWorkspaceCanStillBeRepairedStepByStep() {
        val broken = workspace(boundPage("a", binding(ExpressionKind.CARD)), boundPage("b"))
        // Unrelated edits that introduce nothing new are allowed.
        applied(apply(broken, WorkspaceEdit.Rename("Fixed")))
        // Fixing the broken page works.
        applied(apply(broken, WorkspaceEdit.RemovePage(cid("a"))))
        // Adding a new problem is still rejected.
        assertIs<EditRejection.Invalid>(
            rejection(apply(broken, WorkspaceEdit.AddPage(boundPage("c", binding(ExpressionKind.CARD, NOTES))))),
        )
    }

    @Test
    fun duplicateIdsAreRejected() {
        val ws = workspace(boundPage("a"), gridPage("g", 4, 4, WidgetPlacement(widget("w"), 0, 0)))
        assertIs<EditRejection.Invalid>(rejection(apply(ws, WorkspaceEdit.AddPage(boundPage("a")))))
        assertIs<EditRejection.Invalid>(rejection(apply(ws, WorkspaceEdit.AddWidget(cid("g"), widget("w"), 1, 0))))
    }
}
