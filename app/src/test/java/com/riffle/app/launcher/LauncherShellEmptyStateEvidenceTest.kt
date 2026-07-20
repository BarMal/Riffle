package com.riffle.app.launcher

import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherShellEmptyStateEvidenceTest {
    @Test
    fun migratedFirstRunShellBuildsRenderableEmptyHomeState() {
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(isComplete = true),
                installedAppRepository = EmptyInstalledAppRepository,
            )

        runBlocking { viewModel.refreshInstalledApps().join() }

        val state = viewModel.state.value
        val visibleHomeLayout = state.homeLayout.visibleTo(state.installedApps)

        assertEquals(FirstRunStatus.NEEDS_HOME_ROLE, state.firstRunStatus)
        assertEquals(ShellDestination.HOME, state.destination)
        assertFalse(state.shouldShowDefaultHomePrompt)
        assertTrue(state.shouldShowEmptyHome)
        assertEquals(emptyList<InstalledApp>(), state.installedApps)
        assertEquals(emptyList<InstalledApp>(), state.hiddenApps)
        assertEquals(emptyList<InstalledApp>(), state.appDrawerApps)
        assertEquals(emptyList<InstalledApp>(), state.searchResults)
        assertTrue(state.searchShortcutResults.isEmpty())
        assertTrue(state.installedWidgetProviders.isEmpty())
        assertTrue(state.appIconPreloadIdentities().isEmpty())
        assertEquals(state.homeLayout.selectedPageId, visibleHomeLayout.selectedPageId)
        assertEquals(1, visibleHomeLayout.pages.size)
        assertEquals(0, visibleHomeLayout.selectedPageIndex)
        assertTrue(visibleHomeLayout.selectedPage.items.isEmpty())
        assertTrue(visibleHomeLayout.dock.items.isEmpty())
    }

    @Test
    fun pendingHomeRoleRequestRestoresAsRetryableStateAfterProcessRecreation() {
        val repository = FakeFirstRunRepository(isComplete = false)
        LauncherShellViewModel(firstRunRepository = repository).onDefaultHomeRequestStarted()

        val restoredViewModel = LauncherShellViewModel(firstRunRepository = repository)

        assertEquals(FirstRunStatus.NEEDS_HOME_ROLE, restoredViewModel.state.value.firstRunStatus)
        assertFalse(repository.isHomeRoleRequestPending())

        restoredViewModel.onDefaultHomeRequestStarted()

        assertEquals(FirstRunStatus.REQUESTING_HOME_ROLE, restoredViewModel.state.value.firstRunStatus)
        assertTrue(repository.isHomeRoleRequestPending())
    }

    private class FakeFirstRunRepository(
        private val isComplete: Boolean,
    ) : FirstRunRepository {
        private var homeRoleRequestPending = false

        override fun isFirstRunComplete(): Boolean = isComplete

        override fun setFirstRunComplete() = Unit

        override fun isHomeRoleRequestPending(): Boolean = homeRoleRequestPending

        override fun setHomeRoleRequestPending(pending: Boolean) {
            homeRoleRequestPending = pending
        }
    }

    private object EmptyInstalledAppRepository : InstalledAppRepository {
        override fun installedApps(): List<InstalledApp> = emptyList()
    }
}
