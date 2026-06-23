package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellViewModeViewModelTest {
    @Test
    fun savesLauncherViewModeSelection() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onHomePageEdited(
            LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.HOME_SCREEN_LIBRARY),
        )

        assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, viewModel.state.value.homeLayout.viewMode)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }

    private class FakeHomeLayoutRepository(
        var savedLayout: HomeLayout? = null,
    ) : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout? = savedLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            savedLayout = layout
        }
    }
}
