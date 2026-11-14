package com.riffle.app.screenshots

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import com.riffle.app.launcher.DockFanSource
import com.riffle.app.launcher.LocalDockFan
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamSurface
import com.riffle.core.domain.launcher.dock.DockFan
import com.riffle.core.domain.launcher.dock.FanFrame
import com.riffle.core.domain.launcher.dockpull.DockPullDirection
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import com.riffle.core.domain.launcher.stream.StreamLayoutReducer
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.stream.StreamView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the Stream asks of the Dock's fan (ADR 0003, Amendment 3, Motion): the first cards of the page in front come out
 * of the Dock in sequence, front card first, and a page that is not a stack (a List, an empty or state page) rises as
 * one piece with the front card's timing. A source that records which cards were drawn from it stands in for the Dock.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamFanTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** A fan held at [p] that remembers which cards (by rank) drew from it. */
    private class RecordingFan(var p: Float) : DockFanSource {
        val ranks = mutableSetOf<Int>()
        var reduced = false

        override fun frame(index: Int): FanFrame {
            ranks += index
            return DockFan.cardFrame(p, index, reduced)
        }

        override val pull: DockPullDirection = DockPullDirection.UP
    }

    private fun show(
        fan: DockFanSource?,
        layout: StreamLayout = StreamLayout.DEFAULT,
        results: Map<StreamSection, StreamSectionResult> =
            StreamSection.layoutSections.associateWith { StreamFixtures.ready(it) },
        initialPageKey: String? = null,
    ) {
        composeRule.setContent {
            ScreenshotBackdrop {
                CompositionLocalProvider(LocalDockFan provides fan) {
                    StreamSurface(
                        layout = layout,
                        results = results,
                        env = StreamFixtures.environment(),
                        callbacks = StreamCallbacks(),
                        contentPadding = PaddingValues(),
                        initialPageKey = initialPageKey,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun theCardsOfAStackFanInSequenceFromTheFront() {
        val fan = RecordingFan(p = 0.6f)
        show(fan, initialPageKey = StreamPagePlanner.keyOf(StreamSection.NOW))
        assertEquals("the focused card and the two behind it fan, in rank order", setOf(0, 1, 2), fan.ranks)
    }

    @Test
    fun aListPageRisesAsOnePieceWithTheFrontCardsTiming() {
        val fan = RecordingFan(p = 0.6f)
        val asList = StreamLayoutAction.SetView(StreamSection.NOW, StreamView.LIST)
        val list = StreamLayoutReducer.reduce(StreamLayout.DEFAULT, asList)
        show(fan, layout = list, initialPageKey = StreamPagePlanner.keyOf(StreamSection.NOW))
        assertEquals(setOf(0), fan.ranks)
    }

    @Test
    fun anEmptyOrStatePageRisesAsOnePieceToo() {
        val fan = RecordingFan(p = 0.6f)
        show(fan, results = emptyMap())
        assertEquals(setOf(0), fan.ranks)
    }
}
