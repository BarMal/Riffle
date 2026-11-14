package com.riffle.app.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import com.riffle.app.launcher.HOME_HANDLE_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.PAGE_INDICATOR_ROUNDEL_TEST_TAG
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.HomeHandle
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The handle's search glyph and page dots keep the same room at every page count: the roundel sits exactly over the
 * current page's dot, and the glyph never touches it. (With three pages the roundel used to be squeezed off its dot and
 * onto the magnifier, which showed in the paged Library's screenshot; four or more pages were fine.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class HomeHandleSpacingTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var shell by mutableStateOf(ScreenshotFixtures.homeState().withHomePages(2))

    private fun show(state: LauncherShellState) {
        shell = state
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = shell,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = emptyStreamHost(),
                    onAction = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private val dp get() = composeRule.density.density

    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun searchBounds(): Rect =
        composeRule.onNodeWithContentDescription("Search").fetchSemanticsNode().boundsInRoot

    private fun dotsBounds(): Rect =
        composeRule.onNodeWithContentDescription("Page selector").fetchSemanticsNode().boundsInRoot

    @Test
    fun theRoundelSitsOverTheFirstDotAndClearOfTheGlyphAtEveryPageCount() {
        show(shell)
        val clearances = mutableListOf<Float>()
        (2..7).forEach { pages ->
            shell = ScreenshotFixtures.homeState().withHomePages(pages)
            composeRule.waitForIdle()

            val roundel = bounds(PAGE_INDICATOR_ROUNDEL_TEST_TAG)
            val dots = dotsBounds()
            val glyphCentre = searchBounds().center.x
            // On the first dot: the dot's centre is its radius (3 dp) in from where the dots start.
            assertEquals("$pages pages: the roundel is off its dot", dots.left + 3f * dp, roundel.center.x, 1f)
            // The magnifier's ink is 18 dp wide; the roundel stays clear of it by the same room whatever the count.
            val clearance = roundel.left - (glyphCentre + HomeHandle.SEARCH_GLYPH_DP / 2f * dp)
            assertTrue("$pages pages: the roundel is ${clearance / dp} dp from the glyph", clearance >= 6f * dp)
            clearances += clearance
        }
        assertTrue("the room is the same at every page count: $clearances", clearances.all { it == clearances.first() })
    }

    @Test
    fun theRoundelSitsOverTheLastDotWhenTheLastPageIsOpen() {
        val pages = 3
        show(ScreenshotFixtures.homeState().withHomePages(pages))
        val state = ScreenshotFixtures.homeState().withHomePages(pages)
        val layout = state.homeLayout.copy(selectedPageId = state.homeLayout.pages.last().id)
        shell =
            state.copy(
                homeLayout = layout,
                homeLayoutSet = state.homeLayoutSet.copy(layouts = mapOf(state.homeLayoutSet.activeKey to layout)),
            )
        composeRule.waitForIdle()

        val roundel = bounds(PAGE_INDICATOR_ROUNDEL_TEST_TAG)
        val dots = dotsBounds()
        assertEquals(dots.right - 3f * dp, roundel.center.x, 1f)
    }

    @Test
    fun theHandleIsExactlyAsLongAsItsSizesSay() {
        show(ScreenshotFixtures.homeState().withHomePages(3))
        val handle = bounds(HOME_HANDLE_TEST_TAG)
        assertEquals(HomeHandle.pillWidthDp(3) * dp, handle.width, 1f)
        assertEquals(HomeHandle.TOUCH_TARGET_DP * dp, handle.height, 1f)
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun aColumnKeepsTheSameRoomAlongItsAxis() {
        show(ScreenshotFixtures.homeState(HomeLayoutDeviceClass.FOLDABLE).withHomePages(2))
        val clearances = mutableListOf<Float>()
        (2..6).forEach { pages ->
            shell = ScreenshotFixtures.homeState(HomeLayoutDeviceClass.FOLDABLE).withHomePages(pages)
            composeRule.waitForIdle()

            val roundel = bounds(PAGE_INDICATOR_ROUNDEL_TEST_TAG)
            val dots = dotsBounds()
            val glyphCentre = searchBounds().center.y
            assertEquals("$pages pages: the roundel is off its dot", dots.top + 3f * dp, roundel.center.y, 1f)
            assertEquals("the roundel is centred across the dots", dots.center.x, roundel.center.x, 1f)
            clearances += roundel.top - (glyphCentre + HomeHandle.SEARCH_GLYPH_DP / 2f * dp)
        }
        assertTrue("the room is the same at every page count: $clearances", clearances.all { it == clearances.first() })
        assertTrue(clearances.first() >= 6f * dp)
    }
}
