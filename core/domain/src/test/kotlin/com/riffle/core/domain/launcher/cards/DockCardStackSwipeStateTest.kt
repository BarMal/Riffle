package com.riffle.core.domain.launcher.cards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DockCardStackSwipeStateTest {
    @Test
    fun nextSwipeAssignsOutgoingIncomingAndBackCardRoles() {
        assertEquals(
            DockCardStackSwipeState(
                content = DockCardStackContent.APPS,
                direction = DockCardStackSwipeDirection.NEXT,
                outgoingCardIndex = 1,
                incomingCardIndex = 2,
                newBackCardIndex = 3,
            ),
            DockCardStackSwipeState.create(
                cardCount = 4,
                activeCardIndex = 1,
                direction = DockCardStackSwipeDirection.NEXT,
                content = DockCardStackContent.APPS,
            ),
        )
    }

    @Test
    fun previousSwipeKeepsNotificationNavigationSeparate() {
        val state =
            DockCardStackSwipeState.create(
                cardCount = 4,
                activeCardIndex = 2,
                direction = DockCardStackSwipeDirection.PREVIOUS,
                content = DockCardStackContent.NOTIFICATIONS,
            )

        assertEquals(DockCardStackContent.NOTIFICATIONS, state?.content)
        assertEquals(2, state?.outgoingCardIndex)
        assertEquals(1, state?.incomingCardIndex)
        assertEquals(0, state?.newBackCardIndex)
    }

    @Test
    fun returnsNoStateWhenNoAdjacentCardExists() {
        assertNull(
            DockCardStackSwipeState.create(
                cardCount = 1,
                activeCardIndex = 0,
                direction = DockCardStackSwipeDirection.NEXT,
                content = DockCardStackContent.APPS,
            ),
        )
        assertNull(
            DockCardStackSwipeState.create(
                cardCount = 3,
                activeCardIndex = 0,
                direction = DockCardStackSwipeDirection.PREVIOUS,
                content = DockCardStackContent.NOTIFICATIONS,
            ),
        )
    }

    @Test
    fun rejectsInvalidStackAndOverlappingRoles() {
        assertFailsWith<IllegalArgumentException> {
            DockCardStackSwipeState.create(
                cardCount = -1,
                activeCardIndex = 0,
                direction = DockCardStackSwipeDirection.NEXT,
                content = DockCardStackContent.APPS,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DockCardStackSwipeState(
                content = DockCardStackContent.APPS,
                direction = DockCardStackSwipeDirection.NEXT,
                outgoingCardIndex = 1,
                incomingCardIndex = 1,
                newBackCardIndex = null,
            )
        }
    }
}
