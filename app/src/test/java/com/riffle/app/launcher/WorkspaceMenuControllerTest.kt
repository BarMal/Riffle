package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageRole
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.menu.WorkspaceMenuAction
import com.riffle.core.domain.launcher.workspace.menu.WorkspaceMenuEffect
import com.riffle.core.domain.launcher.workspace.testing.InMemoryWorkspaceRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceMenuControllerTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val lens = Lens(sources = listOf(SourceId("apps")))

    private fun workspace(id: String) =
        Workspace(
            WorkspaceId(id),
            id,
            listOf(
                PageContainer(ContainerId("$id-page"), PageContent.Bound(LensBinding(lens, ExpressionKind.LIST))),
                PageContainer(
                    ContainerId("$id-finder"),
                    PageContent.Bound(LensBinding(lens, ExpressionKind.CATEGORIES)),
                    PageRole.FINDER,
                ),
            ),
        )

    private val set =
        WorkspaceSet(
            mapOf(
                phone to LayoutWorkspaces(listOf(workspace("a"), workspace("b")), WorkspaceId("a"), WorkspaceId("a")),
            ),
        )

    private class Fixture(
        repository: InMemoryWorkspaceRepository?,
        enabled: Boolean,
        deviceClass: HomeLayoutDeviceClass,
    ) {
        val effects = mutableListOf<WorkspaceMenuEffect>()
        val controller =
            WorkspaceMenuController(
                repository = repository,
                deviceClass = { deviceClass },
                isEnabled = { enabled },
                onEffect = { effects += it },
            )
    }

    private fun fixture(
        stored: WorkspaceSet? = set,
        enabled: Boolean = true,
        repository: InMemoryWorkspaceRepository? = InMemoryWorkspaceRepository(stored),
    ) = Fixture(repository, enabled, phone)

    @Test
    fun featureOffNeverOpensAndOffersNothing() {
        val f = fixture(enabled = false)
        assertFalse(f.controller.isAvailable())
        f.controller.dispatch(WorkspaceMenuAction.Open)
        assertFalse(f.controller.state.value.isVisible)
    }

    @Test
    fun noRepositoryOrNothingLoadedNeverOpens() {
        val noRepository = fixture(repository = null)
        noRepository.controller.dispatch(WorkspaceMenuAction.Open)
        assertFalse(noRepository.controller.state.value.isVisible)
        val empty = fixture(stored = null)
        assertFalse(empty.controller.isAvailable())
        empty.controller.dispatch(WorkspaceMenuAction.Open)
        assertFalse(empty.controller.state.value.isVisible)
    }

    @Test
    fun opensWithAModelAndCloses() {
        val f = fixture()
        f.controller.dispatch(WorkspaceMenuAction.Open)
        assertTrue(f.controller.state.value.isVisible)
        assertEquals(listOf("a", "b"), f.controller.state.value.model?.switchEntries?.map { it.id.value })
        f.controller.dispatch(WorkspaceMenuAction.Close)
        assertFalse(f.controller.state.value.isVisible)
        assertNull(f.controller.state.value.model)
    }

    @Test
    fun switchingWorkspacePersistsTheActiveOneAndReportsTheEffect() {
        val repository = InMemoryWorkspaceRepository(set)
        val f = fixture(repository = repository)
        f.controller.dispatch(WorkspaceMenuAction.Open)
        f.controller.dispatch(WorkspaceMenuAction.SwitchWorkspace(WorkspaceId("b")))
        assertEquals(WorkspaceId("b"), repository.stored?.workspacesFor(phone)?.activeId)
        assertEquals(listOf<WorkspaceMenuEffect>(WorkspaceMenuEffect.SetActiveWorkspace(WorkspaceId("b"))), f.effects)
        assertFalse(f.controller.state.value.isVisible)
    }

    @Test
    fun editOnlyEmitsTheEffectAndChangesNothingStored() {
        val repository = InMemoryWorkspaceRepository(set)
        val f = fixture(repository = repository)
        f.controller.dispatch(WorkspaceMenuAction.Open)
        f.controller.dispatch(WorkspaceMenuAction.EditWorkspace)
        assertEquals(listOf<WorkspaceMenuEffect>(WorkspaceMenuEffect.EditWorkspace(WorkspaceId("a"))), f.effects)
        assertEquals(set, repository.stored)
    }

    @Test
    fun finderIsReportedNotPerformed() {
        val f = fixture()
        f.controller.dispatch(WorkspaceMenuAction.Open)
        f.controller.dispatch(WorkspaceMenuAction.OpenFinder)
        assertEquals(
            listOf<WorkspaceMenuEffect>(WorkspaceMenuEffect.OpenFinderPage(ContainerId("a-finder"))),
            f.effects,
        )
    }

    @Test
    fun refreshReplansWhileOpenAndClosesWhenTheModelVanishes() {
        val repository = InMemoryWorkspaceRepository(set)
        val f = fixture(repository = repository)
        f.controller.dispatch(WorkspaceMenuAction.Open)
        repository.stored = set.update(phone) { it.activate(WorkspaceId("b")) }
        f.controller.refresh()
        assertEquals(WorkspaceId("b"), f.controller.state.value.model?.editTarget)
        repository.stored = null
        f.controller.refresh()
        assertFalse(f.controller.state.value.isVisible)
    }
}
