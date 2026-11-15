package com.riffle.app.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.riffle.app.launcher.LauncherShellContent
import com.riffle.app.launcher.search.DOCK_SEARCH_FIELD_TEST_TAG
import com.riffle.app.launcher.search.SEARCH_STACK_TEST_TAG
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The search stack on the narrowest window the emulator has (320 dp wide) with the keyboard up: the stack stays on the
 * screen, above the field and inside the window's width, and the front card keeps its size (only strips give way).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = NARROW)
class SearchStackNarrowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val dp get() = composeRule.density.density

    private fun bounds(tag: String): Rect =
        composeRule.onNodeWithTag(
            tag,
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot

    private fun show() {
        val base = ScreenshotFixtures.homeState()
        val state =
            base.copy(
                destination = ShellDestination.SEARCH,
                searchQuery = "m",
                searchResults = listOf(ScreenshotFixtures.maps, ScreenshotFixtures.notes, ScreenshotFixtures.clock),
                appShortcutsByApp = StreamFixtures.shortcutsOf(ScreenshotFixtures.maps),
                launcherSettings =
                    base.launcherSettings.copy(
                        motion =
                            base.launcherSettings.motion.copy(
                                reducedMotionPreference = ReducedMotionPreference.ON,
                            ),
                    ),
            )
        composeRule.setContent {
            ScreenshotBackdrop {
                LauncherShellContent(
                    state = state,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = emptyStreamHost(),
                    onAction = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun withTheKeyboardUpTheStackStaysOnScreenAboveTheFieldAndInsideTheWidth() {
        show()
        val atRest = bounds(SEARCH_STACK_TEST_TAG)
        composeRule.setImeInset((KEYBOARD_DP * dp).toInt())

        val stack = bounds(SEARCH_STACK_TEST_TAG)
        val field = bounds(DOCK_SEARCH_FIELD_TEST_TAG)
        val window = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals("the window is 320 dp wide", WINDOW_WIDTH_DP * dp, window.width, 1f)
        assertTrue("the stack's top is off the screen: $stack", stack.top >= 0f)
        assertTrue("the stack is wider than the window: $stack", stack.left >= 0f && stack.right <= window.right + 1f)
        assertTrue("the stack does not stand on the field: $stack vs $field", stack.bottom <= field.top + 1f)
        assertTrue("the field is under the keyboard", field.bottom <= window.bottom - KEYBOARD_DP * dp + 1f)
        assertTrue("the stack grew with the keyboard up: $stack vs $atRest", stack.height <= atRest.height + 1f)
        // Strips give way first: the front card keeps its room.
        assertTrue("less than a card's room: ${stack.height / dp} dp", stack.height / dp >= MIN_FRONT_DP)
    }

    private companion object {
        const val KEYBOARD_DP = 260
        const val WINDOW_WIDTH_DP = 320
        const val MIN_FRONT_DP = 150f
    }
}

private const val NARROW = "w320dp-h640dp-normal-long-notround-any-420dpi-keyshidden-nonav"
