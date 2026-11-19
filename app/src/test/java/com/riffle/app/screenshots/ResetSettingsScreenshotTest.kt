package com.riffle.app.screenshots

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LocalStreamLayoutRepository
import com.riffle.app.launcher.RESET_SETTINGS_CONFIRM_TEST_TAG
import com.riffle.app.launcher.RESET_SETTINGS_TEST_TAG
import com.riffle.app.launcher.SettingsPage
import com.riffle.app.launcher.SettingsSurface
import com.riffle.app.launcher.settingsSurfaceState
import com.riffle.app.launcher.stream.CachedStreamLayoutRepository
import com.riffle.app.launcher.stream.StreamLayoutStorePort
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import com.riffle.core.domain.launcher.stream.StreamLayoutReducer
import com.riffle.core.domain.launcher.stream.StreamLayouts
import com.riffle.core.domain.launcher.stream.StreamSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings > About > Reset settings: the confirmation that lists what is kept, then "Settings reset" with Undo. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class ResetSettingsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<LauncherShellAction>()
    private val state = ScreenshotFixtures.homeState()

    private fun show() {
        val surface = state.settingsSurfaceState(appVersionLabel = "Riffle 1.0 (test)")
        composeRule.setContent {
            ScreenshotBackdrop {
                SettingsSurface(state = surface, initialPage = SettingsPage.VERSION, onAction = { actions += it })
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun resetAsksFirstAndListsWhatIsKept() {
        show()
        composeRule.onNodeWithTag(RESET_SETTINGS_TEST_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Reset settings?").assertIsDisplayed()
        composeRule.onNodeWithText("hidden apps", substring = true).assertIsDisplayed()
        composeRule.captureScreen()
        assertEquals(emptyList<LauncherShellAction>(), actions)
    }

    @Test
    fun confirmingResetsAnnouncesAndUndoRestoresThePreviousSettings() {
        show()
        composeRule.onNodeWithTag(RESET_SETTINGS_TEST_TAG).performClick()
        composeRule.onNodeWithTag(RESET_SETTINGS_CONFIRM_TEST_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Settings reset").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()
        assertEquals(
            listOf(LauncherShellAction.ResetSettings, LauncherShellAction.RestoreSettings(state.launcherSettings)),
            actions,
        )
    }

    @Test
    fun cancelChangesNothing() {
        show()
        composeRule.onNodeWithTag(RESET_SETTINGS_TEST_TAG).performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        assertEquals(emptyList<LauncherShellAction>(), actions)
    }

    @Test
    fun confirmingAlsoResetsTheStreamsSectionsAndUndoPutsThemBackExactly() {
        val custom =
            StreamLayouts().with(
                HomeLayoutDeviceClass.PHONE,
                StreamLayoutReducer.reduce(
                    StreamLayout.DEFAULT,
                    StreamLayoutAction.SetEnabled(StreamSection.RECENTS, false),
                ),
            )
        val repository = CachedStreamLayoutRepository(OneStore(custom), CoroutineScope(Dispatchers.Unconfined + Job()))
        val surface = state.settingsSurfaceState(appVersionLabel = "Riffle 1.0 (test)")
        composeRule.setContent {
            ScreenshotBackdrop {
                CompositionLocalProvider(LocalStreamLayoutRepository provides repository) {
                    SettingsSurface(state = surface, initialPage = SettingsPage.VERSION, onAction = { actions += it })
                }
            }
        }
        composeRule.waitForIdle()
        runBlocking { repository.initialize() }
        assertEquals(custom, repository.layouts.value)

        composeRule.onNodeWithTag(RESET_SETTINGS_TEST_TAG).performClick()
        composeRule.onNodeWithTag(RESET_SETTINGS_CONFIRM_TEST_TAG).performClick()
        composeRule.waitForIdle()
        assertEquals(StreamLayout.DEFAULT, repository.layoutFor(HomeLayoutDeviceClass.PHONE))

        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()
        assertEquals(custom, repository.layouts.value)
    }

    private class OneStore(private var stored: StreamLayouts) : StreamLayoutStorePort {
        override suspend fun read(): StreamLayouts = stored

        override suspend fun write(layouts: StreamLayouts) {
            stored = layouts
        }
    }
}
