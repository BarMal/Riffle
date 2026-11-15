package com.riffle.app.launcher.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublishOwnerTest {
    @Test
    fun aStackThatPublishedNothingCannotEraseAnotherStacksCard() {
        val owner = PublishOwner()
        assertTrue(owner.accepts("page-a", hasValue = true))
        // The neighbour page's stack publishes "nothing" on every composition: ignored.
        assertFalse(owner.accepts("page-b", hasValue = false))
        assertFalse(owner.accepts("page-b", hasValue = false))
        // The owner takes its own back; then anyone's nothing is a plain no-op and a new owner may publish.
        assertTrue(owner.accepts("page-a", hasValue = false))
        assertFalse(owner.accepts("page-b", hasValue = false))
        assertTrue(owner.accepts("page-b", hasValue = true))
        assertEquals(false, owner.accepts("page-a", hasValue = false))
    }

    @Test
    fun aResetForgetsTheOwner() {
        val owner = PublishOwner()
        owner.accepts("a", true)
        owner.reset()
        assertFalse(owner.accepts("a", false))
    }
}
