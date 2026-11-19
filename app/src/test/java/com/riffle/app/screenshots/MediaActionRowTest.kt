package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.github.takahirom.roborazzi.captureRoboImage
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.stream.CardActionControls
import com.riffle.app.launcher.stream.MEDIA_SECONDARY_TEST_TAG
import com.riffle.app.launcher.stream.MEDIA_TRANSPORT_TEST_TAG
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamCardActions
import com.riffle.core.domain.launcher.stream.StreamContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

private const val ROW_HOST = "media-row-host"

/**
 * The open player's actions row as the Dock holds it (folded): the transport centred as a group of 48 dp targets with
 * play and pause as the primary, "Open player" at the start when it fits beside it and, on the narrowest screen, on a
 * second row spread evenly. Light and dark; every control reaches the callbacks.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class MediaActionRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val performed = mutableListOf<Pair<StreamContent, StreamAction>>()

    @Test
    fun dockMediaActionsLight() = show(width = 411)

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun dockMediaActionsDark() = show(width = 411)

    @Test
    fun dockMediaActionsOnTheNarrowestScreen() = show(width = 320)

    @Test
    fun theTransportIsCentredAtEvenSpacingAndEveryTargetIs48dp() {
        show(width = 411, capture = false)
        val previous = bounds("Previous track")
        val play = bounds("Pause")
        val next = bounds("Next track")
        listOf(previous, next).forEach { assertTrue(it.width.value >= 47.9f && it.height.value >= 47.9f) }
        assertTrue("play is the primary, larger than the rest", play.width > previous.width)
        assertEquals("even gaps", (play.left - previous.right).value, (next.left - play.right).value, 0.5f)
        val rowCentre = (previous.left + next.right) / 2
        assertTrue("centred on the row", abs((rowCentre - hostLeft() - 411.dp / 2).value) < 1f)
        composeRule.onNodeWithContentDescription(
            "Previous track",
        ).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun openPlayerSitsBesideTheTransportWhenItFitsAndBelowItWhenItDoesNot() {
        show(width = 411, capture = false)
        val wide = composeRule.onNodeWithTag(MEDIA_SECONDARY_TEST_TAG).getUnclippedBoundsInRoot()
        val wideTransport = composeRule.onNodeWithTag(MEDIA_TRANSPORT_TEST_TAG).getUnclippedBoundsInRoot()
        assertTrue("on the transport's row", wide.top < wideTransport.bottom)
        assertEquals("at the start, inside the row's 8 dp padding", 8f, (wide.left - hostLeft()).value, 0.5f)
    }

    @Test
    fun onTheNarrowestScreenTheSecondRowIsBelowAndCentredNotLeftPacked() {
        show(width = 320, capture = false)
        val secondary = composeRule.onNodeWithTag(MEDIA_SECONDARY_TEST_TAG).getUnclippedBoundsInRoot()
        val transport = composeRule.onNodeWithTag(MEDIA_TRANSPORT_TEST_TAG).getUnclippedBoundsInRoot()
        assertTrue("below the transport", secondary.top >= transport.bottom)
        val centre = (secondary.left + secondary.right) / 2
        assertEquals("one action spread evenly is centred", 160f, (centre - hostLeft()).value, 1f)
    }

    @Test
    fun everyControlReachesTheCallbacksAndIsNamed() {
        show(width = 411, capture = false)
        composeRule.onNodeWithContentDescription("Previous track").performClick()
        composeRule.onNodeWithContentDescription("Pause").performClick()
        composeRule.onNodeWithContentDescription("Next track").performClick()
        composeRule.onNodeWithText("Open player").performClick()
        assertEquals(
            listOf(StreamAction.Previous, StreamAction.Pause, StreamAction.Next, StreamAction.Open),
            performed.map { it.second },
        )
    }

    private fun bounds(label: String) = composeRule.onNodeWithContentDescription(label).getUnclippedBoundsInRoot()

    private fun hostLeft() = composeRule.onNodeWithTag(ROW_HOST).getUnclippedBoundsInRoot().left

    private fun show(
        width: Int,
        capture: Boolean = true,
    ) {
        mediaRow(composeRule, width, performed)
        if (capture) {
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(ROW_HOST).captureRoboImage()
        }
    }
}

/** The same row on an unfolded posture, where a side Dock leaves the actions on the card: a column's width. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
class MediaActionRowUnfoldedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dockMediaActionsUnfolded() {
        mediaRow(composeRule, 420, mutableListOf())
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ROW_HOST).captureRoboImage()
    }
}

private fun mediaRow(
    composeRule: androidx.compose.ui.test.junit4.ComposeContentTestRule,
    width: Int,
    performed: MutableList<Pair<StreamContent, StreamAction>>,
) {
    val content: MediaContent = StreamFixtures.media
    composeRule.setContent {
        ScreenshotBackdrop {
            Box(modifier = Modifier.padding(vertical = 16.dp), contentAlignment = Alignment.TopStart) {
                GlassSurface(
                    modifier = Modifier.width(width.dp).testTag(ROW_HOST),
                    weight = GlassWeight.Thick,
                    shape = RiffleShapes.large,
                    tint = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    CardActionControls(
                        content = content,
                        actions = StreamCardActions.forContent(content),
                        callbacks = StreamCallbacks(onAction = { c, a -> performed += c to a }),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}
