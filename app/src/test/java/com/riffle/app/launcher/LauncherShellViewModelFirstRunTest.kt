package com.riffle.app.launcher

import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.ShellNavigationAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherShellViewModelFirstRunTest {
    @Test
    fun preservesPersistedHomeRoleRequestUntilLiveStatusReconciliation() {
        val repository = FakeFirstRunRepository(pendingHomeRoleRequest = true)
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = repository,
            )

        assertEquals(FirstRunStatus.REQUESTING_HOME_ROLE, viewModel.state.value.firstRunStatus)
        assertEquals(HomeRoleStatus.UNKNOWN, viewModel.state.value.homeRoleStatus)
        assertTrue(repository.pendingHomeRoleRequest)

        viewModel.onHomeRoleStatusChanged(HomeRoleStatus.UNKNOWN)

        assertEquals(FirstRunStatus.NEEDS_HOME_ROLE, viewModel.state.value.firstRunStatus)
        assertFalse(repository.pendingHomeRoleRequest)

        viewModel.onDefaultHomeRequestStarted()

        assertEquals(FirstRunStatus.REQUESTING_HOME_ROLE, viewModel.state.value.firstRunStatus)
        assertTrue(repository.pendingHomeRoleRequest)
    }

    @Test
    fun returnedDefaultHomeRequestClearsPendingStateBeforeLiveStatusRefresh() {
        val repository = FakeFirstRunRepository()
        val viewModel = LauncherShellViewModel(firstRunRepository = repository)

        viewModel.onDefaultHomeRequestStarted()
        assertTrue(repository.pendingHomeRoleRequest)

        viewModel.onDefaultHomeRequestReturned()

        assertEquals(FirstRunStatus.NEEDS_HOME_ROLE, viewModel.state.value.firstRunStatus)
        assertEquals(HomeRoleStatus.UNKNOWN, viewModel.state.value.homeRoleStatus)
        assertFalse(repository.pendingHomeRoleRequest)
    }

    @Test
    fun restoresPendingHomeRoleRequestToTheOriginatingDestinationAfterProcessRecreation() {
        val repository = FakeFirstRunRepository()
        LauncherShellViewModel(firstRunRepository = repository)
            .apply {
                onNavigationActionSelected(ShellNavigationAction.OpenSettings)
                onDefaultHomeRequestStarted()
            }

        assertTrue(repository.pendingHomeRoleRequest)
        assertEquals(ShellDestination.SETTINGS, repository.storedHomeRoleRequestDestination)

        val recreatedViewModel = LauncherShellViewModel(firstRunRepository = repository)

        assertEquals(FirstRunStatus.REQUESTING_HOME_ROLE, recreatedViewModel.state.value.firstRunStatus)
        assertEquals(ShellDestination.SETTINGS, recreatedViewModel.state.value.destination)

        recreatedViewModel.onHomeRoleStatusChanged(HomeRoleStatus.UNKNOWN)

        assertEquals(FirstRunStatus.NEEDS_HOME_ROLE, recreatedViewModel.state.value.firstRunStatus)
        assertEquals(ShellDestination.SETTINGS, recreatedViewModel.state.value.destination)
        assertFalse(repository.pendingHomeRoleRequest)
        assertEquals(null, repository.storedHomeRoleRequestDestination)
    }

    private class FakeFirstRunRepository(
        var pendingHomeRoleRequest: Boolean = false,
        var storedHomeRoleRequestDestination: ShellDestination? = null,
    ) : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit

        override fun isHomeRoleRequestPending(): Boolean = pendingHomeRoleRequest

        override fun setHomeRoleRequestPending(pending: Boolean) {
            pendingHomeRoleRequest = pending
        }

        override fun homeRoleRequestDestination(): ShellDestination? = storedHomeRoleRequestDestination

        override fun setHomeRoleRequestDestination(destination: ShellDestination?) {
            storedHomeRoleRequestDestination = destination
        }
    }
}
