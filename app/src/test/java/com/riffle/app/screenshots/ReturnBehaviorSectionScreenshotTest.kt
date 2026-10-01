package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.SettingsReturnBehaviorSection
import com.riffle.app.launcher.returnBehaviorTestTag
import com.riffle.core.domain.launcher.workspace.ReturnBehavior
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Settings > Workspaces "Returning to Home" radio group, drawn directly with a fixed selection. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class ReturnBehaviorSectionScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val picked = mutableListOf<ReturnBehavior>()

    private fun render(current: ReturnBehavior) {
        composeRule.setContent {
            ScreenshotBackdrop {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                ) {
                    SettingsReturnBehaviorSection(current = current, onSelect = { picked += it })
                }
            }
        }
        composeRule.captureScreen()
    }

    @Test
    fun restoreSelectedByDefaultCompact() {
        render(ReturnBehavior.RESTORE)

        composeRule.onNodeWithTag(returnBehaviorTestTag(ReturnBehavior.RESTORE)).assertIsSelected()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun startPageSelectedCompactDark() {
        render(ReturnBehavior.START_PAGE)

        composeRule.onNodeWithTag(returnBehaviorTestTag(ReturnBehavior.START_PAGE)).assertIsSelected()
    }

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun firstPageSelectedLargeFont() {
        render(ReturnBehavior.FIRST_PAGE)
    }

    @Test
    fun tappingAnOptionReportsIt() {
        render(ReturnBehavior.RESTORE)

        composeRule.onNodeWithTag(returnBehaviorTestTag(ReturnBehavior.FIRST_PAGE)).performScrollTo().performClick()

        assertEquals(listOf(ReturnBehavior.FIRST_PAGE), picked)
    }
}
