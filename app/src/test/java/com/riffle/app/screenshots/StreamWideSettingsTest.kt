package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.SettingsStreamSections
import com.riffle.app.launcher.streamWideViewTestTag
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamLayoutReducer
import com.riffle.core.domain.launcher.stream.StreamWideView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings > Stream on an expanded posture: the whole-Stream choice, Pages or Stage, writes the stored layout. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
class StreamWideSettingsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var layout by mutableStateOf(StreamLayout.DEFAULT)

    private fun show(showWideView: Boolean) {
        composeRule.setContent {
            ScreenshotBackdrop {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SettingsStreamSections(
                        layout = layout,
                        onLayoutAction = { action -> layout = StreamLayoutReducer.reduce(layout, action) },
                        onOpenPage = {},
                        showWideView = showWideView,
                    )
                }
            }
        }
    }

    @Test
    fun anExpandedPostureOffersPagesAndStageAndStoresTheChoice() {
        show(showWideView = true)
        composeRule.onNodeWithTag(streamWideViewTestTag(StreamWideView.PAGES)).assertIsSelected()

        composeRule.onNodeWithTag(streamWideViewTestTag(StreamWideView.STAGE)).performClick()

        assertEquals(StreamWideView.STAGE, layout.wideView)
        composeRule.onNodeWithTag(streamWideViewTestTag(StreamWideView.STAGE)).assertIsSelected()
        composeRule.captureScreen()
    }

    @Test
    fun aPhonePostureHasNoSuchChoice() {
        show(showWideView = false)
        composeRule.onAllNodesWithTagCount(streamWideViewTestTag(StreamWideView.STAGE), 0)
    }

    private fun ComposeContentTestRule.onAllNodesWithTagCount(
        tag: String,
        expected: Int,
    ) {
        onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, tag)).assertCountEquals(expected)
    }
}
