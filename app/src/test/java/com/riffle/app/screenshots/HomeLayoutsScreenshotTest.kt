package com.riffle.app.screenshots

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.HOME_LAYOUTS_DIALOG_CONFIRM_TEST_TAG
import com.riffle.app.launcher.HOME_LAYOUTS_SAVE_TEST_TAG
import com.riffle.app.launcher.LocalSavedHomeLayouts
import com.riffle.app.launcher.SettingsPage
import com.riffle.app.launcher.SettingsSurface
import com.riffle.app.launcher.savedhome.InMemorySavedHomeLayoutsStore
import com.riffle.app.launcher.savedhome.SavedHomeAccess
import com.riffle.app.launcher.savedhome.SavedHomeEnvironment
import com.riffle.app.launcher.savedhome.SavedHomeLayoutsController
import com.riffle.app.launcher.settingsSurfaceState
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.saved.SavedHomeCapture
import com.riffle.core.domain.launcher.home.saved.SavedHomeLayout
import com.riffle.core.domain.launcher.home.saved.SavedHomeLayoutKind
import com.riffle.core.domain.launcher.home.saved.SavedHomeLayouts
import com.riffle.core.domain.launcher.settings.FeatureSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings > Home > Home layouts: empty, with three saved, dark, the Activate confirmation, Save, and Simple mode. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class HomeLayoutsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val applied = mutableListOf<HomeLayout>()
    private val state: LauncherShellState = ScreenshotFixtures.homeState()

    private val access =
        object : SavedHomeAccess {
            override fun environment() =
                SavedHomeEnvironment(
                    deviceClass = HomeLayoutDeviceClass.PHONE,
                    layout = state.homeLayout,
                    installed = state.installedApps.map { it.identity }.toSet(),
                    hidden = emptySet(),
                    appsLoaded = true,
                    simpleMode = false,
                )

            override fun applyHome(layout: HomeLayout) {
                applied += layout
            }
        }

    private fun capture(
        name: String,
        id: String,
        kind: SavedHomeLayoutKind = SavedHomeLayoutKind.SAVED,
    ): SavedHomeLayout = SavedHomeCapture.capture(state.homeLayout.pages, { null }, id, name, kind, 1L)

    private fun show(
        saved: List<SavedHomeLayout>,
        shell: LauncherShellState = state,
    ): SavedHomeLayoutsController {
        val store =
            InMemorySavedHomeLayoutsStore(SavedHomeLayouts(mapOf(HomeLayoutDeviceClass.PHONE to saved)))
        var counter = 0
        val controller = SavedHomeLayoutsController(store, access, { null }, { null }, { 1L }, { "id${counter++}" })
        val surface = shell.settingsSurfaceState(appVersionLabel = "Riffle 1.0 (test)")
        composeRule.setContent {
            ScreenshotBackdrop {
                CompositionLocalProvider(LocalSavedHomeLayouts provides controller) {
                    SettingsSurface(state = surface, initialPage = SettingsPage.HOME_LAYOUTS, onAction = {})
                }
            }
        }
        composeRule.waitForIdle()
        return controller
    }

    private fun three() = listOf(capture("Weekday", "a"), capture("Weekend", "b"), capture("Travel", "c"))

    @Test
    fun homeLayoutsEmpty() {
        show(emptyList())
        composeRule.captureScreen()
        composeRule.onNodeWithText("Nothing saved yet", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Changes to Home don’t change a saved layout.").assertIsDisplayed()
    }

    @Test
    fun homeLayoutsWithThreeSaved() {
        show(three())
        composeRule.captureScreen()
        composeRule.onNodeWithText("Weekday").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun homeLayoutsWithThreeSavedDark() {
        show(three())
        composeRule.captureScreen()
    }

    @Test
    fun activateAsksFirstSaysTheCurrentHomeIsKeptThenReplacesHomeAndOffersUndo() {
        show(three())
        composeRule.onNodeWithContentDescription("Options for Weekend").performClick()
        composeRule.onNodeWithText("Activate").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Activate “Weekend”?").assertIsDisplayed()
        composeRule.onNodeWithText("kept as a saved layout", substring = true).assertIsDisplayed()
        composeRule.captureScreen()
        assertTrue(applied.isEmpty())

        composeRule.onNodeWithTag(HOME_LAYOUTS_DIALOG_CONFIRM_TEST_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Home layout activated").assertIsDisplayed()
        assertEquals(1, applied.size)
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()
        assertEquals(state.homeLayout, applied.last())
    }

    @Test
    fun saveIsOffWhenTwelveAreSavedAndInSimpleMode() {
        show((1..12).map { capture("L$it", "l$it") })
        composeRule.onNodeWithTag(HOME_LAYOUTS_SAVE_TEST_TAG).assertIsNotEnabled()
        composeRule.onNodeWithText("You have 12 saved layouts. Delete one to save another.").assertIsDisplayed()
    }

    @Test
    fun everythingIsOffInSimpleMode() {
        val simple =
            state.copy(
                launcherSettings = state.launcherSettings.copy(features = FeatureSettings(simpleMode = true)),
            )
        show(three(), simple)
        composeRule.onNodeWithTag(HOME_LAYOUTS_SAVE_TEST_TAG).assertIsNotEnabled()
        composeRule.onNodeWithText("Off in Simple mode").assertIsDisplayed()
    }
}
