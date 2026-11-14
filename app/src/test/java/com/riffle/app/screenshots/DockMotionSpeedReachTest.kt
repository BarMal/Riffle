package com.riffle.app.screenshots

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import com.riffle.app.launcher.LocalDockMotionTuning
import com.riffle.app.launcher.rememberEasedFraction
import com.riffle.app.launcher.stream.SwipeDismissState
import com.riffle.core.domain.launcher.dock.DockMotionTuning
import com.riffle.core.domain.launcher.settings.DockMotionSettings
import com.riffle.core.domain.launcher.stream.SwipeDismiss
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings > Motion > "Opening speed" reaches the motion added after it was built: the ease of Home's grid under the
 * stack (the "apps behind the cards" fade) and a card flying off when it is swiped away. Each is run on the test clock
 * at the fastest, the default and the slowest speed and must be further along the faster the setting is.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockMotionSpeedReachTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun tuningAt(speedMillis: Int): DockMotionTuning = DockMotionSettings(speedMillis = speedMillis).tuning()

    /** How far the grid's fade has come [millis] after the Stream opens, at [speedMillis]. */
    private fun fadeAfter(
        speedMillis: Int,
        millis: Long,
    ): Float {
        var target by mutableStateOf(false)
        lateinit var fraction: Animatable<Float, AnimationVector1D>
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalDockMotionTuning provides tuningAt(speedMillis)) {
                fraction = rememberEasedFraction(target, reducedMotion = false)
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnUiThread {
            target = true
            Snapshot.sendApplyNotifications()
        }
        // One frame to recompose and start the animation, one more for its first step.
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(millis)
        return fraction.value
    }

    @Test
    fun theGridFadeComesInFasterTheFasterTheOpeningSpeed() {
        val fast = fadeAfter(DockMotionSettings.MIN_SPEED_MILLIS, EASE_MILLIS)

        assertTrue("fast ($fast) is nearly there", fast > 0.95f)
    }

    @Test
    fun theGridFadeAtTheDefaultSpeedIsBetweenTheTwo() {
        val default = fadeAfter(DockMotionSettings().speedMillis, EASE_MILLIS)

        assertTrue("default ($default) is on its way", default in 0.6f..0.95f)
    }

    @Test
    fun theGridFadeComesInSlowerAtTheSlowestSpeed() {
        val slow = fadeAfter(DockMotionSettings.MAX_SPEED_MILLIS, EASE_MILLIS)

        assertTrue("slow ($slow) is still early", slow < 0.6f)
    }

    /** Where a card swiped to dismiss is [millis] after the finger lifts, at [speedMillis], in px of a 300 px card. */
    private fun flownAfter(
        speedMillis: Int,
        millis: Long,
    ): Float {
        lateinit var state: SwipeDismissState
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val scope = rememberCoroutineScope()
            state = SwipeDismissState(scope, { false }, { tuningAt(speedMillis) }, 1f) {}
        }
        composeRule.mainClock.advanceTimeByFrame()
        state.widthPx = CARD_WIDTH
        state.draggable.dispatchRawDelta(CARD_WIDTH * 0.7f)
        state.release(velocityPxPerSecond = 0f)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(millis)
        return state.offsetPx
    }

    @Test
    fun aSwipedCardLeavesFasterTheFasterTheOpeningSpeed() {
        val gone = CARD_WIDTH * SwipeDismiss.FLY_OFF_WIDTHS
        val fast = flownAfter(DockMotionSettings.MIN_SPEED_MILLIS, FLY_MILLIS)

        assertEquals("fast has left", gone, fast, 1f)
    }

    @Test
    fun aSwipedCardLeavesSlowerAtTheSlowestSpeed() {
        val gone = CARD_WIDTH * SwipeDismiss.FLY_OFF_WIDTHS
        val slow = flownAfter(DockMotionSettings.MAX_SPEED_MILLIS, FLY_MILLIS)

        assertTrue("slow ($slow) is still on its way to $gone", slow < gone - 20f)
    }

    private companion object {
        const val EASE_MILLIS = 150L
        const val FLY_MILLIS = 100L
        const val CARD_WIDTH = 300f
    }
}
