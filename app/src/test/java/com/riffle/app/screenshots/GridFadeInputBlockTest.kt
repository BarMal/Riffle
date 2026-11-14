package com.riffle.app.screenshots

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.GridFadeField
import com.riffle.app.launcher.GridFadeInputBlock
import com.riffle.app.launcher.LocalGridFade
import com.riffle.app.launcher.gridFade
import com.riffle.app.launcher.rememberGridFadeCell
import com.riffle.core.domain.launcher.stream.StreamColumnRect
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A grid cell that the Stream's fade has taken to (almost) nothing takes no input: under the column with nothing
 * peeking through, or behind an open card. A visible cell is untouched. The cell here is the real fade and input block
 * Home's grid cells and the paged Library's use, with a button underneath.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class GridFadeInputBlockTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var taps = 0

    private fun show(configure: GridFadeField.() -> Unit) {
        val field = GridFadeField()
        composeRule.setContent {
            field.density = LocalDensity.current.density
            field.configure()
            CompositionLocalProvider(LocalGridFade provides field) {
                val fade = rememberGridFadeCell()
                Box(Modifier.size(100.dp).gridFade(fade).testTag("cell")) {
                    Box(Modifier.fillMaxSize().clickable { taps += 1 })
                    GridFadeInputBlock(fade, Modifier.fillMaxSize())
                }
            }
        }
        composeRule.waitForIdle()
    }

    private val columnOverTheCell = StreamColumnRect(left = 0f, top = 0f, right = 300f, bottom = 300f)
    private val columnFarAway = StreamColumnRect(left = 5_000f, top = 5_000f, right = 5_300f, bottom = 5_300f)

    @Test
    fun aCellUnderTheColumnWithNothingPeekingThroughTakesNoInput() {
        show {
            column = { columnOverTheCell }
            presence = { 1f }
            peekThrough = false
        }
        composeRule.onNodeWithTag("cell").performTouchInput { click() }
        assertEquals("the tap reached the faded cell", 0, taps)
    }

    @Test
    fun aCellBehindAnOpenCardTakesNoInputEvenWithAppsPeekingThroughTheRestingStack() {
        show {
            column = { columnOverTheCell }
            presence = { 1f }
            peekThrough = true
            open = { 1f }
        }
        composeRule.onNodeWithTag("cell").performTouchInput { click() }
        assertEquals("the tap reached a cell behind the open card", 0, taps)
    }

    @Test
    fun aCellPeekingThroughTheRestingStackIsStillThere() {
        show {
            column = { columnOverTheCell }
            presence = { 1f }
            peekThrough = true
        }
        composeRule.onNodeWithTag("cell").performTouchInput { click() }
        assertEquals(1, taps)
    }

    @Test
    fun aCellClearOfTheColumnIsUntouched() {
        show {
            column = { columnFarAway }
            presence = { 1f }
            peekThrough = false
        }
        composeRule.onNodeWithTag("cell").performTouchInput { click() }
        assertEquals(1, taps)
    }

    @Test
    fun withNoColumnTheGridIsUntouched() {
        show { }
        composeRule.onNodeWithTag("cell").performTouchInput { click() }
        assertEquals(1, taps)
    }
}
