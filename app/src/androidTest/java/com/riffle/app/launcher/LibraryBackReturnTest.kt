package com.riffle.app.launcher

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.settings.AppDrawerSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LibraryReturnTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * System Back on a Library home under both stored "After leaving Library" values (S9, closes #1323): the shell no
 * longer leaves Library for another mode, so Back at the top level passes through to the system (here a fallback
 * handler composed below the shell's) and the mode stays.
 */
@RunWith(AndroidJUnit4::class)
class LibraryBackReturnTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun backFromLibraryWithTheDefaultSettingStaysOnLibrary() {
        assertBackKeepsLibrary(LauncherSettings())
    }

    @Test
    fun backFromLibraryWithTheOldAlwaysHomeSettingStaysOnLibraryToo() {
        assertBackKeepsLibrary(
            LauncherSettings(appDrawer = AppDrawerSettings(afterLeavingLibrary = LibraryReturnTarget.HOME)),
        )
    }

    private fun assertBackKeepsLibrary(settings: LauncherSettings) {
        val library =
            HomeLayoutSet.fromLayout(
                HomeLayoutDefaults.standard().copy(viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY),
            )
        val state =
            LauncherShellState(
                homeLayout = library.activeLayout,
                homeLayoutSet = library,
                launcherSettings = settings,
            )
        val dispatched = mutableListOf<LauncherShellAction>()
        var reachedSystem by mutableStateOf(false)

        composeRule.setContent {
            // Composed first, so any handler the shell installs would take precedence over it.
            BackHandler { reachedSystem = true }
            LauncherShellContent(state = state, onAction = { action -> dispatched += action })
        }
        composeRule.waitForIdle()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertTrue(reachedSystem)
            assertTrue(dispatched.none { it is LauncherShellAction.LeaveLibrary })
            assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, state.homeLayoutSet.activeKey.viewMode)
        }
    }
}
