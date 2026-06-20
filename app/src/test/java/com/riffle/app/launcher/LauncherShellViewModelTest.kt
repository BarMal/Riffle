package com.riffle.app.launcher

import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherShellViewModelTest {
    @Test
    fun startsWithDefaultHomePromptVisible() {
        val viewModel = LauncherShellViewModel(firstRunRepository = FakeFirstRunRepository())

        assertTrue(viewModel.state.value.shouldShowDefaultHomePrompt)
    }

    @Test
    fun recordsDefaultHomeRequestInProgress() {
        val viewModel = LauncherShellViewModel(firstRunRepository = FakeFirstRunRepository())

        viewModel.onDefaultHomeRequestStarted()

        assertEquals(FirstRunStatus.REQUESTING_HOME_ROLE, viewModel.state.value.firstRunStatus)
    }

    @Test
    fun hidesPromptWhenAppBecomesDefaultHome() {
        val repository = FakeFirstRunRepository()
        val viewModel = LauncherShellViewModel(firstRunRepository = repository)

        viewModel.onHomeRoleStatusChanged(HomeRoleStatus.DEFAULT_HOME)

        assertFalse(viewModel.state.value.shouldShowDefaultHomePrompt)
        assertTrue(viewModel.state.value.shouldShowEmptyHome)
        assertTrue(repository.isFirstRunComplete())
    }

    @Test
    fun restoresCompletedFirstRunFromRepository() {
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(isComplete = true),
            )

        assertEquals(FirstRunStatus.COMPLETE, viewModel.state.value.firstRunStatus)
        assertTrue(viewModel.state.value.shouldShowEmptyHome)
    }

    private class FakeFirstRunRepository(
        private var isComplete: Boolean = false,
    ) : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = isComplete

        override fun setFirstRunComplete() {
            isComplete = true
        }
    }
}
