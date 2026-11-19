package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.riffle.app.launcher.HOME_SELECTION_BAR_TEST_TAG
import com.riffle.app.launcher.HOME_SELECTION_CLEAR_TEST_TAG
import com.riffle.app.launcher.HOME_SELECTION_COUNT_TEST_TAG
import com.riffle.app.launcher.HOME_SELECTION_FOLDER_TEST_TAG
import com.riffle.app.launcher.HOME_SELECTION_REMOVE_TEST_TAG
import com.riffle.app.launcher.HOME_SELECTION_SELECT_ALL_TEST_TAG
import com.riffle.app.launcher.HomeSelectionBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The selection row of the Edit page bar (ADR 0003, Amendment 15): the count at the start, the actions grouped at the
 * end, wrapping cleanly onto a second line at 320 dp and one line when wide; every target at least 48 dp high.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = COMPACT_320)
class HomeSelectionBarLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(selected: Int = 3) {
        composeRule.setContent {
            ScreenshotBackdrop {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
                    HomeSelectionBar(selectedCount = selected, canMakeFolder = true, onAction = {})
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String) = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private val actionTags =
        listOf(
            HOME_SELECTION_SELECT_ALL_TEST_TAG,
            HOME_SELECTION_CLEAR_TEST_TAG,
            HOME_SELECTION_REMOVE_TEST_TAG,
            HOME_SELECTION_FOLDER_TEST_TAG,
        )

    private fun checkDeliberate(expectedRows: Int) {
        val bar = bounds(HOME_SELECTION_BAR_TEST_TAG)
        val count = bounds(HOME_SELECTION_COUNT_TEST_TAG)
        val actions = actionTags.map(::bounds)
        val dp = composeRule.density.density

        assertTrue("the count is at the start", count.left - bar.left <= 16 * dp)
        val firstRow = actions.filter { it.top == actions.minOf { a -> a.top } }
        assertTrue("the count is left of the first line's actions", firstRow.all { count.right <= it.left + 1f })
        val rows = actions.map { it.top }.distinct().size
        assertEquals("rows of actions", expectedRows, rows)
        assertTrue("the actions end at the bar's end", bar.right - actions.maxOf { it.right } <= 1f)
        assertEquals("the count stands on the first row", actions.minOf { it.top }, count.top, 1f)
        actionTags.forEach { composeRule.onNodeWithTag(it).assertHeightIsAtLeast(48.dp) }
        composeRule.onNodeWithTag(HOME_SELECTION_COUNT_TEST_TAG).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun at320dpTheActionsWrapAndTheCountStaysAtTheStartOfTheFirstLine() {
        show()
        composeRule.onNodeWithTag(HOME_SELECTION_BAR_TEST_TAG).captureRoboImage()
        val rows = actionTags.map { bounds(it).top }.distinct().size
        assertTrue("wrapped onto $rows rows", rows >= 2)
        val bar = bounds(HOME_SELECTION_BAR_TEST_TAG)
        assertTrue(bar.width <= 320 * composeRule.density.density)
        checkDeliberate(expectedRows = rows)
    }

    @Test
    @Config(qualifiers = WIDE_840)
    fun whenWideTheCountIsAtTheStartAndTheActionsAreOneGroupAtTheEnd() {
        show()
        composeRule.onNodeWithTag(HOME_SELECTION_BAR_TEST_TAG).captureRoboImage()
        checkDeliberate(expectedRows = 1)
    }

    @Test
    fun withNothingTickedOnlySelectAllShowsAtTheEnd() {
        show(selected = 0)
        val bar = bounds(HOME_SELECTION_BAR_TEST_TAG)
        val all = bounds(HOME_SELECTION_SELECT_ALL_TEST_TAG)
        assertTrue(bar.right - all.right <= 1f)
        composeRule.onNodeWithTag(HOME_SELECTION_SELECT_ALL_TEST_TAG).assertHeightIsAtLeast(48.dp)
    }
}

private const val COMPACT_320 = "w320dp-h640dp-normal-long-notround-any-xhdpi-keyshidden-nonav"
private const val WIDE_840 = "w840dp-h900dp-large-notlong-notround-any-xhdpi-keyshidden-nonav"
