package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.stream.STREAM_OPTIONS_TEST_TAG
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamSurface
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamWideView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Stream on an unfolded foldable (ADR 0003, Views; P13 and P14): two pages side by side, and the Stage view
 * its rail (full, and collapsed to icons in a narrow pane), its menu and in the dark.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
class StreamUnfoldedScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unfoldedPagesShowTwoSideBySide() = surface(initialPageKey = StreamPageKeys.CHAT, wide = true)

    @Test
    fun unfoldedStageWithItsRail() =
        surface(
            layout = StreamLayout(wideView = StreamWideView.STAGE),
            initialPageKey = StreamPageKeys.CHAT,
            wide = true,
        )

    @Test
    fun unfoldedStageOnTheNowStage() =
        surface(
            layout = StreamLayout(wideView = StreamWideView.STAGE),
            initialPageKey = StreamPageKeys.NOW,
            wide = true,
        )

    @Test
    fun unfoldedStageInANarrowPaneCollapsesTheRailToIcons() =
        surface(
            layout = StreamLayout(wideView = StreamWideView.STAGE),
            initialPageKey = StreamPageKeys.CHAT,
            wide = true,
            paneWidth = 420.dp,
        )

    @Test
    fun unfoldedStageMenuOffersPagesAndStage() {
        surface(layout = StreamLayout(wideView = StreamWideView.STAGE), wide = true)
        composeRule.onNodeWithTag(STREAM_OPTIONS_TEST_TAG).performClick()
        composeRule.captureScreen()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun unfoldedStageInTheDark() =
        surface(
            layout = StreamLayout(wideView = StreamWideView.STAGE),
            initialPageKey = StreamPageKeys.MAIL,
            wide = true,
        )

    private fun surface(
        layout: StreamLayout = StreamLayout.DEFAULT,
        initialPageKey: String? = null,
        wide: Boolean = false,
        paneWidth: Dp? = null,
    ) {
        composeRule.setContent {
            ScreenshotBackdrop {
                Box(
                    modifier =
                        if (paneWidth != null) {
                            Modifier.width(
                                paneWidth,
                            ).fillMaxSize()
                        } else {
                            Modifier.fillMaxSize()
                        },
                ) {
                    StreamSurface(
                        layout = layout,
                        results = StreamSection.layoutSections.associateWith { StreamFixtures.ready(it) },
                        env = StreamFixtures.environment(),
                        callbacks = StreamCallbacks(),
                        contentPadding = PaddingValues(top = 48.dp, bottom = 96.dp),
                        initialPageKey = initialPageKey,
                        wideLayout = wide,
                    )
                }
            }
        }
        composeRule.captureScreen()
    }
}
