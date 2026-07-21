package com.riffle.app.launcher

import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus
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
    fun switchingAwayAfterBecomingDefaultKeepsSetupCardDismissed() {
        val repository = FakeFirstRunRepository()
        val viewModel = LauncherShellViewModel(firstRunRepository = repository)

        viewModel.onHomeRoleStatusChanged(HomeRoleStatus.DEFAULT_HOME)
        viewModel.onHomeRoleStatusChanged(HomeRoleStatus.NOT_DEFAULT_HOME)

        assertEquals(FirstRunStatus.NEEDS_HOME_ROLE, viewModel.state.value.firstRunStatus)
        assertFalse(viewModel.state.value.shouldShowSetupCard)
        assertTrue(repository.isSetupCardDismissed())
    }

    private class FakeFirstRunRepository(
        var pendingHomeRoleRequest: Boolean = false,
    ) : FirstRunRepository {
        private var setupCardDismissed = false

        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit

        override fun isSetupCardDismissed(): Boolean = setupCardDismissed

        override fun setSetupCardDismissed() {
            setupCardDismissed = true
        }

        override fun isHomeRoleRequestPending(): Boolean = pendingHomeRoleRequest

        override fun setHomeRoleRequestPending(pending: Boolean) {
            pendingHomeRoleRequest = pending
        }
    }
}
