package com.riffle.app.screenshots.expressions

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.expressions.ITEM_HIDE_BUTTON_TEST_TAG
import com.riffle.app.launcher.expressions.ItemHideButton
import com.riffle.app.launcher.expressions.ItemHider
import com.riffle.app.launcher.expressions.LocalItemHider
import com.riffle.app.launcher.expressions.rememberItemHideMenu
import com.riffle.app.screenshots.ScreenshotBackdrop
import com.riffle.app.screenshots.ScreenshotDevices
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.settings.HideKind
import com.riffle.core.domain.launcher.workspace.testing.fakeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The contextual Hide affordance the Stream's rows and cards use: with a handler provided an item has a "More options"
 * button that opens the choices; without one it draws nothing. No text field is involved.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class ItemHideMenuScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val picked = mutableListOf<Pair<Item, HideKind>>()

    private val message = fakeItem(id = "m1", sourceId = "notifications", title = "Alex")

    private fun render(withHider: Boolean) {
        composeRule.setContent {
            val hider = if (withHider) ItemHider { item, kind -> picked += item to kind } else null
            ScreenshotBackdrop {
                CompositionLocalProvider(LocalItemHider provides hider) {
                    Row {
                        Text(text = "Alex")
                        ItemHideButton(rememberItemHideMenu(message))
                    }
                }
            }
        }
    }

    @Test
    fun anItemShowsAMoreOptionsButtonWhenAHandlerIsProvided() {
        render(withHider = true)

        assertTrue(composeRule.onAllNodesWithTag(ITEM_HIDE_BUTTON_TEST_TAG).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun anItemIsUnchangedWithoutAHandler() {
        render(withHider = false)

        composeRule.onAllNodesWithTag(ITEM_HIDE_BUTTON_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun choosingAnEntryHandsTheItemAndKindToTheHandler() {
        render(withHider = true)

        composeRule.onAllNodesWithTag(ITEM_HIDE_BUTTON_TEST_TAG)[0].performClick()
        composeRule.onNodeWithText("Hide this notification").performClick()

        assertEquals(1, picked.size)
        assertEquals(HideKind.ITEM, picked.single().second)
    }
}
