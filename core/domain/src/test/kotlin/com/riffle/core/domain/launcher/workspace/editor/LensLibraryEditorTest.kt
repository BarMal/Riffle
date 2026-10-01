package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.BreakPolicy
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LensLibrary
import com.riffle.core.domain.launcher.workspace.LensLibraryOps
import com.riffle.core.domain.launcher.workspace.LensLibraryRejection
import com.riffle.core.domain.launcher.workspace.LibraryAdd
import com.riffle.core.domain.launcher.workspace.LibraryProblem
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.WidgetPlacement
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceDock
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LensLibraryEditorTest {
    private val wid = WorkspaceId("w")
    private val page = FlowMode.EditPage(cid("p"))
    private val widgetMode = FlowMode.EditWidget(cid("g"), cid("wg"))
    private val five = lens(APPS, limit = 5)

    private fun layout(): LayoutWorkspaces =
        LayoutWorkspaces.single(
            Workspace(
                wid,
                "w",
                listOf(
                    boundPage("p", binding(ExpressionKind.LIST, limit = 5)),
                    gridPage(
                        "g",
                        4,
                        4,
                        WidgetPlacement(widget("wg", 2, 1, binding(ExpressionKind.ICON_ROW)), 0, 0),
                    ),
                ),
                WorkspaceDock(binding(ExpressionKind.ICON_ROW)),
            ),
        )

    private fun pageBinding(layout: LayoutWorkspaces) =
        ((layout.active.pages.first { it.id == cid("p") } as PageContainer).content as PageContent.Bound).binding

    private fun saveFive(): LibraryEditResult.Applied =
        assertIs<LibraryEditResult.Applied>(
            LensLibraryEditor.saveAsLens(layout(), wid, page, "Five", counterIds(), EDIT_CONTEXT),
        )

    private fun libraryWith(
        name: String,
        lens: Lens,
    ): Pair<LensLibrary, LensId> {
        val added = LensLibrary().tryAdd(name, lens, counterIds("lib")) as LibraryAdd.Added
        return added.library to added.id
    }

    private fun rejection(result: LibraryEditResult): LibraryEditRejection =
        assertIs<LibraryEditResult.Rejected>(result).reason

    @Test
    fun `save as lens then use it elsewhere`() {
        val saved = saveFive()
        val id = assertNotNull(saved.lensId)
        assertEquals(id, pageBinding(saved.layout).ref)
        assertEquals(five, saved.layout.library.find(id)?.lens)

        val used = LensLibraryEditor.useSavedLens(saved.layout, wid, widgetMode, id, context = EDIT_CONTEXT)
        val dependents = LensLibraryOps.dependents(assertIs<LibraryEditResult.Applied>(used).layout, id)
        assertEquals(2, dependents.size)
        assertEquals(ExpressionKind.ICON_ROW, dependents.last().expression)
    }

    @Test
    fun `save as lens validates the name and the target`() {
        val taken = saveFive()
        val clash = rejection(LensLibraryEditor.saveAsLens(taken.layout, wid, widgetMode, "five", counterIds("b")))
        val library = assertIs<LibraryEditRejection.Library>(clash)
        assertEquals(LensLibraryRejection.Problem(LibraryProblem.NAME_TAKEN), library.reason)
        val missing = FlowMode.EditPage(cid("zz"))
        assertEquals(
            LibraryEditRejection.UnknownTarget,
            rejection(LensLibraryEditor.saveAsLens(layout(), wid, missing, "x")),
        )
        assertEquals(
            LibraryEditRejection.UnknownTarget,
            rejection(LensLibraryEditor.saveAsLens(layout(), wid, FlowMode.Add, "x")),
        )
        val noWorkspace = LensLibraryEditor.saveAsLens(layout(), WorkspaceId("no"), page, "x")
        assertEquals(LibraryEditRejection.UnknownWorkspace, rejection(noWorkspace))
    }

    @Test
    fun `use saved lens is gated by the pairing rule`() {
        val (library, id) = libraryWith("Grouped", lens(NOTES, group = LensGroup.ByGroupKey))
        val base = layout().copy(library = library)
        val rejected =
            assertIs<LibraryEditRejection.Editor>(
                rejection(LensLibraryEditor.useSavedLens(base, wid, page, id, context = EDIT_CONTEXT)),
            )
        assertIs<EditRejection.Invalid>(rejected.reason)
        val ok = LensLibraryEditor.useSavedLens(base, wid, page, id, ExpressionKind.CATEGORIES, EDIT_CONTEXT)
        assertIs<LibraryEditResult.Applied>(ok)
        assertIs<LibraryEditResult.Rejected>(LensLibraryEditor.useSavedLens(base, wid, page, LensId("x")))
    }

    @Test
    fun `detach keeps the lens and drops the ref`() {
        val dock = FlowMode.EditDock
        val saved =
            assertIs<LibraryEditResult.Applied>(LensLibraryEditor.saveAsLens(layout(), wid, dock, "Dock", counterIds()))
        val detached = assertIs<LibraryEditResult.Applied>(LensLibraryEditor.detach(saved.layout, wid, dock))
        assertNotNull(saved.layout.active.dock.dynamicSection?.ref)
        assertNull(detached.layout.active.dock.dynamicSection?.ref)
        assertEquals(saved.layout.active.dock.dynamicSection?.lens, detached.layout.active.dock.dynamicSection?.lens)
    }

    @Test
    fun `editing a saved lens that would break a dependent is rejected with the impact`() {
        val saved = saveFive()
        val id = assertNotNull(saved.lensId)
        val grouped = lens(APPS, group = LensGroup.ByGroupKey)
        val rejected = rejection(LensLibraryEditor.editSavedLens(saved.layout, id, grouped, context = EDIT_CONTEXT))
        val library = assertIs<LibraryEditRejection.Library>(rejected)
        val breaks = assertIs<LensLibraryRejection.BreaksDependents>(library.reason)
        assertEquals(listOf(cid("p")), breaks.impact.wouldBreak.map { it.dependent.containerId })

        val detached =
            LensLibraryEditor.editSavedLens(
                saved.layout,
                id,
                grouped,
                BreakPolicy.DETACH_BROKEN,
                EDIT_CONTEXT,
            )
        val detachedLayout = assertIs<LibraryEditResult.Applied>(detached).layout
        assertNull(pageBinding(detachedLayout).ref)
        assertEquals(five, pageBinding(detachedLayout).lens)

        val two = lens(APPS, limit = 2)
        val fine =
            assertIs<LibraryEditResult.Applied>(
                LensLibraryEditor.editSavedLens(saved.layout, id, two, context = EDIT_CONTEXT),
            )
        assertEquals(two, pageBinding(fine.layout).lens)
        assertEquals(two, fine.layout.library.find(id)?.lens)
    }
}
