package com.riffle.app.launcher

import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.ShellNavigationAction
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellViewModelFirstRunTest {
    @Test
    fun preservesPersistedHomeRoleRequestUntilLiveStatusReconciliation() {
        val repository =
            FakeFirstRunRepository(
                storedHomeRoleRequestContext = HomeRoleRequestContext(ShellDestination.HOME),
            )
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = repository,
            )

        assertEquals(FirstRunStatus.REQUESTING_HOME_ROLE, viewModel.state.value.firstRunStatus)
        assertEquals(HomeRoleStatus.UNKNOWN, viewModel.state.value.homeRoleStatus)
        assertEquals(HomeRoleRequestContext(ShellDestination.HOME), repository.storedHomeRoleRequestContext)

        viewModel.onHomeRoleStatusChanged(HomeRoleStatus.UNKNOWN)

        assertEquals(FirstRunStatus.NEEDS_HOME_ROLE, viewModel.state.value.firstRunStatus)
        assertEquals(null, repository.storedHomeRoleRequestContext)

        viewModel.onDefaultHomeRequestStarted()

        assertEquals(FirstRunStatus.REQUESTING_HOME_ROLE, viewModel.state.value.firstRunStatus)
        assertEquals(HomeRoleRequestContext(ShellDestination.HOME), repository.storedHomeRoleRequestContext)
    }

    @Test
    fun returnedDefaultHomeRequestClearsPendingStateBeforeLiveStatusRefresh() {
        val repository = FakeFirstRunRepository()
        val viewModel = LauncherShellViewModel(firstRunRepository = repository)

        viewModel.onDefaultHomeRequestStarted()
        assertEquals(HomeRoleRequestContext(ShellDestination.HOME), repository.storedHomeRoleRequestContext)

        viewModel.onDefaultHomeRequestReturned()

        assertEquals(FirstRunStatus.NEEDS_HOME_ROLE, viewModel.state.value.firstRunStatus)
        assertEquals(HomeRoleStatus.UNKNOWN, viewModel.state.value.homeRoleStatus)
        assertEquals(null, repository.storedHomeRoleRequestContext)
    }

    @Test
    fun restoresPendingHomeRoleRequestToTheOriginatingDestinationAfterProcessRecreation() {
        val repository = FakeFirstRunRepository()
        LauncherShellViewModel(firstRunRepository = repository)
            .apply {
                onNavigationActionSelected(ShellNavigationAction.OpenSettings)
                onDefaultHomeRequestStarted()
            }

        assertEquals(
            HomeRoleRequestContext(destination = ShellDestination.SETTINGS),
            repository.storedHomeRoleRequestContext,
        )

        val recreatedViewModel = LauncherShellViewModel(firstRunRepository = repository)

        assertEquals(FirstRunStatus.REQUESTING_HOME_ROLE, recreatedViewModel.state.value.firstRunStatus)
        assertEquals(ShellDestination.SETTINGS, recreatedViewModel.state.value.destination)

        recreatedViewModel.onHomeRoleStatusChanged(HomeRoleStatus.UNKNOWN)

        assertEquals(FirstRunStatus.NEEDS_HOME_ROLE, recreatedViewModel.state.value.firstRunStatus)
        assertEquals(ShellDestination.SETTINGS, recreatedViewModel.state.value.destination)
        assertEquals(null, repository.storedHomeRoleRequestContext)
    }

    private class FakeFirstRunRepository(
        var storedHomeRoleRequestContext: HomeRoleRequestContext? = null,
    ) : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit

        override fun homeRoleRequestContext(): HomeRoleRequestContext? = storedHomeRoleRequestContext

        override fun setHomeRoleRequestContext(context: HomeRoleRequestContext?) {
            storedHomeRoleRequestContext = context
        }
    }
}
