package com.riffle.app.launcher.stream

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.riffle.core.domain.launcher.dock.DockMotionTuning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cue at the edge of an open card's scrolling body (open card polish): the drag past the body's edge fills it
 * toward the hand-off threshold, only toward a card that exists, and it is gone when the finger lifts. And the bodies
 * of
 * two cards swapping are never both drawn: the one leaving has gone before the one arriving shows.
 */
class OpenCardHandoffCueTest {
    private val density = 2f

    private fun connection(
        cue: HandoffCueState,
        hasOlder: Boolean = true,
        hasNewer: Boolean = true,
    ): OpenCardHandoffConnection {
        val mover =
            StackMover(
                state = StreamStackState(),
                scope = CoroutineScope(Dispatchers.Unconfined),
                density = density,
                slopDp = 0f,
                count = { 3 },
                tuning = { DockMotionTuning() },
                reduced = { true },
                tick = {},
            )
        val body = ScrollState(0)
        return OpenCardHandoffConnection(mover, density, { body }, cue) { older -> if (older) hasOlder else hasNewer }
    }

    private fun OpenCardHandoffConnection.drag(dyDp: Float) {
        onPostScroll(Offset.Zero, Offset(0f, dyDp * density), NestedScrollSource.UserInput)
    }

    @Test
    fun theCueFillsAsTheFingerGoesPastTheEdgeTowardTheThreshold() {
        val cue = HandoffCueState()
        val c = connection(cue)
        c.drag(14f)
        assertEquals(0.25f, cue.fraction, 1e-4f)
        c.drag(14f)
        assertEquals(0.5f, cue.fraction, 1e-4f)
        c.drag(100f)
        assertEquals(1f, cue.fraction, 1e-4f)
    }

    @Test
    fun draggingTheOtherWayHasTheCueTowardTheCardInFront() {
        val cue = HandoffCueState()
        connection(cue).drag(-28f)
        assertEquals(-0.5f, cue.fraction, 1e-4f)
    }

    @Test
    fun thereIsNoCueTowardACardThatIsNotThere() {
        val none = HandoffCueState()
        connection(none, hasOlder = false).drag(40f)
        assertEquals(0f, none.fraction, 0f)
        val noNewer = HandoffCueState()
        connection(noNewer, hasNewer = false).drag(-40f)
        assertEquals(0f, noNewer.fraction, 0f)
    }

    @Test
    fun aFingerThatComesBackTakesTheCueBackAndLiftingEndsIt() =
        runBlocking {
            val cue = HandoffCueState()
            val c = connection(cue)
            c.drag(42f)
            assertEquals(0.75f, cue.fraction, 1e-4f)
            c.onPreScroll(Offset(0f, -14f * density), NestedScrollSource.UserInput)
            assertEquals(0.5f, cue.fraction, 1e-4f)
            c.onPreFling(Velocity.Zero)
            assertEquals(0f, cue.fraction, 0f)
        }

    @Test
    fun theBodyOfTheCardLeavingIsGoneBeforeTheBodyOfTheCardArrivingBeginsToShow() {
        // Openness of the arriving card is w, of the leaving one 1 - w: at no moment are both bodies drawn.
        for (i in 0..100) {
            val w = i / 100f
            assertEquals("both bodies at $w", 0f, minOf(bodyAlphaAt(w), bodyAlphaAt(1f - w)), 0f)
            assertTrue(bodyAlphaAt(w) + bodyAlphaAt(1f - w) <= 1f + 1e-6f)
        }
        assertEquals(1f, bodyAlphaAt(1f), 0f)
        assertEquals(0f, bodyAlphaAt(0f), 0f)
    }

    @Test
    fun theHeadersScaleBetweenAStripsSizeAndWhole() {
        assertEquals(HEADER_STRIP_SCALE, headerScaleAt(0f), 1e-6f)
        assertEquals(1f, headerScaleAt(1f), 1e-6f)
        assertTrue(headerScaleAt(0.5f) in HEADER_STRIP_SCALE..1f)
    }
}
