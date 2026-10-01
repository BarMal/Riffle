package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LensLibraryOpsTest {
    private val notes = llLens(LL_NOTES, group = LensGroup.ByGroupKey)

    /** A layout whose list page, page-set, widget and dock all use one saved lens "Shared" where valid. */
    private data class Fixture(val layout: LayoutWorkspaces, val id: LensId)

    private fun fixture(lens: Lens = llLens()): Fixture {
        val added = LensLibrary().tryAdd("Shared", lens, llCounter("lib")) as LibraryAdd.Added
        val ref = added.id
        val ws =
            Workspace(
                WorkspaceId("w"),
                "w",
                listOf(
                    llPage("list", llBinding(ExpressionKind.LIST, lens, ref)),
                    llGrid("grid", "widget", llBinding(ExpressionKind.ICON_ROW, lens, ref)),
                    llPage("other", llBinding(ExpressionKind.LIST, llLens(LL_NOTES))),
                ),
                WorkspaceDock(llBinding(ExpressionKind.ICON_ROW, lens, ref)),
            )
        return Fixture(LayoutWorkspaces.single(ws).copy(library = added.library), ref)
    }

    @Test
    fun `dependents lists every kind of site`() {
        val (layout, id) = fixture()
        val deps = LensLibraryOps.dependents(layout, id)
        assertEquals(
            listOf("list", "widget", null),
            deps.map { it.containerId?.value },
        )
        assertTrue(LensLibraryOps.dependents(layout, LensId("none")).isEmpty())
    }

    @Test
    fun `resolve prefers the library lens and falls back to the snapshot`() {
        val (layout, id) = fixture()
        val newer = llLens(limit = 2)
        val stale = llBinding(lens = llLens(limit = 9), ref = id)
        val library = layout.library.update(id, newer)
        assertEquals(newer, LensLibraryOps.resolve(library, stale).lens)
        val dangling = llBinding(lens = llLens(limit = 9), ref = LensId("gone"))
        assertEquals(dangling, LensLibraryOps.resolve(library, dangling))
    }

    @Test
    fun `previewEdit lists the dependents that would break and changes nothing`() {
        val (layout, id) = fixture()
        val impact = LensLibraryOps.previewEdit(layout, id, notes)
        assertEquals(3, impact.dependents.size)
        // Grouped lens: LIST and ICON_ROW accept only flat results.
        assertEquals(3, impact.wouldBreak.size)
        assertTrue(impact.wouldBreak.all { it.issues.isNotEmpty() })
        assertTrue(LensLibraryOps.previewEdit(layout, id, llLens(limit = 4)).isClean)
        assertEquals(layout.allBindings(), layout.allBindings())
    }

    @Test
    fun `applyEdit refreshes every dependent snapshot and the library together`() {
        val (layout, id) = fixture()
        val newLens = llLens(limit = 4)
        val applied = assertIs<LensLibraryResult.Applied>(LensLibraryOps.applyEdit(layout, id, newLens))
        assertEquals(newLens, applied.layout.library.find(id)?.lens)
        val refs = applied.layout.allBindings().filter { it.ref == id }
        assertEquals(3, refs.size)
        assertTrue(refs.all { it.lens == newLens })
        assertTrue(applied.layout.issues().isEmpty())
    }

    @Test
    fun `an edit that would break dependents is rejected with the impact unless broken ones are detached`() {
        val (layout, id) = fixture()
        val rejected = assertIs<LensLibraryResult.Rejected>(LensLibraryOps.applyEdit(layout, id, notes))
        val reason = assertIs<LensLibraryRejection.BreaksDependents>(rejected.reason)
        assertEquals(3, reason.impact.wouldBreak.size)

        val applied =
            assertIs<LensLibraryResult.Applied>(
                LensLibraryOps.applyEdit(layout, id, notes, BreakPolicy.DETACH_BROKEN),
            )
        assertEquals(3, applied.detached.size)
        assertTrue(applied.layout.allBindings().none { it.ref == id })
        assertTrue(applied.layout.issues().isEmpty())
        assertEquals(notes, applied.layout.library.find(id)?.lens)
    }

    @Test
    fun `unknown lens edits are rejected`() {
        val (layout, _) = fixture()
        val result = LensLibraryOps.applyEdit(layout, LensId("nope"), llLens())
        assertEquals(
            LensLibraryResult.Rejected(LensLibraryRejection.Problem(LibraryProblem.UNKNOWN_LENS)),
            result,
        )
    }

    @Test
    fun `remove with detach keeps every container drawing its lens`() {
        val (layout, id) = fixture(llLens(limit = 7))
        val applied = assertIs<LensLibraryResult.Applied>(LensLibraryOps.remove(layout, id))
        assertNull(applied.layout.library.find(id))
        assertTrue(applied.layout.allBindings().none { it.ref != null })
        assertEquals(layout.allBindings().map { it.lens }, applied.layout.allBindings().map { it.lens })
        assertTrue(LensLibraryOps.danglingRefs(applied.layout).isEmpty())
    }

    @Test
    fun `remove with replacement switches valid dependents and detaches the ones it would break`() {
        val (layout, id) = fixture()
        val withOther = (layout.library.tryAdd("Other", notes, llCounter("o")) as LibraryAdd.Added)
        val base = layout.copy(library = withOther.library)
        val applied =
            assertIs<LensLibraryResult.Applied>(
                LensLibraryOps.remove(base, id, RemovePolicy.ReplaceWith(withOther.id)),
            )
        // Grouped replacement cannot draw LIST or ICON_ROW, so everything is detached with its old lens.
        assertEquals(3, applied.detached.size)
        assertTrue(applied.layout.allBindings().none { it.ref == id || it.ref == withOther.id })
        assertTrue(applied.layout.issues().isEmpty())

        val flat = (base.library.tryAdd("Flat", llLens(limit = 2), llCounter("f")) as LibraryAdd.Added)
        val ok =
            assertIs<LensLibraryResult.Applied>(
                LensLibraryOps.remove(base.copy(library = flat.library), id, RemovePolicy.ReplaceWith(flat.id)),
            )
        assertTrue(ok.detached.isEmpty())
        assertEquals(3, ok.layout.allBindings().count { it.ref == flat.id && it.lens == llLens(limit = 2) })
    }

    @Test
    fun `remove with an unknown replacement or lens is rejected`() {
        val (layout, id) = fixture()
        assertIs<LensLibraryResult.Rejected>(LensLibraryOps.remove(layout, id, RemovePolicy.ReplaceWith(LensId("x"))))
        assertIs<LensLibraryResult.Rejected>(LensLibraryOps.remove(layout, LensId("x")))
        assertIs<LensLibraryResult.Rejected>(LensLibraryOps.remove(layout, id, RemovePolicy.ReplaceWith(id)))
    }

    @Test
    fun `dangling refs keep their snapshot and can be detached`() {
        val (layout, id) = fixture()
        val broken = layout.copy(library = layout.library.remove(id))
        val dangling = LensLibraryOps.danglingRefs(broken)
        assertEquals(3, dangling.size)
        assertTrue(dangling.all { it.ref == id })
        assertTrue(broken.issues().isEmpty())
        val hydrated = LensLibraryOps.rehydrate(broken)
        assertEquals(3, hydrated.dangling.size)
        assertEquals(broken.allBindings().map { it.lens }, hydrated.layout.allBindings().map { it.lens })
        assertTrue(LensLibraryOps.danglingRefs(LensLibraryOps.detachDangling(broken)).isEmpty())
    }

    @Test
    fun `rehydrate lets the library win over a stale snapshot`() {
        val (layout, id) = fixture()
        val stale = layout.copy(library = layout.library.update(id, llLens(limit = 2)))
        val hydrated = LensLibraryOps.rehydrate(stale)
        assertTrue(hydrated.dangling.isEmpty())
        assertTrue(hydrated.layout.allBindings().filter { it.ref == id }.all { it.lens == llLens(limit = 2) })
    }

    @Test
    fun `make independent detaches one workspace only`() {
        val (layout, id) = fixture()
        val copy = layout.add(layout.workspaces.single().copy(id = WorkspaceId("w2")))
        val independent = LensLibraryOps.makeIndependent(copy, WorkspaceId("w2"))
        assertTrue(
            independent.find(WorkspaceId("w2"))!!.let {
                WorkspaceBindings.sites(it).none {
                        s ->
                    s.binding.ref != null
                }
            },
        )
        assertEquals(3, WorkspaceBindings.sites(independent.find(WorkspaceId("w"))!!).count { it.binding.ref == id })
    }

    @Test
    fun `duplicating a workspace in a layout shares its saved lenses`() {
        val (layout, id) = fixture()
        val duplicated = layout.duplicate(layout.activeId, llCounter("d"))
        assertEquals(2, duplicated.workspaces.size)
        assertEquals(6, duplicated.allBindings().count { it.ref == id })
        assertEquals(layout.library, duplicated.library)
    }

    @Test
    fun `copy from another layout copies the library with fresh ids and rewrites refs`() {
        val (layout, id) = fixture()
        val extra = (layout.library.tryAdd("Unused", llLens(limit = 6), llCounter("u")) as LibraryAdd.Added).library
        val phone = HomeLayoutDeviceClass.PHONE
        val tablet = HomeLayoutDeviceClass.TABLET
        val set = WorkspaceSet(mapOf(phone to layout.copy(library = extra)))
        val copied = set.copyFromOtherLayout(phone, tablet, llCounter("c")).workspacesFor(tablet)

        assertEquals(listOf("Shared", "Unused"), copied.library.lenses.map { it.name })
        val sourceIds = extra.lenses.map { it.id }.toSet()
        assertTrue(copied.library.lenses.none { it.id in sourceIds })
        assertEquals(extra.lenses.map { it.lens }, copied.library.lenses.map { it.lens })
        val newId = copied.library.lenses.first { it.name == "Shared" }.id
        assertNotEquals(id, newId)
        assertEquals(3, copied.allBindings().count { it.ref == newId })
        assertTrue(copied.allBindings().none { it.ref == id })
        assertTrue(LensLibraryOps.danglingRefs(copied).isEmpty())
        // Editing the copy never touches the source layout.
        val edited = LensLibraryOps.applyEdit(copied, newId, llLens(limit = 2)) as LensLibraryResult.Applied
        assertEquals(llLens(), set.workspacesFor(phone).library.find(id)?.lens)
        assertEquals(llLens(limit = 2), edited.layout.library.find(newId)?.lens)
    }

    @Test
    fun `a binding never resolves against another layout's library`() {
        val (layout, id) = fixture()
        val other = LayoutWorkspaces.single(layout.workspaces.single())
        assertEquals(3, LensLibraryOps.danglingRefs(other).count { it.ref == id })
    }
}
