package com.riffle.core.domain.launcher.workspace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LayoutWorkspacesTest {
    private val lens = Lens(sources = listOf(SourceId("apps")))

    private fun ws(
        id: String,
        name: String = id,
    ) = Workspace(
        WorkspaceId(id),
        name,
        listOf(PageContainer(ContainerId("p-$id"), PageContent.Bound(LensBinding(lens, ExpressionKind.LIST)))),
    )

    private fun layout() = LayoutWorkspaces.single(ws("a")).add(ws("b")).add(ws("c"))

    private fun ids(layout: LayoutWorkspaces) = layout.workspaces.map { it.id.value }

    private class Counter : WorkspaceIdFactory {
        var n = 0

        override fun next() = "n${n++}"
    }

    @Test
    fun invariantsAreEnforcedByConstruction() {
        assertFailsWith<IllegalArgumentException> { LayoutWorkspaces(emptyList(), WorkspaceId("a"), WorkspaceId("a")) }
        assertFailsWith<IllegalArgumentException> {
            LayoutWorkspaces(listOf(ws("a"), ws("a")), WorkspaceId("a"), WorkspaceId("a"))
        }
        assertFailsWith<IllegalArgumentException> {
            LayoutWorkspaces(listOf(ws("a")), WorkspaceId("x"), WorkspaceId("a"))
        }
        assertFailsWith<IllegalArgumentException> {
            LayoutWorkspaces(listOf(ws("a")), WorkspaceId("a"), WorkspaceId("x"))
        }
    }

    @Test
    fun addAppendsAndOptionallyActivates() {
        val added = layout().add(ws("d"), activate = true)
        assertEquals(listOf("a", "b", "c", "d"), ids(added))
        assertEquals(WorkspaceId("d"), added.activeId)
        assertEquals(WorkspaceId("a"), added.defaultId)
    }

    @Test
    fun addIgnoresDuplicateId() {
        val base = layout()
        assertSame(base, base.add(ws("b", "other")))
    }

    @Test
    fun removeKeepsAtLeastOneWorkspace() {
        val only = LayoutWorkspaces.single(ws("a"))
        assertSame(only, only.remove(WorkspaceId("a")))
        assertSame(only, only.remove(WorkspaceId("missing")))
    }

    @Test
    fun removingActiveFallsBackToDefault() {
        val removed = layout().activate(WorkspaceId("c")).withDefault(WorkspaceId("b")).remove(WorkspaceId("c"))
        assertEquals(WorkspaceId("b"), removed.activeId)
        assertEquals(listOf("a", "b"), ids(removed))
    }

    @Test
    fun removingDefaultMovesDefaultToFirstRemainingAndActiveFollowsWhenItWasDefault() {
        val removed = layout().remove(WorkspaceId("a"))
        assertEquals(WorkspaceId("b"), removed.defaultId)
        assertEquals(WorkspaceId("b"), removed.activeId)
    }

    @Test
    fun removingOtherWorkspaceLeavesActiveAndDefault() {
        val removed = layout().activate(WorkspaceId("b")).remove(WorkspaceId("c"))
        assertEquals(WorkspaceId("b"), removed.activeId)
        assertEquals(WorkspaceId("a"), removed.defaultId)
    }

    @Test
    fun renameTrimsAndRejectsBlank() {
        val base = layout()
        assertEquals("Work", base.rename(WorkspaceId("b"), "  Work ").find(WorkspaceId("b"))?.name)
        assertSame(base, base.rename(WorkspaceId("b"), "   "))
        assertSame(base, base.rename(WorkspaceId("zzz"), "x"))
    }

    @Test
    fun moveReordersAndClamps() {
        assertEquals(listOf("c", "a", "b"), ids(layout().move(WorkspaceId("c"), 0)))
        assertEquals(listOf("b", "c", "a"), ids(layout().move(WorkspaceId("a"), 99)))
        assertEquals(listOf("a", "b", "c"), ids(layout().move(WorkspaceId("b"), -5).move(WorkspaceId("b"), 1)))
        assertEquals(listOf("a", "b", "c"), ids(layout().move(WorkspaceId("missing"), 0)))
    }

    @Test
    fun duplicateInsertsFreshCopyAfterSourceWithoutActivating() {
        val ids = Counter()
        val duplicated = layout().duplicate(WorkspaceId("a"), ids)
        assertEquals(listOf("a", "n0", "b", "c"), ids(duplicated))
        val copy = duplicated.find(WorkspaceId("n0"))!!
        assertEquals("a copy", copy.name)
        assertEquals(ContainerId("n1"), copy.pages.single().id)
        assertEquals(WorkspaceId("a"), duplicated.activeId)
        assertEquals(layout().find(WorkspaceId("a"))!!.pages.single().id, ContainerId("p-a"))
    }

    @Test
    fun duplicateOfUnknownIsNoOp() {
        val base = layout()
        assertSame(base, base.duplicate(WorkspaceId("nope")))
    }

    @Test
    fun activateAndDefaultRequireExistingWorkspace() {
        val base = layout()
        assertEquals(WorkspaceId("b"), base.activate(WorkspaceId("b")).activeId)
        assertSame(base, base.activate(WorkspaceId("nope")))
        assertEquals(WorkspaceId("c"), base.withDefault(WorkspaceId("c")).defaultId)
        assertSame(base, base.withDefault(WorkspaceId("nope")))
    }

    @Test
    fun replaceKeepsIdAndUpdatesContent() {
        val replaced = layout().replace(WorkspaceId("b")) { it.copy(id = WorkspaceId("hijack"), skinOverrideId = "s") }
        assertEquals("s", replaced.find(WorkspaceId("b"))?.skinOverrideId)
        assertNull(replaced.find(WorkspaceId("hijack")))
    }

    @Test
    fun repairedFixesInconsistentParts() {
        val parts = listOf(ws("a"), ws("a"), ws("b"))
        val repaired = LayoutWorkspaces.repaired(parts, WorkspaceId("zzz"), WorkspaceId("b"))!!
        assertEquals(listOf("a", "b"), ids(repaired))
        assertEquals(WorkspaceId("b"), repaired.defaultId)
        assertEquals(WorkspaceId("b"), repaired.activeId)
        assertNull(LayoutWorkspaces.repaired(emptyList(), null, null))
        val fallback = LayoutWorkspaces.repaired(listOf(ws("a"), ws("b")), null, null)!!
        assertEquals(WorkspaceId("a"), fallback.activeId)
        assertTrue(fallback.active.pages.isNotEmpty())
    }
}
