package com.riffle.core.domain.launcher.workspace.settings

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.BreakPolicy
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.LL_APPS
import com.riffle.core.domain.launcher.workspace.LL_NOTES
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LensLibraryOps
import com.riffle.core.domain.launcher.workspace.MAX_SAVED_LENSES
import com.riffle.core.domain.launcher.workspace.MAX_SAVED_LENS_NAME
import com.riffle.core.domain.launcher.workspace.RemovePolicy
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.WorkspaceBindings
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.WorkspaceSetCodec
import com.riffle.core.domain.launcher.workspace.editor.FlowMode
import com.riffle.core.domain.launcher.workspace.editor.LensLibraryEditor
import com.riffle.core.domain.launcher.workspace.editor.LibraryEditResult
import com.riffle.core.domain.launcher.workspace.issues
import com.riffle.core.domain.launcher.workspace.llCounter
import com.riffle.core.domain.launcher.workspace.llLayout
import com.riffle.core.domain.launcher.workspace.llLens
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Seeded random sequences of every action the Saved lenses page has, over layouts whose containers use saved
 * lenses at every kind of site. After each step both layouts must still satisfy the library invariants, and an
 * Undo taken straight after a step must restore the previous set exactly.
 */
class LensesSettingsPropertyTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val tablet = HomeLayoutDeviceClass.TABLET
    private val descriptors =
        listOf(
            SourceDescriptor(LL_APPS, setOf(SourceCapability.GROUPABLE)),
            SourceDescriptor(LL_NOTES, setOf(SourceCapability.GROUPABLE)),
        )
    private val lenses =
        listOf(
            llLens(),
            llLens(limit = 2),
            llLens(limit = 1),
            llLens(group = LensGroup.ByGroupKey),
            llLens(LL_NOTES, group = LensGroup.ByGroupKey),
            llLens(LL_NOTES),
            llLens(LL_NOTES, limit = 4),
        )
    private val names = listOf("A", "b", "Notes", "x".repeat(40), "x".repeat(41), "", " a ", "Lens 7")

    private fun seeded(ids: WorkspaceIdFactory): WorkspaceSet {
        val targets =
            listOf(
                FlowMode.EditPage(ContainerId("w1-list")),
                FlowMode.EditPage(ContainerId("w1-set")),
                FlowMode.EditWidget(ContainerId("w1-grid"), ContainerId("w1-widget")),
                FlowMode.EditDock,
            )
        var layout = llLayout()
        targets.forEachIndexed { index, target ->
            val saved = LensLibraryEditor.saveAsLens(layout, WorkspaceId("w1"), target, "Seed $index", ids)
            if (saved is LibraryEditResult.Applied) layout = saved.layout
        }
        return WorkspaceSet(mapOf(phone to layout, tablet to llLayout()))
    }

    private fun check(layout: LayoutWorkspaces) {
        assertTrue(LensLibraryOps.danglingRefs(layout).isEmpty(), "dangling refs")
        assertTrue(layout.issues().isEmpty(), "workspaces must stay valid: ${layout.issues()}")
        layout.workspaces.forEach { ws ->
            WorkspaceBindings.sites(ws).forEach { site ->
                site.binding.ref?.let { ref ->
                    assertEquals(layout.library.find(ref)?.lens, site.binding.lens, "snapshot")
                }
            }
        }
        val lowered = layout.library.lenses.map { it.name.lowercase() }
        assertEquals(lowered.size, lowered.toSet().size, "unique names")
        assertTrue(layout.library.lenses.all { it.name == it.name.trim() && it.name.length in 1..MAX_SAVED_LENS_NAME })
        assertEquals(layout.library.lenses.size, layout.library.lenses.map { it.id }.toSet().size, "unique ids")
        assertTrue(layout.library.lenses.size <= MAX_SAVED_LENSES)
    }

    private fun randomAction(
        set: WorkspaceSet,
        viewed: HomeLayoutDeviceClass,
        other: HomeLayoutDeviceClass,
        random: Random,
    ): LensesSettingsAction {
        val saved = set.workspacesFor(viewed).library.lenses
        val id =
            if (saved.isEmpty() || random.nextInt(12) == 0) {
                LensId(
                    "none",
                )
            } else {
                saved[random.nextInt(saved.size)].id
            }
        val lens = lenses[random.nextInt(lenses.size)]
        val name = names[random.nextInt(names.size)]
        return when (random.nextInt(8)) {
            0 -> LensesSettingsAction.Create(name, lens)
            1 -> LensesSettingsAction.Save(id, name, lens)
            2 -> LensesSettingsAction.Save(id, name, lens, BreakPolicy.DETACH_BROKEN)
            3 -> LensesSettingsAction.Rename(id, name)
            4 -> LensesSettingsAction.Duplicate(id)
            5 -> LensesSettingsAction.Delete(id)
            6 ->
                LensesSettingsAction.Delete(
                    id,
                    RemovePolicy.ReplaceWith(saved.randomOrNull(random)?.id ?: LensId("none")),
                )
            else -> LensesSettingsAction.CopyToLayout(id, other)
        }
    }

    @Test
    fun everyActionSequenceKeepsBothLayoutsValidAndEveryUndoIsExact() {
        for (seed in 0 until 150) {
            val random = Random(seed)
            val ids = llCounter("s$seed")
            val env = LensesEnvironment(descriptors, ids = ids)
            var set = seeded(llCounter("seed$seed"))
            check(set.workspacesFor(phone))
            repeat(40) {
                val viewed = if (random.nextBoolean()) phone else tablet
                val other = if (viewed == phone) tablet else phone
                val action = randomAction(set, viewed, other, random)
                val change = action.applyTo(set, viewed, env)
                if (!change.applied) assertEquals(set, change.set, "a refused action changes nothing: $action")
                if (change.undoable) assertEquals(set, change.undo(change.set), "undo of $action")
                set = change.set
                check(set.workspacesFor(phone))
                check(set.workspacesFor(tablet))
            }
        }
    }

    @Test
    fun aCreatedOrDuplicatedOrCopiedLensIsAlwaysFreshAndNeverShared() {
        for (seed in 0 until 60) {
            val random = Random(2000 + seed)
            val env = LensesEnvironment(descriptors, ids = llCounter("f$seed"))
            var set = seeded(llCounter("fs$seed"))
            repeat(30) {
                val action = randomAction(set, phone, tablet, random)
                val change = action.applyTo(set, phone, env)
                val created = change.lensId
                if (created != null) {
                    val inPhone = set.workspacesFor(phone).library.find(created)
                    val inTablet = set.workspacesFor(tablet).library.find(created)
                    assertTrue(inPhone == null && inTablet == null, "a new id was already in use: $action")
                }
                set = change.set
            }
            val phoneIds = set.workspacesFor(phone).library.lenses.map { it.id }.toSet()
            val tabletIds = set.workspacesFor(tablet).library.lenses.map { it.id }.toSet()
            assertTrue(phoneIds.intersect(tabletIds).isEmpty(), "no id is shared between layouts")
            // No workspace of one layout refers to a lens of the other.
            assertTrue(
                set.workspacesFor(tablet).workspaces.flatMap { WorkspaceBindings.sites(it) }.all { site ->
                    site.binding.ref == null || site.binding.ref in tabletIds
                },
            )
        }
    }

    @Test
    fun theCodecRoundTripsEveryReachableSet() {
        for (seed in 0 until 40) {
            val random = Random(7000 + seed)
            val env = LensesEnvironment(descriptors, ids = llCounter("k$seed"))
            var set = seeded(llCounter("ks$seed"))
            repeat(30) {
                set = randomAction(set, phone, tablet, random).applyTo(set, phone, env).set
            }
            assertEquals(set, WorkspaceSetCodec.decode(WorkspaceSetCodec.encode(set)))
        }
    }

    @Test
    fun theDetailPlannerNeverSaysCanSaveForADraftTheActionWouldRefuse() {
        for (seed in 0 until 80) {
            val random = Random(9000 + seed)
            val set = seeded(llCounter("d$seed"))
            val layout = set.workspacesFor(phone)
            val saved = layout.library.lenses[random.nextInt(layout.library.lenses.size)]
            val lens = lenses[random.nextInt(lenses.size)]
            val name = names[random.nextInt(names.size)]
            val detail = LensDetailPlanner.plan(layout, saved.id, name, lens, descriptors)
            val env = LensesEnvironment(descriptors, ids = llCounter("e$seed"))
            val policy = if (detail.needsChoice) BreakPolicy.DETACH_BROKEN else BreakPolicy.REJECT
            val change = LensesSettingsAction.Save(saved.id, name, lens, policy).applyTo(set, phone, env)
            if (detail.canSave) assertTrue(change.applied, "planner allowed it: ${detail.problems} $name")
            if (!detail.canSave) assertTrue(!change.applied || !detail.changed, "planner refused it but it applied")
            if (change.applied) check(change.set.workspacesFor(phone))
        }
    }
}
