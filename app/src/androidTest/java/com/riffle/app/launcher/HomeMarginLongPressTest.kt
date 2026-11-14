package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.home.GridInsets
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val MARGIN_TEST_ROOT_TAG = "home-margin-root"

/**
 * Home's background is the whole of Home that is not an item (dock modes plan, "Long-press coverage"): a long-press on
 * its bottom margin or its side margin opens the Home menu, where the menu used to be reachable only from the grid's
 * own cells. The emulator's window is only 320 dp wide, so the margins are given in the layout and the probes are
 * taken from the window's edges.
 */
@RunWith(AndroidJUnit4::class)
class HomeMarginLongPressTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<LauncherShellAction>()

    private fun layoutWithMargins(): HomeLayout =
        HomeLayoutDefaults.standard().let { standardLayout ->
            standardLayout.copy(
                settings =
                    standardLayout.settings.copy(
                        grid =
                            standardLayout.settings.grid.copy(
                                margin = GridInsets(start = 32, top = 24, end = 32, bottom = 48),
                            ),
                    ),
            )
        }

    private fun showHome() {
        val layout = layoutWithMargins()
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize().testTag(MARGIN_TEST_ROOT_TAG)) {
                    StandardHome(
                        layout = layout,
                        installedApps = emptyList(),
                        interactions = StandardHomeInteractions(),
                        presentation = StandardHomePresentation(appShortcutsByApp = emptyMap()),
                        appIconLoader = EmptyAppIconLoader,
                        onAction = { action -> actions += action },
                    )
                }
            }
        }
    }

    @Test
    fun longPressingTheBottomMarginOpensTheHomeMenu() {
        showHome()
        val bounds = composeRule.onNodeWithTag(MARGIN_TEST_ROOT_TAG).fetchSemanticsNode().boundsInRoot

        composeRule.onNodeWithTag(MARGIN_TEST_ROOT_TAG).performTouchInput {
            longClick(Offset(bounds.width / 2f, bounds.height - 4f))
        }

        composeRule.onNodeWithText("Edit page").assertExists()
        composeRule.onNodeWithText("Manage pages").assertExists()
        assertTrue("a long-press chooses nothing: $actions", actions.isEmpty())
    }

    @Test
    fun longPressingTheSideMarginOpensTheHomeMenu() {
        showHome()
        val bounds = composeRule.onNodeWithTag(MARGIN_TEST_ROOT_TAG).fetchSemanticsNode().boundsInRoot

        composeRule.onNodeWithTag(MARGIN_TEST_ROOT_TAG).performTouchInput {
            longClick(Offset(4f, bounds.height / 2f))
        }

        composeRule.onNodeWithText("Edit page").assertExists()
        assertTrue("a long-press chooses nothing: $actions", actions.isEmpty())
    }
}
