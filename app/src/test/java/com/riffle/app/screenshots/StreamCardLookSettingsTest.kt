package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.GlassCardLook
import com.riffle.app.launcher.GlassCardLookKey
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamCard
import com.riffle.core.domain.launcher.designsystem.CardTint
import com.riffle.core.domain.launcher.settings.StreamSettings
import com.riffle.core.domain.launcher.stream.StreamAppRef
import com.riffle.core.domain.launcher.stream.StreamContentId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings > Stream > Cards, "Card colour strength" and "Glow on the top edge": a real Stream card is drawn at several
 * settings and the look it reports (the fill it draws and whether the glow is lit) is compared, so each setting is
 * proved to reach what the card looks like and not only the store. The pixel maths of the fill is `CardTintTest`'s,
 * the glow's drawing is the edge light's own (`GlassEdgeOutlineTest`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamCardLookSettingsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var settings by mutableStateOf(StreamSettings())

    private fun showCard() {
        val app = ScreenshotFixtures.installedApps.first()
        composeRule.setContent {
            ScreenshotBackdrop {
                Box(Modifier.padding(24.dp)) {
                    StreamCard(
                        content =
                            StreamFixtures.chatNotification.copy(
                                id = StreamContentId("look"),
                                app = StreamAppRef(app.identity.packageName.value, "personal"),
                                appLabel = app.label,
                                title = "Sam",
                                person = null,
                                picture = null,
                                image = null,
                                thread = emptyList(),
                            ),
                        env = StreamFixtures.environment().copy(cardSettings = settings),
                        callbacks = StreamCallbacks(),
                        groupKey = null,
                        modifier = Modifier.fillMaxWidth().height(112.dp).testTag(CARD_TAG),
                    )
                }
            }
        }
    }

    /** The look of the card the settings (changed live, as the page changes them) now give. */
    private fun look(change: StreamSettings): GlassCardLook {
        settings = change
        composeRule.waitForIdle()
        return composeRule.onNodeWithTag(CARD_TAG).fetchSemanticsNode().config[GlassCardLookKey]
    }

    @Test
    fun theColourStrengthChangesTheCardsFillAndNothingElse() {
        showCard()

        val none = look(StreamSettings(cardTintPercent = CardTint.MIN_STRENGTH_PERCENT))
        val strong = look(StreamSettings(cardTintPercent = CardTint.MAX_STRENGTH_PERCENT))

        assertNotEquals("0% and 40% fill differently", none.fillArgb, strong.fillArgb)
        assertEquals(none.topGlow, strong.topGlow)
    }

    @Test
    fun theGlowIsLitOnlyWhenTheSwitchIsOnAndDoesNotChangeTheFill() {
        showCard()

        val off = look(StreamSettings(cardTopGlow = false))
        val on = look(StreamSettings(cardTopGlow = true))

        assertFalse(off.topGlow)
        assertTrue(on.topGlow)
        assertEquals(off.fillArgb, on.fillArgb)
    }

    private companion object {
        const val CARD_TAG = "look-card"
    }
}
