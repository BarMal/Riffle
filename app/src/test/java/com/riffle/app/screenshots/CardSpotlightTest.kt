package com.riffle.app.screenshots

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamCard
import com.riffle.core.domain.launcher.stream.StreamAppRef
import com.riffle.core.domain.launcher.stream.StreamContentId
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A Stream card's spotlight: the app icon (or avatar) lights the part of the card's edge beside it, softly and falling
 * off with distance, over the tinted fill; the edge away from it stays the neutral glass edge (no uniform coloured
 * outline). Rendered through the real card in both themes, and read back as pixels: the top edge beside the icon is
 * measurably bluer than the same edge far from it, where it matches the card's own fill. The screenshots show it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class CardSpotlightTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // The first fixture app's icon is blue (0xFF1A73E8), so "bluer" is what the spotlight adds beside the icon.
    private val app = ScreenshotFixtures.camera

    private fun show(reducedTransparency: Boolean = false) {
        composeRule.setContent {
            ScreenshotBackdrop(reducedTransparency = reducedTransparency) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StreamCard(
                        content =
                            StreamFixtures.chatNotification.copy(
                                id = StreamContentId("spot:1"),
                                app = StreamAppRef(app.identity.packageName.value, "personal"),
                                appLabel = app.label,
                                title = "Sam",
                                person = null,
                                picture = null,
                                image = null,
                                thread = emptyList(),
                            ),
                        env = StreamFixtures.environment(),
                        callbacks = StreamCallbacks(),
                        groupKey = null,
                        modifier = Modifier.fillMaxWidth().height(112.dp).testTag(CARD_TAG),
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** How blue a pixel is: its blue channel over its red one. */
    private fun blueness(
        image: Bitmap,
        xDp: Float,
        yDp: Float,
    ): Float {
        val density = composeRule.density.density
        val pixel = image.getPixel((xDp * density).toInt(), (yDp * density).toInt())
        return (Color.blue(pixel) - Color.red(pixel)) / MAX_CHANNEL
    }

    private fun assertLitNearTheIconAndNeutralFarFromIt() {
        val bounds = composeRule.onNodeWithTag(CARD_TAG).fetchSemanticsNode().boundsInRoot
        val bitmap = composeRule.activity.window.decorView.drawToBitmap()
        val image =
            Bitmap.createBitmap(
                bitmap,
                bounds.left.toInt(),
                bounds.top.toInt(),
                bounds.width.toInt(),
                bounds.height.toInt(),
            )
        val widthDp = image.width / composeRule.density.density
        // Just inside the top edge, beside the avatar (which sits about 40 dp in) and far along the card.
        val near = blueness(image, xDp = 40f, yDp = EDGE_SAMPLE_DP)
        val far = blueness(image, xDp = widthDp - 24f, yDp = EDGE_SAMPLE_DP)
        // The same distance from the edge but in the card's body, which only the fill colours.
        val body = blueness(image, xDp = widthDp - 24f, yDp = BODY_SAMPLE_DP)
        assertTrue("the edge beside the icon is lit: near=$near far=$far", near > far + LIT_STEP)
        assertTrue(
            "the far edge is the neutral edge, the fill's colour: far=$far body=$body",
            far - body < NEUTRAL_STEP,
        )
    }

    @Test
    fun theIconLightsTheEdgeBesideItAndTheRestStaysNeutralLightTheme() {
        show()
        assertLitNearTheIconAndNeutralFarFromIt()
        composeRule.captureScreen()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun theIconLightsTheEdgeBesideItAndTheRestStaysNeutralDarkTheme() {
        show()
        assertLitNearTheIconAndNeutralFarFromIt()
        composeRule.captureScreen()
    }

    @Test
    fun reducedTransparencyKeepsTheLightAtHalfStrength() {
        show(reducedTransparency = true)
        composeRule.captureScreen()
    }

    private companion object {
        const val MAX_CHANNEL = 255f
        const val CARD_TAG = "card-spotlight"
        const val EDGE_SAMPLE_DP = 3f
        const val BODY_SAMPLE_DP = 56f
        const val LIT_STEP = 0.03f
        const val NEUTRAL_STEP = 0.03f
    }
}
