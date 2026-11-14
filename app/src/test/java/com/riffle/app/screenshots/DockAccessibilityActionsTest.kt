package com.riffle.app.screenshots

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.riffle.app.launcher.HOME_DOCK_PULL_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The dock's accessibility actions are one list: "Open Library" from the pull, "Show live pages" from the press and
 * "Search" from the handle.
 * Two semantics
 * modifiers on one node do not merge their custom actions, so a regression here would silently drop one set from
 * TalkBack. There is no "Workspace menu" action any more.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockAccessibilityActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theDockOffersTheLibraryAndStreamActionsAndNoWorkspaceMenu() {
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = ScreenshotFixtures.homeState(),
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = emptyStreamHost(),
                    onAction = {},
                )
            }
        }

        val actions =
            composeRule
                .onNodeWithTag(HOME_DOCK_PULL_TEST_TAG)
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
        assertEquals(listOf("Open Library", "Show live pages", "Search"), actions.map { it.label })
    }
}
