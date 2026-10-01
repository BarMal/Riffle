package com.riffle.core.domain.launcher.workspace.settings

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutCapabilities
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceCodec
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.WorkspaceSetCodec
import com.riffle.core.domain.launcher.workspace.preset.WorkspacePresets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspacesSettingsTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val foldable = HomeLayoutDeviceClass.FOLDABLE
    private val lens = Lens(sources = listOf(SourceId("apps.all")))

    private fun page(
        id: String,
        kind: ExpressionKind = ExpressionKind.LIST,
    ) = PageContainer(ContainerId(id), PageContent.Bound(LensBinding(lens, kind)))

    private fun ws(
        id: String,
        name: String = id,
        presetId: String? = null,
        kind: ExpressionKind = ExpressionKind.LIST,
    ) = Workspace(WorkspaceId(id), name, listOf(page("p-$id", kind)), presetId = presetId)

    private val a = ws("a", "Alpha", presetId = "nova")
    private val b = ws("b", "Beta")
    private val c = ws("c", "Gamma", presetId = "no-such-preset")

    private val set =
        WorkspaceSet(
            mapOf(
                phone to LayoutWorkspaces(listOf(a, b, c), WorkspaceId("b"), WorkspaceId("a")),
                foldable to LayoutWorkspaces.single(ws("f", "Fold")),
            ),
        )

    private class Counter : WorkspaceIdFactory {
        var n = 0

        override fun next() = "new-${n++}"
    }

    private val ids = Counter()

    private fun apply(
        action: WorkspacesSettingsAction,
        from: WorkspaceSet = set,
        layout: HomeLayoutDeviceClass = phone,
    ) = action.applyTo(from, layout, ids)

    @Test
    fun plannerMarksActiveDefaultAndWhatEachRowMayDo() {
        val model = WorkspacesSettingsPlanner.plan(set, phone, current = phone)

        assertEquals(listOf("a", "b", "c"), model.rows.map { it.id.value })
        assertEquals(listOf(false, true, false), model.rows.map { it.isActive })
        assertEquals(listOf(true, false, false), model.rows.map { it.isDefault })
        assertEquals(listOf(false, true, true), model.rows.map { it.canMoveUp })
        assertEquals(listOf(true, true, false), model.rows.map { it.canMoveDown })
        assertTrue(model.rows.all { it.canDelete })
        assertTrue(model.canEdit)
    }

    @Test
    fun resetIsOfferedOnlyForARecordedKnownPreset() {
        val rows = WorkspacesSettingsPlanner.plan(set, phone, current = phone).rows

        assertEquals(listOf("Nova", null, null), rows.map { it.presetName })
        assertEquals(listOf(true, false, false), rows.map { it.canReset })
    }

    @Test
    fun theLastWorkspaceCannotBeDeletedAndEditingNeedsTheCurrentLayout() {
        val model = WorkspacesSettingsPlanner.plan(set, foldable, current = phone)

        assertEquals(1, model.rows.size)
        assertFalse(model.rows.single().canDelete)
        assertFalse(model.canEdit)
        assertTrue(model.onlyOne)
    }

    @Test
    fun copySourcesAreTheOtherAvailableLayoutsInOrder() {
        val all = HomeLayoutDeviceClass.entries
        val model = WorkspacesSettingsPlanner.plan(set, foldable, phone, available = all)
        assertEquals(all.filter { it != foldable }, model.copySources.map { it.layout })
        assertEquals(3, model.copySources.first { it.layout == phone }.workspaceCount)

        val limited = WorkspacesSettingsPlanner.plan(set, phone, phone, available = listOf(phone, foldable))
        assertEquals(listOf(foldable), limited.copySources.map { it.layout })
    }

    @Test
    fun noFallbackNoticeWhenTheActiveWorkspaceCanBeDrawn() {
        assertNull(WorkspacesSettingsPlanner.plan(set, phone, phone).fallback)
    }

    @Test
    fun fallbackNoticeNamesTheExpressionTheLayoutCannotDraw() {
        val index = ws("i", "Index", kind = ExpressionKind.INDEX)
        val layout = LayoutWorkspaces(listOf(a, index), WorkspaceId("i"), WorkspaceId("a"))
        val capabilities = LayoutCapabilities(ExpressionKind.entries.toSet() - ExpressionKind.INDEX)

        val notice =
            WorkspacesSettingsPlanner.plan(
                WorkspaceSet(mapOf(phone to layout)),
                phone,
                phone,
                capabilities = capabilities,
            )
                .fallback

        assertEquals("Index", notice?.requestedName)
        assertEquals("Alpha", notice?.shownName)
        assertEquals(listOf(ExpressionKind.INDEX), notice?.unsupported)
        assertEquals(0, notice?.otherIssues)
    }

    @Test
    fun switchingRenamingMovingAndMakingDefaultAreSingleSteps() {
        assertEquals(
            WorkspaceId("c"),
            apply(WorkspacesSettingsAction.Activate(WorkspaceId("c"))).set.workspacesFor(phone).activeId,
        )
        assertEquals(
            "Renamed",
            apply(
                WorkspacesSettingsAction.Rename(WorkspaceId("b"), "  Renamed "),
            ).set.workspacesFor(phone).find(WorkspaceId("b"))?.name,
        )
        assertEquals(
            listOf("b", "a", "c"),
            apply(
                WorkspacesSettingsAction.Move(WorkspaceId("b"), -1),
            ).set.workspacesFor(phone).workspaces.map { it.id.value },
        )
        assertEquals(
            WorkspaceId("c"),
            apply(WorkspacesSettingsAction.MakeDefault(WorkspaceId("c"))).set.workspacesFor(phone).defaultId,
        )
    }

    @Test
    fun anActionThatChangesNothingIsNotApplied() {
        val activeAgain = apply(WorkspacesSettingsAction.Activate(WorkspaceId("b")))
        assertFalse(activeAgain.applied)
        assertEquals(set, activeAgain.set)

        assertFalse(apply(WorkspacesSettingsAction.Move(WorkspaceId("a"), -1)).applied)
        assertFalse(apply(WorkspacesSettingsAction.Activate(WorkspaceId("missing"))).applied)

        val blank = apply(WorkspacesSettingsAction.Rename(WorkspaceId("a"), "   "))
        assertFalse(blank.applied)
        assertEquals(WorkspacesSettingsMessage.InvalidName, blank.message)
    }

    @Test
    fun duplicateInsertsACopyWithFreshIdsAfterTheOriginal() {
        val change = apply(WorkspacesSettingsAction.Duplicate(WorkspaceId("a")))

        val layout = change.set.workspacesFor(phone)
        assertEquals(listOf("a", "new-0", "b", "c"), layout.workspaces.map { it.id.value })
        assertEquals("Alpha copy", layout.workspaces[1].name)
        assertNotEquals(layout.workspaces[0].pages.single().id, layout.workspaces[1].pages.single().id)
        assertEquals("nova", layout.workspaces[1].presetId)
        assertEquals(WorkspaceId("b"), layout.activeId)
    }

    @Test
    fun deleteRemovesMovesActiveAndDefaultAndCanBeUndoneExactly() {
        val change = apply(WorkspacesSettingsAction.Delete(WorkspaceId("a")))

        val layout = change.set.workspacesFor(phone)
        assertEquals(listOf("b", "c"), layout.workspaces.map { it.id.value })
        assertEquals(WorkspaceId("b"), layout.defaultId)
        assertEquals(WorkspacesSettingsMessage.Deleted("Alpha"), change.message)
        assertTrue(change.undoable)

        assertEquals(set, change.undo(change.set, phone))
    }

    @Test
    fun undoRestoresOnlyTheChangedLayout() {
        val change = apply(WorkspacesSettingsAction.Delete(WorkspaceId("a")))
        val edited = change.set.update(foldable) { it.rename(WorkspaceId("f"), "Edited") }

        val undone = change.undo(edited, phone)

        assertEquals(set.workspacesFor(phone), undone.workspacesFor(phone))
        assertEquals("Edited", undone.workspacesFor(foldable).active.name)
    }

    @Test
    fun theLastWorkspaceIsNeverDeleted() {
        val single = WorkspaceSet(mapOf(phone to LayoutWorkspaces.single(a)))

        val change = apply(WorkspacesSettingsAction.Delete(WorkspaceId("a")), from = single)

        assertFalse(change.applied)
        assertEquals(WorkspacesSettingsMessage.CannotDeleteLast, change.message)
        assertEquals(single, change.set)
    }

    @Test
    fun nonDestructiveActionsAreNotUndoable() {
        assertFalse(apply(WorkspacesSettingsAction.Activate(WorkspaceId("c"))).undoable)
        assertFalse(apply(WorkspacesSettingsAction.Rename(WorkspaceId("c"), "x")).undoable)
        assertEquals(set, apply(WorkspacesSettingsAction.Rename(WorkspaceId("c"), "x")).undo(set, phone))
    }

    @Test
    fun resetReinstallsTheRecordedPresetKeepingIdAndNameAndIsUndoable() {
        val change = apply(WorkspacesSettingsAction.ResetToPreset(WorkspaceId("a")))

        val reset = change.set.workspacesFor(phone).find(WorkspaceId("a"))
        assertEquals("Alpha", reset?.name)
        assertEquals("nova", reset?.presetId)
        assertEquals(WorkspacePresets.nova.compact.pages.size, reset?.pages?.size)
        assertEquals(WorkspacesSettingsMessage.Reset("Alpha", "Nova"), change.message)
        assertEquals(set, change.undo(change.set, phone))
    }

    @Test
    fun resetUsesTheViewedLayoutsPosture() {
        val expanded = WorkspaceSet(mapOf(foldable to LayoutWorkspaces.single(ws("f", "Fold", presetId = "nova"))))

        val change = apply(WorkspacesSettingsAction.ResetToPreset(WorkspaceId("f")), expanded, foldable)

        assertEquals(
            WorkspacePresets.nova.expanded.pages.size,
            change.set.workspacesFor(foldable).active.pages.size,
        )
    }

    @Test
    fun resetWithoutARecordedPresetDoesNothing() {
        listOf("b", "c").forEach { id ->
            val change = apply(WorkspacesSettingsAction.ResetToPreset(WorkspaceId(id)))
            assertFalse(change.applied, id)
            assertEquals(WorkspacesSettingsMessage.NoKnownPreset, change.message)
            assertEquals(set, change.set)
        }
    }

    @Test
    fun installPresetAddsAStampedCopyAndOptionallyActivatesIt() {
        val change = apply(WorkspacesSettingsAction.InstallPreset("ios", activate = true))

        val layout = change.set.workspacesFor(phone)
        val installed = layout.workspaces.last()
        assertEquals("iOS", installed.name)
        assertEquals("ios", installed.presetId)
        assertEquals(installed.id, layout.activeId)
        assertEquals(4, layout.workspaces.size)
        assertFalse(change.undoable)
    }

    @Test
    fun installingThePresetTwiceGivesDistinctNames() {
        val once = apply(WorkspacesSettingsAction.InstallPreset("nova", activate = false)).set
        val twice = apply(WorkspacesSettingsAction.InstallPreset("nova", activate = false), once).set

        assertEquals(
            listOf("Alpha", "Beta", "Gamma", "Nova", "Nova 2"),
            twice.workspacesFor(phone).workspaces.map { it.name },
        )
        assertEquals(WorkspaceId("b"), twice.workspacesFor(phone).activeId)
    }

    @Test
    fun everyCatalogPresetInstallsAsAValidWorkspace() {
        WorkspacePresets.all.forEach { preset ->
            val change = apply(WorkspacesSettingsAction.InstallPreset(preset.id, activate = true))
            assertTrue(change.applied, preset.id)
        }
        assertFalse(apply(WorkspacesSettingsAction.InstallPreset("nope", activate = true)).applied)
    }

    @Test
    fun copyFromOtherLayoutReplacesWithFreshCopiesAndUndoRestores() {
        val change = apply(WorkspacesSettingsAction.CopyFromLayout(phone), layout = foldable)

        val copied = change.set.workspacesFor(foldable)
        assertEquals(listOf("Alpha", "Beta", "Gamma"), copied.workspaces.map { it.name })
        assertEquals("Beta", copied.active.name)
        assertEquals("Alpha", copied.default.name)
        val original = set.workspacesFor(phone).workspaces.map { it.id }.toSet()
        assertTrue(copied.workspaces.none { it.id in original })
        assertEquals(set.workspacesFor(phone), change.set.workspacesFor(phone))
        assertEquals(WorkspacesSettingsMessage.Copied(phone, replaced = 1, copied = 3), change.message)

        assertEquals(set, change.undo(change.set, foldable))
    }

    @Test
    fun copyingFromTheSameLayoutIsNotApplied() {
        assertFalse(apply(WorkspacesSettingsAction.CopyFromLayout(phone), layout = phone).applied)
    }

    @Test
    fun workspacesNeverHoldPlacedItemsSoNoActionCanTouchThem() {
        // A workspace holds containers and lenses only; there is no item content anywhere in the model.
        val change = apply(WorkspacesSettingsAction.Delete(WorkspaceId("a")))
        assertIs<WorkspacesSettingsMessage.Deleted>(change.message)
        assertEquals(setOf(phone, foldable), change.set.layouts.keys)
    }

    @Test
    fun presetOriginRoundTripsThroughTheCodecAndIsOmittedWhenAbsent() {
        val withPreset = ws("x", presetId = "ios")
        assertEquals("ios", WorkspaceCodec.decodeWorkspace(WorkspaceCodec.encode(withPreset))?.presetId)

        val without = ws("y")
        assertNull(WorkspaceCodec.decodeWorkspace(WorkspaceCodec.encode(without))?.presetId)
        assertFalse(WorkspaceCodec.encode(without).fields.containsKey("preset"))

        val set = WorkspaceSet(mapOf(phone to LayoutWorkspaces.single(withPreset)))
        assertEquals(set, WorkspaceSetCodec.decode(WorkspaceSetCodec.encode(set)))
    }
}
