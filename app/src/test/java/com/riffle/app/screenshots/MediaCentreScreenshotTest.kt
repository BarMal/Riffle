package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamSurface
import com.riffle.core.domain.launcher.stream.StreamGroup
import com.riffle.core.domain.launcher.stream.StreamGroupKeys
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The media centre (ADR 0003, Amendment 4), folded: Now with one playing session and two recent players, with only
 * recent players, and empty; and Today with the next event and the alarm behind it. Light and dark.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class MediaCentreScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun nowWithAPlayingSessionAndTwoRecentPlayersLight() = now(MediaCentreFixtures.playingAndTwoRecent)

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun nowWithAPlayingSessionAndTwoRecentPlayersDark() = now(MediaCentreFixtures.playingAndTwoRecent)

    @Test
    fun nowWithOnlyRecentPlayersLight() = now(MediaCentreFixtures.onlyRecent)

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun nowWithOnlyRecentPlayersDark() = now(MediaCentreFixtures.onlyRecent)

    @Test
    fun nowEmptyLight() = now(StreamSectionResult.Empty)

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun nowEmptyDark() = now(StreamSectionResult.Empty)

    @Test
    fun todayWithTheNextEventAndTheAlarmLight() = today()

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun todayWithTheNextEventAndTheAlarmDark() = today()

    private fun now(result: StreamSectionResult) = renderMediaCentre(composeRule, StreamSection.NOW, result, null)

    private fun today() =
        renderMediaCentre(composeRule, StreamSection.TODAY, StreamFixtures.ready(StreamSection.TODAY), null)
}

/** The same four views on an unfolded posture: the Stream's column beside the Dock, as wide as it is there. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
class MediaCentreUnfoldedScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun nowWithAPlayingSessionAndTwoRecentPlayersUnfoldedLight() =
        render(StreamSection.NOW, MediaCentreFixtures.playingAndTwoRecent)

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun nowWithAPlayingSessionAndTwoRecentPlayersUnfoldedDark() =
        render(StreamSection.NOW, MediaCentreFixtures.playingAndTwoRecent)

    @Test
    fun nowWithOnlyRecentPlayersUnfoldedLight() = render(StreamSection.NOW, MediaCentreFixtures.onlyRecent)

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun nowWithOnlyRecentPlayersUnfoldedDark() = render(StreamSection.NOW, MediaCentreFixtures.onlyRecent)

    @Test
    fun nowEmptyUnfoldedLight() = render(StreamSection.NOW, StreamSectionResult.Empty)

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun nowEmptyUnfoldedDark() = render(StreamSection.NOW, StreamSectionResult.Empty)

    @Test
    fun todayWithTheNextEventAndTheAlarmUnfoldedLight() =
        render(StreamSection.TODAY, StreamFixtures.ready(StreamSection.TODAY))

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun todayWithTheNextEventAndTheAlarmUnfoldedDark() =
        render(StreamSection.TODAY, StreamFixtures.ready(StreamSection.TODAY))

    private fun render(
        section: StreamSection,
        result: StreamSectionResult,
    ) = renderMediaCentre(composeRule, section, result, paneWidth = UNFOLDED_COLUMN_WIDTH)

    private companion object {
        val UNFOLDED_COLUMN_WIDTH = 420.dp
    }
}

internal object MediaCentreFixtures {
    private fun now(vararg items: com.riffle.core.domain.launcher.stream.StreamContent) =
        StreamSectionResult.Ready(listOf(StreamGroup(StreamGroupKeys.NOW, null, items.toList())))

    val playingAndTwoRecent =
        now(StreamFixtures.media, StreamFixtures.recentPodcasts, StreamFixtures.recentRadio)

    val onlyRecent = now(StreamFixtures.recentPodcasts, StreamFixtures.recentRadio)
}

private fun renderMediaCentre(
    composeRule: androidx.compose.ui.test.junit4.ComposeContentTestRule,
    section: StreamSection,
    result: StreamSectionResult,
    paneWidth: Dp?,
) {
    val results = StreamSection.layoutSections.associateWith { StreamFixtures.ready(it) } + (section to result)
    composeRule.setContent {
        ScreenshotBackdrop {
            val size = if (paneWidth != null) Modifier.width(paneWidth).fillMaxSize() else Modifier.fillMaxSize()
            Box(modifier = size) {
                StreamSurface(
                    layout = StreamLayout.DEFAULT,
                    results = results,
                    env = StreamFixtures.environment(),
                    callbacks = StreamCallbacks(),
                    contentPadding = PaddingValues(top = 48.dp, bottom = 96.dp),
                    initialPageKey = com.riffle.core.domain.launcher.stream.StreamPagePlanner.keyOf(section),
                )
            }
        }
    }
    composeRule.captureScreen()
}
