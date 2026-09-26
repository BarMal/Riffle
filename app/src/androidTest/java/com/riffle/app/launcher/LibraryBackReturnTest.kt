package com.riffle.app.launcher

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.settings.AppDrawerSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LibraryReturnTarget
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * System Back from Library, under both "After leaving Library" settings (Decision 10, #1243). The
 * shell's own BackHandler dispatches the leave; the real reducer applies it.
 */
@RunWith(AndroidJUnit4::class)
class LibraryBackReturnTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun backFromLibraryWithTheDefaultSettingStaysOnLibrary() {
        val finalMode = pressBackFromLibrary(LauncherSettings())

        composeRule.runOnIdle {
            assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, finalMode())
        }
    }

    @Test
    fun backFromLibraryWithAlwaysHomeSettingLandsOnHome() {
        val settings =
            LauncherSettings(appDrawer = AppDrawerSettings(afterLeavingLibrary = LibraryReturnTarget.HOME))
        val finalMode = pressBackFromLibrary(settings)

        composeRule.runOnIdle {
            assertEquals(LauncherViewMode.CARD_INTERFACE, finalMode())
        }
    }

    /** Sets Library on screen under [settings], presses system Back, and returns the mode left showing. */
    private fun pressBackFromLibrary(settings: LauncherSettings): () -> LauncherViewMode {
        val reducer =
            LauncherHomePageEditReducer(
                homeLayoutRepository = InMemoryHomeLayoutRepository(),
                viewModeAvailability = defaultLauncherViewModeAvailability(),
            )
        val library =
            HomeLayoutSet.fromLayout(
                HomeLayoutDefaults.standard().copy(viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY),
            )
        var state by
            mutableStateOf(
                LauncherShellState(
                    homeLayout = library.activeLayout,
                    homeLayoutSet = library,
                    launcherSettings = settings,
                ),
            )

        composeRule.setContent {
            LauncherShellContent(
                state = state,
                onAction = { action ->
                    if (action is LauncherShellAction.LeaveLibrary) state = reducer.reduce(state, action)
                },
            )
        }
        composeRule.waitForIdle()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        return { state.homeLayoutSet.activeKey.viewMode }
    }

    private class InMemoryHomeLayoutRepository : HomeLayoutRepository {
        private var layoutSet: HomeLayoutSet? = null

        override fun loadHomeLayout(): HomeLayout? = layoutSet?.activeLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            layoutSet = HomeLayoutSet.fromLayout(layout)
        }

        override fun loadHomeLayoutSet(): HomeLayoutSet? = layoutSet

        override fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) {
            this.layoutSet = layoutSet
        }
    }
}
