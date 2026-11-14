package com.riffle.app.screenshots

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.riffle.app.launcher.RiffleLauncherTheme
import com.riffle.app.launcher.stream.STREAM_OPEN_CARD_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_PAGER_TEST_TAG
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamDockLink
import com.riffle.app.launcher.stream.StreamSurface
import com.riffle.app.launcher.stream.streamStackCardTag
import com.riffle.core.domain.launcher.notifications.NotificationPriority
import com.riffle.core.domain.launcher.stream.NotificationAction
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.StreamAppRef
import com.riffle.core.domain.launcher.stream.StreamContentId
import com.riffle.core.domain.launcher.stream.StreamGroup
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamPrivacy
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.workspace.ItemTarget
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * A strip tucks under the card in front of it (device fixes, round 6, item 5), read back as pixels over a backdrop of
 * one colour no card has: between a strip and the card in front no row shows the backdrop (no gap); just above the
 * front card's rounded top corner, beside its arc, the strip's own pixels show (the cut follows the corner, it is not
 * a straight line); outside both cards the backdrop shows (nothing is drawn where no card is); and the strip is no
 * darker along the cut than higher up (the two translucent fills do not overlap). At rest, with the stack between two
 * cards, and with a card open.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamStripCutPixelsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val page = StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, "chat:personal")
    private val density get() = composeRule.density.density

    private fun note(id: Int) =
        NotificationContent(
            id = StreamContentId("n:chat:$id"),
            app = StreamAppRef("chat", "personal"),
            appLabel = "Chat",
            title = "Card $id",
            text = "One line of card $id",
            icon = null,
            image = null,
            postedAtEpochMillis = StreamFixtures.NOW - id * 60_000L,
            category = null,
            priority = NotificationPriority.DEFAULT,
            target = ItemTarget.None,
            actions = setOf(NotificationAction.OPEN),
            privacy = StreamPrivacy.VISIBLE,
            notificationKey = "k:chat:$id",
        )

    private fun show() {
        val results: Map<StreamSection, StreamSectionResult> =
            StreamSection.layoutSections.associateWith<StreamSection, StreamSectionResult> {
                StreamSectionResult.Empty
            } +
                (
                    StreamSection.NOTIFICATIONS to
                        StreamSectionResult.Ready(listOf(StreamGroup("chat:personal", "Chat", (1..4).map(::note))))
                )
        composeRule.setContent {
            RiffleLauncherTheme {
                Box(modifier = Modifier.fillMaxSize().background(BACKDROP)) {
                    StreamSurface(
                        layout = StreamLayout.DEFAULT,
                        results = results,
                        env = StreamFixtures.environment(reducedMotion = true),
                        callbacks = StreamCallbacks(),
                        initialPageKey = page,
                        dockLink = StreamDockLink(),
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun slot(id: Int): SemanticsNode =
        composeRule.onNodeWithTag(streamStackCardTag("n:chat:$id")).fetchSemanticsNode()

    private fun capture(): Bitmap = composeRule.activity.window.decorView.drawToBitmap()

    private fun Bitmap.isBackdrop(
        x: Float,
        y: Float,
    ): Boolean {
        val pixel = getPixel(x.toInt(), y.toInt())
        return abs(android.graphics.Color.red(pixel) - BACKDROP_RED) < CLOSE &&
            android.graphics.Color.green(pixel) < CLOSE &&
            abs(android.graphics.Color.blue(pixel) - BACKDROP_BLUE) < CLOSE
    }

    private fun Bitmap.brightness(
        x: Float,
        y: Float,
    ): Int {
        val pixel = getPixel(x.toInt(), y.toInt())
        return android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) +
            android.graphics.Color.blue(pixel)
    }

    /**
     * The seam between the card whose top-left corner is ([frontLeft], [frontTop]), [frontWidth] wide, and the strip
     * behind it.
     */
    private fun assertTheStripTucksUnder(
        image: Bitmap,
        frontLeft: Float,
        frontTop: Float,
        frontWidth: Float,
        what: String,
    ) {
        val d = density
        // No gap: every row from well inside the strip to well inside the card in front is a card's, not the backdrop.
        for (x in listOf(frontLeft + frontWidth / 2f, frontLeft + 60 * d, frontLeft + frontWidth - 60 * d)) {
            var y = frontTop - 20 * d
            while (y <= frontTop + 12 * d) {
                assertTrue("$what: a gap row at x=$x y=${y - frontTop} px from the card's top", !image.isBackdrop(x, y))
                y += 1f
            }
        }
        // The rounded cut: beside the front card's corner arc (10 dp in, 2 dp down: the arc is 5 dp down there) the
        // strip shows, on both sides. A straight cut along the card's top would leave the backdrop there.
        for (x in listOf(frontLeft + 10 * d, frontLeft + frontWidth - 10 * d)) {
            assertTrue("$what: the strip shows beside the corner at x=$x", !image.isBackdrop(x, frontTop + 2 * d))
        }
        // Outside both cards (in the very corner, which the strip, being narrower, does not reach) the backdrop shows.
        assertTrue("$what: the corner itself is empty", image.isBackdrop(frontLeft + 1.5f * d, frontTop + 1.5f * d))
        // No doubly opaque band: the strip is as light just above the cut as higher up.
        val x = frontLeft + frontWidth - 70 * d
        val nearCut = image.brightness(x, frontTop - 3 * d)
        val higher = image.brightness(x, frontTop - 14 * d)
        assertTrue("$what: the strip is darker along the cut ($nearCut vs $higher)", abs(nearCut - higher) < BAND)
    }

    @Test
    fun atRestEveryStripTucksUnderTheCardInFrontOfIt() {
        show()
        val image = capture()
        val front = slot(1)
        assertTheStripTucksUnder(
            image,
            front.positionInRoot.x,
            front.positionInRoot.y,
            front.size.width.toFloat(),
            "the front card",
        )
        // The strip behind the first strip tucks under it the same way (the first strip is drawn a step smaller).
        val strip = slot(2).boundsInRoot
        assertTheStripTucksUnder(image, strip.left, strip.top, strip.width, "the first strip")
    }

    @Test
    fun theCutFollowsTheCardInFrontWhileTheStackIsHeldBetweenTwoCards() {
        show()
        // Hold the stack a third of the way to the next card: the front card is sinking and the next one opening.
        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput {
            down(Offset(centerX, height * 0.5f))
            repeat(10) { moveBy(Offset(0f, 4.dp.toPx()), delayMillis = 16) }
        }
        composeRule.waitForIdle()
        val image = capture()
        // Between the second strip and the first (neither is the sinking card): no gap, a rounded cut.
        val strip = slot(2).boundsInRoot
        assertTheStripTucksUnder(image, strip.left, strip.top, strip.width, "the first strip while dragging")
        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput { up() }
    }

    @Test
    fun withACardOpenTheStripBehindTucksUnderTheOpenCard() {
        show()
        composeRule.onNodeWithText("Card 1").performClick()
        composeRule.waitForIdle()
        val open = composeRule.onNodeWithTag(STREAM_OPEN_CARD_TEST_TAG).fetchSemanticsNode()
        assertTheStripTucksUnder(
            capture(),
            open.positionInRoot.x,
            open.positionInRoot.y,
            open.size.width.toFloat(),
            "the open card",
        )
    }

    private companion object {
        /** A colour no card is: full red and blue, no green. */
        val BACKDROP = Color(0xFFFF00FF)
        const val BACKDROP_RED = 255
        const val BACKDROP_BLUE = 255
        const val CLOSE = 12
        const val BAND = 18
    }
}
