package com.riffle.app.launcher.stream

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which pages leave the view button's corner clear: a page that says nothing does, a page that reaches it does not. */
class StreamViewButtonRoomTest {
    @Test
    fun aPageThatSaidNothingLeavesTheCornerClear() {
        val room = StreamViewButtonRoom()
        assertTrue(room.isClear("now"))
        assertTrue(room.isClear(null))
    }

    @Test
    fun aPageThatReachesTheCornerIsNotClearUntilItStops() {
        val room = StreamViewButtonRoom()
        room.report("now", clear = false)
        assertFalse(room.isClear("now"))
        assertTrue("another page is its own", room.isClear("today"))

        room.report("now", clear = true)
        assertTrue(room.isClear("now"))
    }

    @Test
    fun reportingTheSameThingTwiceChangesNothing() {
        val room = StreamViewButtonRoom()
        repeat(2) { room.report("now", clear = false) }
        assertFalse(room.isClear("now"))
        repeat(2) { room.report("now", clear = true) }
        assertTrue(room.isClear("now"))
    }
}
