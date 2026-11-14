package com.riffle.app.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.HOME_WORKSPACE_GRID_OVERFLOW_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.settings.FeatureSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The "More apps" button on a generated Home page opens the Library, and search while the Library is off: there is no
 * drawer to open then, and the button used to do nothing. (Simple mode turns the Library off too, but shows the App
 * library as Home, where no generated page is drawn.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class GeneratedPageOverflowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<LauncherShellAction>()

    private fun generatedPageWithOverflow(): LauncherShellState {
        val state = ScreenshotFixtures.homeState()
        val page =
            state.homeLayout.selectedPage.copy(
                type = LauncherPageType.Generated(GeneratedLauncherPageKind.APP),
                items = emptyList(),
                generatedContentOverflowCount = 3,
            )
        val layout = state.homeLayout.copy(pages = listOf(page), selectedPageId = page.id)
        return state.copy(
            homeLayout = layout,
            homeLayoutSet = state.homeLayoutSet.copy(layouts = mapOf(state.homeLayoutSet.activeKey to layout)),
        )
    }

    private fun tapMoreApps(state: LauncherShellState) {
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = state,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = emptyStreamHost(),
                    onAction = { action -> actions += action },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(HOME_WORKSPACE_GRID_OVERFLOW_TEST_TAG).performClick()
    }

    @Test
    fun withTheLibraryOnItOpensTheLibrary() {
        tapMoreApps(generatedPageWithOverflow())
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenAppDrawer), actions)
    }

    @Test
    fun withTheLibraryOffItOpensSearchInstead() {
        val state = generatedPageWithOverflow().withFeatures(FeatureSettings(library = false))
        tapMoreApps(state)
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenSearch), actions)
    }
}
