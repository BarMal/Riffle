package com.riffle.app.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.SEARCH_WEB_PILL_TEST_TAG
import com.riffle.app.launcher.search.DOCK_SEARCH_FIELD_TEST_TAG
import com.riffle.core.domain.launcher.LauncherShellState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Search from the Dock (batch 7, bug C): the way on to the web is on the results' own surface, above the field. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockSearchPanelTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val dp get() = composeRule.density.density

    private fun show(state: LauncherShellState) {
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = state,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = emptyStreamHost(),
                    onAction = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun assertTheWebActionIsNotFloating() {
        val pill = composeRule.onNodeWithTag(SEARCH_WEB_PILL_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val field = composeRule.onNodeWithTag(DOCK_SEARCH_FIELD_TEST_TAG).fetchSemanticsNode().boundsInRoot
        // Only the results surface's own padding and the small seam stand between the action and the field.
        val gap = field.top - pill.bottom
        assertTrue("the action floats ${gap / dp} dp above the field", gap in 0f..20f * dp)
    }

    @Test
    fun withNoResultsTheWebActionIsOnTheEmptyAnswersSurface() {
        show(ScreenshotFixtures.homeState().searching(query = "help", results = emptyList()))
        assertTheWebActionIsNotFloating()
    }

    @Test
    fun withResultsTheWebActionIsTheirFooter() {
        show(ScreenshotFixtures.homeState().searching())
        assertTheWebActionIsNotFloating()
    }
}
