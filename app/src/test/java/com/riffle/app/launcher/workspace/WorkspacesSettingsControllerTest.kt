package com.riffle.app.launcher.workspace

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.settings.WorkspacesSettingsAction
import com.riffle.core.domain.launcher.workspace.testing.InMemoryWorkspaceRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspacesSettingsControllerTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val foldable = HomeLayoutDeviceClass.FOLDABLE
    private val a = Workspace(WorkspaceId("a"), "Alpha", presetId = "nova")
    private val b = Workspace(WorkspaceId("b"), "Beta")
    private val initial =
        WorkspaceSet(
            mapOf(
                phone to LayoutWorkspaces(listOf(a, b), WorkspaceId("a"), WorkspaceId("a")),
                foldable to LayoutWorkspaces.single(Workspace(WorkspaceId("f"), "Fold")),
            ),
        )
    private val repository = InMemoryWorkspaceRepository(initial)
    private var changes = 0
    private var counter = 0
    private val controller =
        WorkspacesSettingsController(repository, WorkspaceIdFactory { "id-${counter++}" }) { changes++ }

    private fun layout(device: HomeLayoutDeviceClass = phone) = checkNotNull(repository.load()).workspacesFor(device)

    @Test
    fun theModelIsNullUntilTheRepositoryHasLoaded() {
        val empty = WorkspacesSettingsController(InMemoryWorkspaceRepository(null))

        assertNull(empty.model(phone, phone, listOf(phone)))
        assertNotNull(controller.model(phone, phone, listOf(phone, foldable)))
    }

    @Test
    fun anActionIsPersistedThroughTheRepositoryAndAnnounced() {
        val applied = controller.dispatch(phone, WorkspacesSettingsAction.Activate(WorkspaceId("b")))

        assertTrue(applied)
        assertEquals(WorkspaceId("b"), layout().activeId)
        assertEquals(1, changes)
        assertEquals("Switched to \"Beta\"", controller.feedback.value?.message)
        assertFalse(controller.feedback.value?.canUndo ?: true)
    }

    @Test
    fun deletingConfirmsNothingItselfButOffersUndoThatRestoresTheLayoutExactly() {
        controller.dispatch(phone, WorkspacesSettingsAction.Delete(WorkspaceId("b")))

        assertEquals(listOf("a"), layout().workspaces.map { it.id.value })
        assertTrue(controller.feedback.value?.canUndo ?: false)

        controller.undo()

        assertEquals(initial, repository.load())
        assertEquals("Undone", controller.feedback.value?.message)
        assertFalse(controller.feedback.value?.canUndo ?: true)
        assertEquals(2, changes)
    }

    @Test
    fun theLastWorkspaceIsNeverDeletedAndTheReasonIsAnnounced() {
        controller.dispatch(phone, WorkspacesSettingsAction.Delete(WorkspaceId("b")))
        val applied = controller.dispatch(phone, WorkspacesSettingsAction.Delete(WorkspaceId("a")))

        assertFalse(applied)
        assertEquals(listOf("a"), layout().workspaces.map { it.id.value })
        assertEquals("The last workspace on a layout can't be deleted.", controller.feedback.value?.message)
        assertFalse(controller.feedback.value?.canUndo ?: true)
    }

    @Test
    fun aLaterChangeDropsTheOfferSoUndoCanNeverOverwriteSomethingNewer() {
        controller.dispatch(phone, WorkspacesSettingsAction.Delete(WorkspaceId("b")))
        controller.dispatch(phone, WorkspacesSettingsAction.Rename(WorkspaceId("a"), "Renamed"))

        controller.undo()

        assertEquals("Renamed", layout().active.name)
        assertEquals(listOf("a"), layout().workspaces.map { it.id.value })
    }

    @Test
    fun dismissingTheAnnouncementEndsTheUndoOffer() {
        controller.dispatch(phone, WorkspacesSettingsAction.Delete(WorkspaceId("b")))
        val shown = checkNotNull(controller.feedback.value)

        controller.feedbackShown(shown.id)
        controller.undo()

        assertNull(controller.feedback.value)
        assertEquals(listOf("a"), layout().workspaces.map { it.id.value })
    }

    @Test
    fun aStaleDismissalDoesNotClearANewerAnnouncement() {
        controller.dispatch(phone, WorkspacesSettingsAction.Activate(WorkspaceId("b")))
        val first = checkNotNull(controller.feedback.value)
        controller.dispatch(phone, WorkspacesSettingsAction.Duplicate(WorkspaceId("a")))
        val second = checkNotNull(controller.feedback.value)

        controller.feedbackShown(first.id)

        assertNotEquals(first.id, second.id)
        assertEquals(second, controller.feedback.value)
    }

    @Test
    fun leavingThePageDropsAnythingPending() {
        controller.dispatch(phone, WorkspacesSettingsAction.Delete(WorkspaceId("b")))

        controller.leave()
        controller.undo()

        assertNull(controller.feedback.value)
        assertEquals(listOf("a"), layout().workspaces.map { it.id.value })
    }

    @Test
    fun copyFromOtherLayoutReplacesOnlyTheViewedLayoutAndUndoRestoresIt() {
        controller.dispatch(foldable, WorkspacesSettingsAction.CopyFromLayout(phone))

        assertEquals(listOf("Alpha", "Beta"), layout(foldable).workspaces.map { it.name })
        assertEquals(initial.workspacesFor(phone), layout())
        assertTrue(controller.feedback.value?.canUndo ?: false)

        controller.undo()

        assertEquals(initial, repository.load())
    }

    @Test
    fun resetUsesTheViewedLayoutsPostureAndKeepsIdAndName() {
        controller.dispatch(phone, WorkspacesSettingsAction.ResetToPreset(WorkspaceId("a")))

        val reset = layout().find(WorkspaceId("a"))
        assertEquals("Alpha", reset?.name)
        assertTrue(reset?.pages?.isNotEmpty() ?: false)
    }

    @Test
    fun nothingHappensBeforeTheRepositoryHasLoaded() {
        val empty = WorkspacesSettingsController(InMemoryWorkspaceRepository(null)) { changes++ }

        assertFalse(empty.dispatch(phone, WorkspacesSettingsAction.Activate(WorkspaceId("a"))))
        assertEquals(0, changes)
        assertNull(empty.feedback.value)
    }

    @Test
    fun sourceUsageIsNullUntilLoadedAndCountsTheGivenLayoutsOnly() {
        assertNull(WorkspacesSettingsController(InMemoryWorkspaceRepository()).sourceUsage(listOf(phone)))

        val usage = checkNotNull(controller.sourceUsage(listOf(phone)))

        assertEquals(0, usage.countOf(SourceIds.ALL_APPS))
        assertTrue(usage.layoutsOf(SourceIds.ALL_APPS).isEmpty())
    }

    @Test
    fun sourceUsageReadsTheStoredPagesAndNeverChangesThem() {
        val binding = LensBinding(Lens(listOf(SourceIds.RSS)), ExpressionKind.LIST)
        val page = PageContainer(ContainerId("p"), PageContent.Bound(binding))
        val feeds = Workspace(WorkspaceId("w"), "Feeds", listOf(page))
        val repo = InMemoryWorkspaceRepository(WorkspaceSet(mapOf(phone to LayoutWorkspaces.single(feeds))))
        val before = repo.load()

        val usage = checkNotNull(WorkspacesSettingsController(repo).sourceUsage(listOf(phone)))

        assertEquals(1, usage.countOf(SourceIds.RSS))
        assertEquals("Feeds", usage.placesOf(SourceIds.RSS).single().workspaceName)
        assertEquals(before, repo.load())
    }
}
