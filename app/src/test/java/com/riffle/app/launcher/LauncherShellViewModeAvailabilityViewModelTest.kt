package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellViewModeAvailabilityViewModelTest {
    @Test
    fun startsWithStandardWhenStoredActiveViewModeIsUnavailable() {
        val standardKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER)
        val cardKey = HomeLayoutKey(LauncherViewMode.CARD_INTERFACE)
        val cardLayout =
            HomeLayoutDefaults.standard()
                .copy(viewMode = LauncherViewMode.CARD_INTERFACE)
        val layoutSet =
            HomeLayoutSet(
                activeKey = cardKey,
                layouts =
                    mapOf(
                        standardKey to HomeLayoutDefaults.standard(),
                        cardKey to cardLayout,
                    ),
            )
        val repository = FakeHomeLayoutRepository(layoutSet)

        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        assertEquals(standardKey, viewModel.state.value.homeLayoutSet.activeKey)
        assertEquals(LauncherViewMode.STANDARD_APP_DRAWER, viewModel.state.value.homeLayout.viewMode)
        assertEquals(cardLayout, repository.savedLayoutSet?.layoutFor(cardKey))
        assertEquals(
            LauncherViewMode.CARD_INTERFACE,
            repository.savedLayoutSet?.preferredModesByDeviceClass?.get(HomeLayoutDeviceClass.PHONE),
        )
    }

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }

    private class FakeHomeLayoutRepository(
        var savedLayoutSet: HomeLayoutSet,
    ) : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout? = savedLayoutSet.activeLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            savedLayoutSet = savedLayoutSet.withActiveLayout(layout)
        }

        override fun loadHomeLayoutSet(): HomeLayoutSet = savedLayoutSet

        override fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) {
            savedLayoutSet = layoutSet
        }
    }
}
