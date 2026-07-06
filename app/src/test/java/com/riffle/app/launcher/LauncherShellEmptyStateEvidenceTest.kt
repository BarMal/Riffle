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
    fun completedFirstRunShellBuildsRenderableEmptyHomeState() {
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(isComplete = true),
                installedAppRepository = EmptyInstalledAppRepository,
            )

        runBlocking { viewModel.refreshInstalledApps().join() }

        val state = viewModel.state.value
        val visibleHomeLayout = state.homeLayout.visibleTo(state.installedApps)

        assertEquals(FirstRunStatus.COMPLETE, state.firstRunStatus)
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

    private class FakeFirstRunRepository(
        private val isComplete: Boolean,
    ) : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = isComplete

        override fun setFirstRunComplete() = Unit
    }

    private object EmptyInstalledAppRepository : InstalledAppRepository {
        override fun installedApps(): List<InstalledApp> = emptyList()
    }
}
