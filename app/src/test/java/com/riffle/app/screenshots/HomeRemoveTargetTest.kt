package com.riffle.app.screenshots

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import com.riffle.app.launcher.HOME_REMOVE_TARGET_PROBE_KEY
import com.riffle.app.launcher.HOME_REMOVE_TARGET_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherHapticEvent
import com.riffle.app.launcher.LauncherHaptics
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LocalRecompositionProbe
import com.riffle.app.launcher.RecompositionProbe
import com.riffle.core.domain.launcher.home.AppShortcutItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drop to remove: while an item of Home is lifted the Remove target shows; dropping on it removes the item as the
 * menu's "Remove from Home" does, with one light tick as the finger enters it, and the hover recomposes nothing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class HomeRemoveTargetTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<LauncherShellAction>()
    private val events = mutableListOf<LauncherHapticEvent>()
    private val probe = RecompositionProbe()
    private var shell by mutableStateOf(ScreenshotFixtures.homeState())

    private val haptics =
        object : LauncherHaptics {
            override fun longPress() = Unit

            override fun perform(event: LauncherHapticEvent) {
                events += event
            }
        }

    private fun show() {
        composeRule.setContent {
            ScreenshotBackdrop {
                CompositionLocalProvider(LocalRecompositionProbe provides probe) {
                    HomeDestination(
                        state = shell,
                        appIconLoader = SolidColorAppIconLoader(),
                        stream = emptyStreamHost(),
                        haptics = haptics,
                        onAction = { action -> actions += action },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun firstApp(): AppShortcutItem =
        shell.homeLayout.selectedPage.items.filterIsInstance<AppShortcutItem>().first()

    private fun lift() {
        val start = composeRule.onNodeWithText(firstApp().label).fetchSemanticsNode().boundsInRoot.center
        composeRule.onRoot().performTouchInput {
            down(start)
            advanceEventTime(700)
            moveBy(Offset(40f, 40f))
        }
        composeRule.waitForIdle()
    }

    private fun targetCentre() =
        composeRule.onNodeWithTag(HOME_REMOVE_TARGET_TEST_TAG).fetchSemanticsNode().boundsInRoot.center

    @Test
    fun theTargetIsOnlyThereWhileSomethingIsLifted() {
        show()
        composeRule.onAllNodesWithTag(HOME_REMOVE_TARGET_TEST_TAG).assertCountEquals(0)
        composeRule.captureScreen()
        lift()
        composeRule.onNodeWithTag(HOME_REMOVE_TARGET_TEST_TAG).assertIsDisplayed()
        composeRule.captureScreen()
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(HOME_REMOVE_TARGET_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun theTargetIsAtLeast48dpAndLabelled() {
        show()
        lift()
        val bounds = composeRule.onNodeWithTag(HOME_REMOVE_TARGET_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val dp = composeRule.density.density
        assertTrue("${bounds.height}", bounds.height >= 48 * dp)
        assertTrue("${bounds.width}", bounds.width >= 48 * dp)
        composeRule.onNodeWithText("Remove").assertIsDisplayed()
        composeRule.onRoot().performTouchInput { up() }
    }

    @Test
    fun droppingAnItemOnTheTargetRemovesItFromItsPageAndTicksOnEntering() {
        show()
        val item = firstApp()
        lift()
        val before = events.count { it == LauncherHapticEvent.DETENT }
        val target = targetCentre()
        composeRule.onRoot().performTouchInput { moveTo(target) }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
        composeRule.captureScreen()
        assertEquals("one tick on entering", before + 1, events.count { it == LauncherHapticEvent.DETENT })
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()

        val removal = actions.filterIsInstance<LauncherShellAction.RemoveHomeShortcut>().single()
        assertEquals(item.id, removal.itemId)
        assertEquals(shell.homeLayout.selectedPageId, removal.pageId)
        assertTrue(actions.toString(), actions.none { it is LauncherShellAction.DropHomeItem })
    }

    @Test
    fun releasingElsewhereDoesNotRemoveAnything() {
        show()
        lift()
        composeRule.onRoot().performTouchInput { moveBy(Offset(60f, 0f)) }
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()
        assertTrue(actions.toString(), actions.none { it is LauncherShellAction.RemoveHomeShortcut })
    }

    @Test
    fun movingTheFingerAroundDoesNotRecomposeTheTargetAndEnteringItDoesNotEither() {
        show()
        lift()
        val settled = probe.count(HOME_REMOVE_TARGET_PROBE_KEY)
        val target = targetCentre()
        composeRule.onRoot().performTouchInput {
            repeat(20) { moveBy(Offset(3f, 3f)) }
            moveTo(target)
        }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
        // Entering flips the hover once; the target recomposes for that and for nothing else.
        assertTrue(
            "${probe.count(HOME_REMOVE_TARGET_PROBE_KEY)} after $settled",
            probe.count(HOME_REMOVE_TARGET_PROBE_KEY) - settled <= 2,
        )
        composeRule.onRoot().performTouchInput { up() }
    }
}
