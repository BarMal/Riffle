package com.riffle.app.screenshots

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.riffle.app.launcher.stream.STREAM_OPEN_CARD_TEST_TAG
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import org.junit.Test
import org.robolectric.annotation.Config

/** The open card as a screen shows it: a long card with neighbours above and below, and the cue mid-drag. */
internal abstract class OpenCardPolishShots(
    deviceClass: HomeLayoutDeviceClass,
    dock: DockPosition?,
) : OpenCardPolishHarness(deviceClass, dock) {
    private fun step(label: String) {
        val action =
            composeRule.onAllNodes(
                hasAnyAncestor(hasTestTag(STREAM_OPEN_CARD_TEST_TAG)) or hasTestTag(STREAM_OPEN_CARD_TEST_TAG),
            )
                .fetchSemanticsNodes().firstNotNullOf { node ->
                    node.config.getOrNull(SemanticsActions.CustomActions)?.firstOrNull { it.label == label }
                }
        composeRule.runOnUiThread { action.action() }
        composeRule.waitForIdle()
    }

    /** The second card of four is open (a long one): two older cards stand above it and the front card below it. */
    protected fun openTheMiddleCard() {
        show(more = 1, sam = "Are you around later?", notes = longText)
        tapFront()
        composeRule.waitForIdle()
        step("Next card")
    }

    protected fun shootOpen() {
        openTheMiddleCard()
        composeRule.captureScreen()
    }

    protected fun shootCue(dyDp: Float) {
        openTheMiddleCard()
        composeRule.mainClock.autoAdvance = false
        val card = checkNotNull(boundsOf(STREAM_OPEN_CARD_TEST_TAG))
        val start = Offset(card.center.x, card.top + card.height * 0.5f)
        composeRule.onRoot().performTouchInput {
            down(start)
            repeat(6) { moveBy(Offset(0f, dyDp * density / 6f), delayMillis = 16) }
        }
        repeat(4) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.captureScreen()
        composeRule.onRoot().performTouchInput { up() }
    }
}

@Config(sdk = [ScreenshotDevices.SDK], qualifiers = OWNER_UNFOLDED)
internal class OpenCardPolishUnfoldedShots : OpenCardPolishShots(HomeLayoutDeviceClass.FOLDABLE, DockPosition.RIGHT) {
    @Test
    fun aLongCardWithNeighboursAboveAndBelow() = shootOpen()

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun aLongCardWithNeighboursAboveAndBelowDark() = shootOpen()

    @Test
    fun theCueAtTheTopOfTheBodyNamesTheOlderCard() = shootCue(dyDp = 36f)

    @Test
    fun theCueAtTheBottomOfTheBodyNamesTheNewerCard() {
        openTheMiddleCard()
        scrollBodyToEnd()
        composeRule.mainClock.autoAdvance = false
        val card = checkNotNull(boundsOf(STREAM_OPEN_CARD_TEST_TAG))
        val start = Offset(card.center.x, card.top + card.height * 0.5f)
        composeRule.onRoot().performTouchInput {
            down(start)
            repeat(6) { moveBy(Offset(0f, -36f * density / 6f), delayMillis = 16) }
        }
        repeat(4) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.captureScreen()
        composeRule.onRoot().performTouchInput { up() }
    }

    private fun scrollBodyToEnd() {
        val node =
            composeRule.onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
                    hasAnyAncestor(hasTestTag(STREAM_OPEN_CARD_TEST_TAG)),
            ).fetchSemanticsNodes().first()
        val range = node.config[SemanticsProperties.VerticalScrollAxisRange]
        composeRule.runOnUiThread {
            node.config[SemanticsActions.ScrollBy].action?.invoke(0f, range.maxValue() - range.value())
        }
        composeRule.waitForIdle()
    }
}

@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
internal class OpenCardPolishFoldedShots : OpenCardPolishShots(HomeLayoutDeviceClass.PHONE, null) {
    @Test
    fun aLongCardWithNeighboursAboveAndBelow() = shootOpen()

    @Test
    fun theCueAtTheTopOfTheBodyNamesTheOlderCard() = shootCue(dyDp = 36f)
}
