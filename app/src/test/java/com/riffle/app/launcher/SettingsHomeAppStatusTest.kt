package com.riffle.app.launcher

import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsHomeAppStatusTest {
    @Test
    fun labelsDefaultHomeStatus() {
        assertEquals("Riffle is default", HomeRoleStatus.DEFAULT_HOME.settingsHomeAppStatusLabel())
        assertEquals("Riffle is not default", HomeRoleStatus.NOT_DEFAULT_HOME.settingsHomeAppStatusLabel())
        assertEquals("Home app status unavailable", HomeRoleStatus.UNKNOWN.settingsHomeAppStatusLabel())
    }

    @Test
    fun usesAHomeSettingsActionForEveryLiveStatus() {
        assertEquals("Default", HomeRoleStatus.DEFAULT_HOME.settingsHomeAppActionLabel())
        assertEquals("Set home", HomeRoleStatus.NOT_DEFAULT_HOME.settingsHomeAppActionLabel())
        assertEquals("Open settings", HomeRoleStatus.UNKNOWN.settingsHomeAppActionLabel())
    }

    @Test
    fun unresolvedHomeRoleAfterRequestDoesNotRepeatOnboarding() {
        val repository = FakeFirstRunRepository()
        val viewModel = LauncherShellViewModel(firstRunRepository = repository)

        viewModel.onDefaultHomeRequestStarted()
        viewModel.onHomeRoleStatusChanged(HomeRoleStatus.UNKNOWN)

        assertEquals(FirstRunStatus.REQUESTING_HOME_ROLE, viewModel.state.value.firstRunStatus)
        assertFalse(viewModel.state.value.shouldShowDefaultHomePrompt)
        assertTrue(viewModel.state.value.shouldShowEmptyHome)
        assertFalse(repository.isFirstRunComplete())
    }

    @Test
    fun storedPresentationStateDoesNotOverrideUnknownLiveHomeStatus() {
        val repository = FakeFirstRunRepository()
        LauncherShellViewModel(firstRunRepository = repository)
            .onHomeRoleStatusChanged(HomeRoleStatus.DEFAULT_HOME)

        val coldStartViewModel = LauncherShellViewModel(firstRunRepository = repository)
        coldStartViewModel.onHomeRoleStatusChanged(HomeRoleStatus.UNKNOWN)

        assertEquals(HomeRoleStatus.UNKNOWN, coldStartViewModel.state.value.homeRoleStatus)
        assertFalse(coldStartViewModel.state.value.shouldShowSetupCard)
        assertFalse(coldStartViewModel.state.value.shouldShowDefaultHomePrompt)
    }

    @Test
    fun dismissingSetupCardPersistsOnlyPresentationState() {
        val repository = FakeFirstRunRepository()
        val viewModel = LauncherShellViewModel(firstRunRepository = repository)

        viewModel.onSetupCardDismissed()

        assertTrue(repository.isSetupCardDismissed())
        assertFalse(viewModel.state.value.shouldShowSetupCard)
        assertEquals(HomeRoleStatus.UNKNOWN, viewModel.state.value.homeRoleStatus)
    }

    private class FakeFirstRunRepository : FirstRunRepository {
        private var isComplete = false
        private var setupCardDismissed = false

        override fun isFirstRunComplete(): Boolean = isComplete

        override fun setFirstRunComplete() {
            isComplete = true
        }

        override fun isSetupCardDismissed(): Boolean = setupCardDismissed || isComplete

        override fun setSetupCardDismissed() {
            setupCardDismissed = true
        }
    }
}
