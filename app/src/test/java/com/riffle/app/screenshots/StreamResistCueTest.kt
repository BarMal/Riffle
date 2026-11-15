package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.riffle.app.launcher.LauncherHapticEvent
import com.riffle.app.launcher.LauncherHaptics
import com.riffle.app.launcher.LocalLauncherHaptics
import com.riffle.app.launcher.stream.ResistCueAlphaKey
import com.riffle.app.launcher.stream.ResistCueLabelKey
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamSurface
import com.riffle.app.launcher.stream.streamStackCardTag
import com.riffle.core.domain.launcher.stream.StreamGroup
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The cue a card that resists a sideways drag draws (it says why): a card that is not a notification, here a recent-app
 * card, says "Can't dismiss", a strip says nothing, and the cue goes when the card settles.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamResistCueTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val dp get() = composeRule.density.density
    private val group = "recent-group"
    private val recents = StreamFixtures.recents.take(3)

    private fun show() {
        val results: Map<StreamSection, StreamSectionResult> =
            StreamSection.layoutSections.associateWith<StreamSection, StreamSectionResult> {
                StreamSectionResult.Empty
            } + (StreamSection.NOTIFICATIONS to StreamSectionResult.Ready(listOf(StreamGroup(group, "Apps", recents))))
        val haptics =
            object : LauncherHaptics {
                override fun longPress() = Unit

                override fun perform(event: LauncherHapticEvent) = Unit
            }
        composeRule.setContent {
            CompositionLocalProvider(LocalLauncherHaptics provides haptics) {
                ScreenshotBackdrop {
                    Box(Modifier.fillMaxSize()) {
                        StreamSurface(
                            layout = StreamLayout.DEFAULT,
                            results = results,
                            env = StreamFixtures.environment(reducedMotion = true),
                            callbacks = StreamCallbacks(),
                            initialPageKey = StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, group),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun cardNode(index: Int) =
        composeRule.onNodeWithTag(streamStackCardTag(recents[index].id.value), useUnmergedTree = true)

    private fun drag(index: Int) {
        cardNode(index).performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(30f * dp, 0f), delayMillis = 16) }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun aRecentAppCardInFrontSaysItCannotBeDismissedWhileDraggedAndNothingAfterSettling() {
        show()
        drag(0)
        val config = cardNode(0).fetchSemanticsNode().config
        assertEquals("Can't dismiss", config.getOrNull(ResistCueLabelKey))
        assertTrue((config.getOrNull(ResistCueAlphaKey) ?: 0f) > 0.9f)
        cardNode(0).performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals("", cardNode(0).fetchSemanticsNode().config.getOrNull(ResistCueLabelKey))
    }

    @Test
    fun aStripBehindItSaysNothing() {
        show()
        drag(1)
        val node =
            composeRule.onAllNodes(
                SemanticsMatcher("the strip") { n ->
                    n.config.getOrNull(SemanticsProperties.TestTag) == streamStackCardTag(recents[1].id.value)
                },
            ).fetchSemanticsNodes().first()
        assertEquals(null, node.config.getOrNull(ResistCueAlphaKey))
        cardNode(1).performTouchInput { up() }
    }
}
