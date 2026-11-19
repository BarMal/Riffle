package com.riffle.core.domain.launcher.stream

import kotlin.test.Test
import kotlin.test.assertEquals

/** A stack keeps the person on the same card when its cards change under them (ADR 0003, Amendment 18). */
class StackFollowTest {
    private fun cards(vararg ids: String) = ids.map { StackCardRef(it, goneMeansPlayed = it.startsWith("q")) }

    private val before = cards("player", "q1", "q2", "q3")

    @Test
    fun `the same card is followed when the queue moves up by one`() {
        // The track advanced: q1 is playing now (the player's card), the others shift toward the front.
        assertEquals(1, StackFollow.indexAfterChange(before, 2, listOf("player", "q2", "q3", "q4")))
        assertEquals(2, StackFollow.indexAfterChange(before, 3, listOf("player", "q2", "q3", "q4")))
    }

    @Test
    fun `the card that became the playing one lands on the front`() {
        assertEquals(0, StackFollow.indexAfterChange(before, 1, listOf("player", "q2", "q3", "q4")))
    }

    @Test
    fun `two tracks advancing at once follow the same card or land on the front`() {
        val after = listOf("player", "q3", "q4")
        assertEquals(1, StackFollow.indexAfterChange(before, 3, after))
        assertEquals(0, StackFollow.indexAfterChange(before, 2, after))
        assertEquals(0, StackFollow.indexAfterChange(before, 1, after))
    }

    @Test
    fun `the front stays the front and an unchanged stack changes nothing`() {
        assertEquals(0, StackFollow.indexAfterChange(before, 0, listOf("player", "q2", "q3", "q4")))
        (0..3).forEach { assertEquals(it, StackFollow.indexAfterChange(before, it, before.map(StackCardRef::id))) }
    }

    @Test
    fun `a card that moved earlier or later is followed`() {
        assertEquals(3, StackFollow.indexAfterChange(before, 1, listOf("player", "q2", "q3", "q1")))
        assertEquals(1, StackFollow.indexAfterChange(before, 3, listOf("player", "q3", "q1", "q2")))
    }

    @Test
    fun `another card that went away leaves the next one in its place`() {
        val notes = listOf("a", "b", "c", "d").map { StackCardRef(it) }
        assertEquals(1, StackFollow.indexAfterChange(notes, 1, listOf("a", "c", "d")))
        assertEquals(2, StackFollow.indexAfterChange(notes, 3, listOf("a", "b", "c")))
        assertEquals(0, StackFollow.indexAfterChange(notes, 2, listOf("z")))
    }

    @Test
    fun `empty stacks and out of range indexes are held`() {
        assertEquals(0, StackFollow.indexAfterChange(before, 2, emptyList()))
        assertEquals(0, StackFollow.indexAfterChange(emptyList(), 2, listOf("a")))
        // 99 is held to the last card (q3), -5 to the first (player).
        assertEquals(1, StackFollow.indexAfterChange(before, 99, listOf("player", "q3")))
        assertEquals(1, StackFollow.indexAfterChange(before, -5, listOf("x", "player")))
    }
}
