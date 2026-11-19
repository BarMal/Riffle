package com.riffle.app.screenshots

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import com.riffle.app.launcher.HOME_DOCK_TEST_TAG
import com.riffle.app.launcher.HOME_HANDLE_TEST_TAG
import com.riffle.app.launcher.HOME_WORKSPACE_GRID_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.library.LIBRARY_PAGED_TEST_TAG
import com.riffle.app.launcher.library.LIBRARY_PAGE_TEST_TAG_PREFIX
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.settings.FeatureSettings
import com.riffle.core.domain.launcher.settings.HomeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home mode (ADR 0003, Amendment 1) through the real [HomeDestination]: "Placed items" is Home as it was; "App library"
 * shows the paged Library as Home, with the Dock and the handle where they were, and the placed layout kept, so
 * switching back brings it back untouched.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class HomeModeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<LauncherShellAction>()
    private var shell by mutableStateOf(ScreenshotFixtures.homeState().withManyApps())

    private fun show(state: LauncherShellState = shell) {
        shell = state
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = shell,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = emptyStreamHost(),
                    onAction = { action ->
                        actions += action
                        shell = shell.answering(action)
                    },
                )
            }
        }
        composeRule.waitForIdle()
    }

    // Simple mode is how Home shows the app library now (the stored app-library Home mode is migrated away).
    private fun appLibraryHome() =
        ScreenshotFixtures.homeState().withManyApps().withFeatures(FeatureSettings(simpleMode = true))

    @Test
    fun anArrangedHomeIsTheDefault() {
        assertEquals(HomeMode.PlacedItems, shell.launcherSettings.home.mode)
        show()

        composeRule.onNodeWithTag(HOME_WORKSPACE_GRID_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(LIBRARY_PAGED_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun appLibraryShowsEveryAppOnPagesAsHomeWithTheDockAndTheHandle() {
        show(appLibraryHome())

        composeRule.onNodeWithTag(LIBRARY_PAGED_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(HOME_WORKSPACE_GRID_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Alarm").assertIsDisplayed()
        composeRule.onNodeWithTag(HOME_DOCK_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Page selector")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Page 1 of 2"))
    }

    @Test
    fun theAppLibraryPagesSwipeAndQuickDragLikeHomesAndLaunchAnApp() {
        show(appLibraryHome())

        composeRule.onNodeWithTag("${LIBRARY_PAGE_TEST_TAG_PREFIX}0").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Zoo").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Page selector").performTouchInput {
            swipe(start = Offset(width - 1f, height / 2f), end = Offset(1f, height / 2f))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Alarm").assertIsDisplayed()

        composeRule.onNodeWithText("Alarm").performClick()
        // Home is not a sheet: launching leaves it open and sends nothing else.
        assertEquals(
            listOf<LauncherShellAction>(LauncherShellAction.LaunchApp(pagedApp("Alarm").identity)),
            actions,
        )
    }

    @Test
    fun theHandleSearchesFromTheAppLibraryHomeToo() {
        show(appLibraryHome())

        composeRule.onNodeWithContentDescription("Search").performClick()

        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenSearch), actions)
    }

    @Test
    fun simpleModeShowsTheAppLibraryWhateverHomeModeSaysAndOffersNoWayToPlacedItems() {
        show(ScreenshotFixtures.homeState().withManyApps().withFeatures(FeatureSettings(simpleMode = true)))

        composeRule.onNodeWithTag(LIBRARY_PAGED_TEST_TAG).assertIsDisplayed()
        val labels =
            composeRule.onNodeWithTag(LIBRARY_PAGED_TEST_TAG).fetchSemanticsNode()
                .config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf("Settings"), labels)
    }

    @Test
    fun appLibraryHomeCompact() {
        show(appLibraryHome())
        composeRule.captureScreen()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun appLibraryHomeCompactDark() {
        show(appLibraryHome())
        composeRule.captureScreen()
    }
}
