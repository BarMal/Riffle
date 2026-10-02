package com.riffle.app.launcher.workspace

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.BreakPolicy
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LensLibrary
import com.riffle.core.domain.launcher.workspace.LibraryProblem
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.RemovePolicy
import com.riffle.core.domain.launcher.workspace.SavedLens
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.editor.LensDraftAction
import com.riffle.core.domain.launcher.workspace.settings.LensProblem
import com.riffle.core.domain.launcher.workspace.settings.LensesSettingsAction
import com.riffle.core.domain.launcher.workspace.settings.SourceStatus
import com.riffle.core.domain.launcher.workspace.testing.InMemoryWorkspaceRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LensesSettingsControllerTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val tablet = HomeLayoutDeviceClass.TABLET
    private val apps = SourceId("apps")
    private val notes = SourceId("notifications")
    private val descriptors =
        listOf(
            SourceDescriptor(apps, setOf(SourceCapability.GROUPABLE)),
            SourceDescriptor(notes, setOf(SourceCapability.GROUPABLE)),
        )
    private val flat = Lens(listOf(apps))
    private val grouped = Lens(listOf(notes), group = LensGroup.ByGroupKey)
    private val first = SavedLens(LensId("l1"), "Everything", flat)
    private val second = SavedLens(LensId("l2"), "Notes by app", grouped)
    private val initial =
        WorkspaceSet(
            mapOf(
                phone to
                    LayoutWorkspaces.single(Workspace(WorkspaceId("w"), "Work"))
                        .copy(library = LensLibrary(listOf(first, second))),
                tablet to LayoutWorkspaces.single(Workspace(WorkspaceId("t"), "Tab")),
            ),
        )
    private val repository = InMemoryWorkspaceRepository(initial)
    private var changes = 0
    private var counter = 0
    private val controller =
        LensesSettingsController(
            repository = repository,
            descriptors = { descriptors },
            ids = WorkspaceIdFactory { "id-${counter++}" },
            onChanged = { changes++ },
        )

    private fun library(device: HomeLayoutDeviceClass = phone) =
        checkNotNull(repository.load()).workspacesFor(device).library

    private val choices get() = controller.queries.sourceChoices(emptyMap(), emptySet())

    @Test
    fun theModelIsNullUntilTheRepositoryHasLoaded() {
        val empty = LensesSettingsController(InMemoryWorkspaceRepository(null), { descriptors })

        assertNull(empty.queries.model(phone))
        assertEquals(listOf("Everything", "Notes by app"), controller.queries.model(phone)?.rows?.map { it.name })
        assertEquals(emptyList<LensId>(), controller.queries.model(tablet)?.rows?.map { it.id })
    }

    @Test
    fun anActionIsPersistedThroughTheRepositoryAndAnnounced() {
        val change = controller.dispatch(phone, LensesSettingsAction.Rename(first.id, "All apps"))

        assertTrue(change?.applied == true)
        assertEquals("All apps", library().find(first.id)?.name)
        assertEquals(1, changes)
        assertEquals("Renamed to \"All apps\"", controller.feedback.value?.message)
        assertFalse(controller.feedback.value?.canUndo ?: true)
    }

    @Test
    fun aRefusedActionStoresNothingAndSaysWhy() {
        val change = controller.dispatch(phone, LensesSettingsAction.Rename(first.id, "notes BY app"))

        assertFalse(change?.applied ?: true)
        assertEquals(initial, repository.load())
        assertEquals(0, changes)
        assertEquals("Another saved lens already has that name.", controller.feedback.value?.message)
    }

    @Test
    fun deletingOffersUndoThatRestoresTheLayoutExactly() {
        controller.dispatch(phone, LensesSettingsAction.Delete(first.id))

        assertNull(library().find(first.id))
        assertTrue(controller.feedback.value?.canUndo ?: false)

        controller.undo()

        assertEquals(initial, repository.load())
        assertEquals("Undone", controller.feedback.value?.message)
        assertFalse(controller.feedback.value?.canUndo ?: true)
        assertEquals(2, changes)
    }

    @Test
    fun aLaterChangeEndsTheUndoOfferSoItCanNeverOverwriteSomethingNewer() {
        controller.dispatch(phone, LensesSettingsAction.Delete(first.id))
        controller.dispatch(phone, LensesSettingsAction.Rename(second.id, "Renamed"))
        val after = repository.load()

        controller.undo()

        assertEquals(after, repository.load())
    }

    @Test
    fun dismissingTheAnnouncementEndsTheOffer() {
        controller.dispatch(phone, LensesSettingsAction.Delete(first.id))
        controller.feedbackShown(checkNotNull(controller.feedback.value).id)
        val after = repository.load()

        controller.undo()

        assertEquals(after, repository.load())
        assertNull(controller.feedback.value)
    }

    @Test
    fun copyingToAnotherLayoutChangesOnlyThatLayoutAndUndoRestoresIt() {
        controller.dispatch(phone, LensesSettingsAction.CopyToLayout(second.id, tablet))

        assertEquals(listOf("Notes by app"), library(tablet).lenses.map { it.name })
        assertEquals(initial.workspacesFor(phone), checkNotNull(repository.load()).workspacesFor(phone))
        assertTrue(controller.feedback.value?.message?.contains("Tablet") == true)

        controller.undo()

        assertEquals(initial, repository.load())
    }

    @Test
    fun theBuilderOpensASavedLensEditsItThroughTheSharedReducerAndPlansTheDetail() {
        assertTrue(controller.builder.open(phone, first.id))
        assertEquals(flat, controller.builder.session.value?.lens)

        controller.builder.edit(LensDraftAction.SetLimit(4), choices)

        assertEquals(4, controller.builder.session.value?.lens?.limit)
        val detail = checkNotNull(controller.queries.detail(phone, controller.builder.session.value, "Everything"))
        assertTrue(detail.canSave)
        assertFalse(detail.isNew)
        // The stored lens is untouched until Save.
        assertEquals(flat, library().find(first.id)?.lens)
    }

    @Test
    fun anUnknownLensDoesNotOpen() {
        assertFalse(controller.builder.open(phone, LensId("nope")))
        assertNull(controller.builder.session.value)
        assertNull(controller.queries.detail(phone, controller.builder.session.value, "x"))
    }

    @Test
    fun aNewLensStartsEmptyIsBlockedUntilItHasASourceAndThenOpensOnTheCreatedLens() {
        controller.builder.startNew(phone)
        val empty = checkNotNull(controller.queries.detail(phone, controller.builder.session.value, "Mine"))
        assertTrue(empty.isNew)
        assertEquals(listOf(LensProblem.NoSource), empty.problems)

        controller.builder.edit(LensDraftAction.ToggleSource(notes), choices)
        val lens = checkNotNull(controller.builder.session.value?.lens)
        controller.dispatch(phone, LensesSettingsAction.Create("Mine", lens))

        val opened = checkNotNull(controller.builder.session.value)
        assertNotNull(opened.id)
        assertEquals("Mine", opened.baseline?.name)
        assertEquals(lens, library().find(checkNotNull(opened.id))?.lens)
    }

    @Test
    fun savingKeepsTheBuilderOpenOnTheSavedLensAndUndoPutsTheOldOneBack() {
        controller.builder.open(phone, first.id)
        controller.builder.edit(LensDraftAction.SetLimit(2), choices)
        val edited = checkNotNull(controller.builder.session.value?.lens)

        controller.dispatch(phone, LensesSettingsAction.Save(first.id, "Everything", edited))

        assertEquals(edited, library().find(first.id)?.lens)
        assertEquals(edited, controller.builder.session.value?.baseline?.lens)
        assertFalse(
            checkNotNull(controller.queries.detail(phone, controller.builder.session.value, "Everything")).changed,
        )

        controller.undo()

        assertEquals(flat, library().find(first.id)?.lens)
        assertEquals(flat, controller.builder.session.value?.lens)
    }

    @Test
    fun deletingTheLensBeingBuiltClosesTheBuilder() {
        controller.builder.open(phone, first.id)

        controller.dispatch(phone, LensesSettingsAction.Delete(first.id, RemovePolicy.Detach))

        assertNull(controller.builder.session.value)
    }

    @Test
    fun savingAChangeThatBreaksAContainerNeedsAnExplicitChoice() {
        val bound = bindPageSet()
        controller.builder.open(phone, second.id)
        controller.builder.edit(LensDraftAction.SetGroup(LensGroup.None), choices)
        val flatNotes = checkNotNull(controller.builder.session.value?.lens)

        val detail = checkNotNull(controller.queries.detail(phone, controller.builder.session.value, "Notes by app"))
        assertTrue(detail.needsChoice)
        assertEquals(1, detail.broken.size)

        val refused = controller.dispatch(phone, LensesSettingsAction.Save(second.id, "Notes by app", flatNotes))
        assertFalse(refused?.applied ?: true)
        assertEquals(bound, repository.load())

        val detached =
            controller.dispatch(
                phone,
                LensesSettingsAction.Save(second.id, "Notes by app", flatNotes, BreakPolicy.DETACH_BROKEN),
            )
        assertTrue(detached?.applied == true)
    }

    @Test
    fun leavingThePageDropsTheBuilderAnyAnnouncementAndTheUndoOffer() {
        controller.builder.open(phone, first.id)
        controller.dispatch(phone, LensesSettingsAction.Delete(second.id))

        controller.leave()

        assertNull(controller.builder.session.value)
        assertNull(controller.feedback.value)
        val after = repository.load()
        controller.undo()
        assertEquals(after, repository.load())
    }

    @Test
    fun theBuildersSourceRowsReportStatusesWithoutAskingForAnything() {
        val rows =
            controller.queries.sourceChoices(
                statuses = mapOf(notes to SourceStatus.NEEDS_PERMISSION),
                disabled = setOf(apps),
            )

        assertEquals(SourceStatus.OFF, rows.first { it.id == apps }.status)
        assertEquals(SourceStatus.NEEDS_PERMISSION, rows.first { it.id == notes }.status)
    }

    @Test
    fun copyTargetsAndReplacementsComeFromTheStoredLibrary() {
        assertEquals(1, controller.queries.copyTargets(phone, first.id, listOf(phone, tablet)).size)
        assertEquals(listOf("Notes by app"), controller.queries.replacements(phone, first.id).map { it.name })
    }

    @Test
    fun nameProblemsAreReportedForTheLayoutBeingViewed() {
        assertNull(controller.queries.nameProblem(phone, first.id, "Everything"))
        assertEquals(LibraryProblem.NAME_TAKEN, controller.queries.nameProblem(phone, first.id, "notes by app"))
        assertEquals(LibraryProblem.BLANK_NAME, controller.queries.nameProblem(phone, null, " "))
        assertNull(controller.queries.nameProblem(tablet, null, "notes by app"))
    }

    @Test
    fun theDryRunOfAReplacementCountsTheContainersItCannotServe() {
        bindPageSet()
        // The page-set needs a grouped lens: a flat replacement cannot serve it.
        assertEquals(1, controller.queries.replacementImpact(phone, second.id, first.id))
        assertEquals(0, controller.queries.replacementImpact(phone, first.id, second.id))
    }

    @Test
    fun theCopyNameFollowsTheSessionsLensAndTheTypedName() {
        controller.builder.open(phone, first.id)
        val session = controller.builder.session.value

        assertEquals("Everything copy", controller.queries.copyName(phone, session, "Everything"))
        assertEquals("Fresh", controller.queries.copyName(phone, session, "Fresh"))
        assertEquals(phone, controller.builder.layout)
        controller.builder.close()
        assertNull(controller.builder.layout)
    }

    /** Puts the grouped lens behind a page-set so that making it flat would break that container. */
    private fun bindPageSet(): WorkspaceSet {
        val page =
            PageSetContainer(
                ContainerId("set"),
                LensBinding(
                    grouped,
                    ExpressionKind.CARD_STACK,
                    second.id,
                ),
            )
        val set =
            initial.update(phone) { layout ->
                layout.replace(WorkspaceId("w")) { it.copy(pages = listOf(page)) }
            }
        repository.save(set)
        return set
    }
}
