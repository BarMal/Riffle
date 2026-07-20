package com.riffle.app.launcher

import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherShellViewModelFirstRunTest {
    @Test
    fun restoresPersistedHomeRoleRequestAsCheckingStateAfterRecreation() {
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(pendingHomeRoleRequest = true),
            )

        assertEquals(FirstRunStatus.REQUESTING_HOME_ROLE, viewModel.state.value.firstRunStatus)
        assertEquals(HomeRoleStatus.UNKNOWN, viewModel.state.value.homeRoleStatus)
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

    private class FakeFirstRunRepository(
        var pendingHomeRoleRequest: Boolean = false,
    ) : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit

        override fun isHomeRoleRequestPending(): Boolean = pendingHomeRoleRequest

        override fun setHomeRoleRequestPending(pending: Boolean) {
            pendingHomeRoleRequest = pending
        }
    }
}
