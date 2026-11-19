package com.riffle.app.screenshots

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.ADD_ALL_APPS_TEST_TAG
import com.riffle.app.launcher.ARRANGE_COMPACT_TEST_TAG
import com.riffle.app.launcher.ARRANGE_CONFIRM_TEST_TAG
import com.riffle.app.launcher.ARRANGE_HOME_TEST_TAG
import com.riffle.app.launcher.EMPTY_HOME_HINT_ADD_TEST_TAG
import com.riffle.app.launcher.EMPTY_HOME_HINT_DISMISS_TEST_TAG
import com.riffle.app.launcher.EMPTY_HOME_HINT_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LauncherShellContent
import com.riffle.app.launcher.SettingKey
import com.riffle.app.launcher.SettingsPage
import com.riffle.app.launcher.SettingsSurface
import com.riffle.app.launcher.settingsSurfaceState
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeSeeding
import com.riffle.core.domain.launcher.home.HomeStart
import com.riffle.core.domain.launcher.settings.FeatureSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Filling Home: Settings > Home's "Fill Home" rows with their confirmation and Undo, the first-run seeded Home and
 * Dock, and the empty-Home hint.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class HomePopulationScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<LauncherShellAction>()

    private val emptyHome: LauncherShellState =
        ScreenshotFixtures.homeState().let { base ->
            val empty = HomeLayoutDefaults.standard()
            base.copy(
                homeLayout = empty,
                homeLayoutSet = base.homeLayoutSet.withActiveLayout(empty),
                // The Home-role card comes first on a first run and holds the hint back; it is out of the way here.
                setupCardDismissed = true,
            )
        }

    private fun settings(
        state: LauncherShellState,
        capture: Boolean = false,
        anchor: SettingKey? = SettingKey.HOME_ADD_ALL_APPS,
    ) {
        val surface = state.settingsSurfaceState(appVersionLabel = "Riffle 1.0 (test)")
        composeRule.setContent {
            ScreenshotBackdrop {
                SettingsSurface(
                    state = surface,
                    initialPage = SettingsPage.HOME,
                    initialAnchor = anchor,
                    onAction = { action -> actions += action },
                )
            }
        }
        composeRule.waitForIdle()
        if (capture) composeRule.captureScreen()
    }

    @Test
    fun settingsHomeShowsTheAddAllCountTheArrangeButtonAndTheCompactSwitch() {
        settings(emptyHome, capture = true)

        composeRule.onNodeWithTag(ADD_ALL_APPS_TEST_TAG).assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText("Adds 12 apps that aren't on Home yet").assertIsDisplayed()
        composeRule.onNodeWithTag(ARRANGE_HOME_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(ARRANGE_COMPACT_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun addAllSendsItsActionAnnouncesTheCountAndUndoRestoresTheLayoutBefore() {
        settings(emptyHome)

        composeRule.onNodeWithTag(ADD_ALL_APPS_TEST_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Added 12 apps").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf(
                LauncherShellAction.AddAllAppsToHome,
                LauncherShellAction.UndoHomeBulkEdit(emptyHome.homeLayout),
            ),
            actions,
        )
    }

    @Test
    fun addAllIsDisabledAndSaysSoWhenEveryAppIsAlreadyOnHome() {
        val seeded = seeded(HomeStart.ALL_APPS, dockRoles = false)
        settings(seeded, capture = true)

        composeRule.onNodeWithTag(ADD_ALL_APPS_TEST_TAG).assertIsNotEnabled()
        composeRule.onNodeWithText("Every app is already on Home").assertIsDisplayed()
    }

    @Test
    fun theBulkRowsAreOffInSimpleMode() {
        val simple =
            emptyHome.copy(
                launcherSettings = emptyHome.launcherSettings.copy(features = FeatureSettings(simpleMode = true)),
            )
        settings(simple)

        composeRule.onNodeWithTag(ADD_ALL_APPS_TEST_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(ARRANGE_HOME_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun arrangeAsksFirstCancelSendsNothingAndArrangeThenOffersUndo() {
        settings(emptyHome, anchor = SettingKey.HOME_ARRANGE)

        composeRule.onNodeWithTag(ARRANGE_HOME_TEST_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Arrange Home A to Z?").assertIsDisplayed()
        composeRule.onNodeWithText("Your current arrangement will be replaced.").assertIsDisplayed()
        composeRule.captureScreen()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        assertTrue(actions.isEmpty())

        composeRule.onNodeWithTag(ARRANGE_HOME_TEST_TAG).performClick()
        composeRule.onNodeWithTag(ARRANGE_CONFIRM_TEST_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Home arranged").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf(
                LauncherShellAction.ArrangeHomeAtoZ,
                LauncherShellAction.UndoHomeBulkEdit(emptyHome.homeLayout),
            ),
            actions,
        )
    }

    @Test
    fun theCompactSwitchSendsItsChoice() {
        settings(emptyHome, anchor = SettingKey.HOME_ARRANGE_COMPACT)

        composeRule.onNodeWithTag(ARRANGE_COMPACT_TEST_TAG).performClick()

        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.SetCompactArrange(false)), actions)
    }

    private fun seeded(
        start: HomeStart,
        dockRoles: Boolean = true,
    ): LauncherShellState {
        val base = ScreenshotFixtures.homeState()
        val roles = if (dockRoles) ScreenshotFixtures.dockApps.map { it.identity.packageName } else emptyList()
        val layout =
            HomeSeeding.seed(HomeLayoutDefaults.standard(), ScreenshotFixtures.installedApps, roles, start, true)
        return base.copy(homeLayout = layout, homeLayoutSet = base.homeLayoutSet.withActiveLayout(layout))
    }

    @Test
    fun aSeededHomeShowsEveryOtherAppAToZAboveTheSeededDock() {
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = seeded(HomeStart.ALL_APPS),
                    appIconLoader = SolidColorAppIconLoader(),
                    onAction = {},
                )
            }
        }
        composeRule.captureScreen()
        composeRule.onNodeWithText("Calendar").assertIsDisplayed()
        composeRule.onNodeWithText("Weather").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun aSeededHomeDark() {
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = seeded(HomeStart.ALL_APPS),
                    appIconLoader = SolidColorAppIconLoader(),
                    onAction = {},
                )
            }
        }
        composeRule.captureScreen()
    }

    private var heldAnItem = 0

    private fun renderShell(state: LauncherShellState) {
        composeRule.setContent {
            ScreenshotBackdrop {
                LauncherShellContent(
                    state = state,
                    appIconLoader = SolidColorAppIconLoader(),
                    onAction = { action -> actions += action },
                    onHomeHeldAnItem = { heldAnItem++ },
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun theEmptyHomeHintOffersAddAllAndCanBeDismissed() {
        renderShell(emptyHome)

        composeRule.onNodeWithTag(EMPTY_HOME_HINT_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Home is empty.").assertIsDisplayed()
        composeRule.captureScreen()
        composeRule.onNodeWithTag(EMPTY_HOME_HINT_ADD_TEST_TAG).performClick()
        composeRule.onNodeWithTag(EMPTY_HOME_HINT_DISMISS_TEST_TAG).performClick()

        assertEquals(
            listOf<LauncherShellAction>(LauncherShellAction.AddAllAppsToHome, LauncherShellAction.DismissEmptyHomeHint),
            actions,
        )
    }

    @Test
    fun theHintIsNotShownOnceDismissedOrWhenHomeHasAnItem() {
        val dismissed =
            emptyHome.copy(
                launcherSettings =
                    emptyHome.launcherSettings.copy(
                        home = emptyHome.launcherSettings.home.copy(emptyHomeHintDismissed = true),
                    ),
            )
        renderShell(dismissed)
        composeRule.onNodeWithTag(EMPTY_HOME_HINT_TEST_TAG).assertDoesNotExist()
        assertEquals(0, heldAnItem)
    }

    @Test
    fun theHintIsNotShownWhenHomeHoldsAnAppAndTheShellRecordsThatOnce() {
        renderShell(ScreenshotFixtures.homeState().copy(setupCardDismissed = true))

        composeRule.onNodeWithTag(EMPTY_HOME_HINT_TEST_TAG).assertDoesNotExist()
        assertEquals(1, heldAnItem)
        assertTrue(actions.isEmpty())
    }
}
