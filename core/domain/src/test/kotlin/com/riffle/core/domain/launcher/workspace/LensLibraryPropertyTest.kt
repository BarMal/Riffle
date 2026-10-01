package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.editor.FlowMode
import com.riffle.core.domain.launcher.workspace.editor.LensLibraryEditor
import com.riffle.core.domain.launcher.workspace.editor.LibraryEditResult
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Seeded random operation sequences over a layout with every kind of binding site. */
class LensLibraryPropertyTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val tablet = HomeLayoutDeviceClass.TABLET
    private val wids = listOf(WorkspaceId("w1"), WorkspaceId("w2"))
    private val targets =
        listOf(
            { ws: String -> FlowMode.EditPage(ContainerId("$ws-list")) },
            { ws: String -> FlowMode.EditPage(ContainerId("$ws-set")) },
            { ws: String -> FlowMode.EditWidget(ContainerId("$ws-grid"), ContainerId("$ws-widget")) },
            { _: String -> FlowMode.EditDock },
        )
    private val lenses =
        listOf(
            llLens(),
            llLens(limit = 2),
            llLens(limit = 1),
            llLens(group = LensGroup.ByGroupKey),
            llLens(LL_NOTES, group = LensGroup.ByGroupKey),
            llLens(LL_NOTES, limit = 4),
        )

    private fun check(layout: LayoutWorkspaces) {
        assertTrue(LensLibraryOps.danglingRefs(layout).isEmpty(), "dangling refs")
        assertTrue(layout.issues().isEmpty(), "workspaces must stay valid: ${layout.issues()}")
        layout.workspaces.forEach { ws ->
            WorkspaceBindings.sites(ws).forEach { site ->
                val ref = site.binding.ref
                if (ref != null) assertEquals(layout.library.find(ref)?.lens, site.binding.lens, "snapshot")
            }
        }
        val names = layout.library.lenses.map { it.name.lowercase() }
        assertEquals(names.size, names.toSet().size, "unique names")
        assertEquals(layout.library.lenses.size, layout.library.lenses.map { it.id }.toSet().size, "unique ids")
        assertTrue(layout.library.lenses.size <= MAX_SAVED_LENSES)
    }

    private fun step(
        layout: LayoutWorkspaces,
        random: Random,
        ids: WorkspaceIdFactory,
    ): LayoutWorkspaces {
        val ws = wids[random.nextInt(wids.size)]
        val target = targets[random.nextInt(targets.size)](ws.value)
        val saved = layout.library.lenses
        val pick = if (saved.isEmpty()) LensId("none") else saved[random.nextInt(saved.size)].id
        val lens = lenses[random.nextInt(lenses.size)]
        val applied =
            when (random.nextInt(10)) {
                0, 1 -> LensLibraryEditor.saveAsLens(layout, ws, target, "L${random.nextInt(6)}", ids)
                2, 3 -> LensLibraryEditor.useSavedLens(layout, ws, target, pick)
                4 -> LensLibraryEditor.editSavedLens(layout, pick, lens)
                5 -> LensLibraryEditor.editSavedLens(layout, pick, lens, BreakPolicy.DETACH_BROKEN)
                6 -> LensLibraryEditor.detach(layout, ws, target)
                else -> null
            }
        return when {
            applied is LibraryEditResult.Applied -> applied.layout
            applied != null -> layout
            else -> structural(layout, random, ids, pick)
        }
    }

    private fun structural(
        layout: LayoutWorkspaces,
        random: Random,
        ids: WorkspaceIdFactory,
        pick: LensId,
    ): LayoutWorkspaces =
        when (random.nextInt(4)) {
            0 -> removeResult(layout, pick, RemovePolicy.Detach)
            1 -> {
                val other = layout.library.lenses.randomOrNull(random)?.id ?: pick
                removeResult(layout, pick, RemovePolicy.ReplaceWith(other))
            }
            2 -> layout.copy(library = layout.library.duplicate(pick, ids).rename(pick, "R${random.nextInt(5)}"))
            else -> LensLibraryOps.makeIndependent(layout, wids[random.nextInt(wids.size)])
        }

    private fun removeResult(
        layout: LayoutWorkspaces,
        id: LensId,
        policy: RemovePolicy,
    ): LayoutWorkspaces = (LensLibraryOps.remove(layout, id, policy) as? LensLibraryResult.Applied)?.layout ?: layout

    @Test
    fun `no dangling refs, valid workspaces and fresh snapshots after any operation sequence`() {
        for (seed in 0 until 150) {
            val random = Random(seed)
            val ids = llCounter("s$seed")
            var layout = llLayout()
            check(layout)
            repeat(40) {
                layout = step(layout, random, ids)
                check(layout)
            }
        }
    }

    @Test
    fun `copying a layout after any sequence keeps the invariants and shares no ids`() {
        for (seed in 0 until 60) {
            val random = Random(1000 + seed)
            val ids = llCounter("c$seed")
            var layout = llLayout()
            repeat(30) { layout = step(layout, random, ids) }
            val set = WorkspaceSet(mapOf(phone to layout))
            val copied = set.copyFromOtherLayout(phone, tablet, llCounter("t$seed")).workspacesFor(tablet)
            check(copied)
            val sourceIds = layout.library.lenses.map { it.id }.toSet()
            assertTrue(copied.library.lenses.none { it.id in sourceIds })
            assertEquals(layout.library.lenses.map { it.lens }, copied.library.lenses.map { it.lens })
            assertEquals(layout.allBindings().map { it.lens }, copied.allBindings().map { it.lens })
            assertEquals(
                layout.allBindings().count { it.ref != null },
                copied.allBindings().count { it.ref != null },
            )
        }
    }

    @Test
    fun `codec round trips every reachable layout`() {
        for (seed in 0 until 60) {
            val random = Random(5000 + seed)
            val ids = llCounter("k$seed")
            var layout = llLayout()
            repeat(30) { layout = step(layout, random, ids) }
            val set = WorkspaceSet(mapOf(phone to layout))
            assertEquals(set, WorkspaceSetCodec.decode(WorkspaceSetCodec.encode(set)))
        }
    }
}
