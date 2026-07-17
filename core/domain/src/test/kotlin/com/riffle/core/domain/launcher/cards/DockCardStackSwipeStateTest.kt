package com.riffle.core.domain.launcher.cards

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
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
    fun wrappedSwipeCyclesOnlyWhenTheHybridDockEnablesIt() {
        assertEquals(
            DockCardStackSwipeState(
                content = DockCardStackContent.APPS,
                direction = DockCardStackSwipeDirection.NEXT,
                outgoingCardIndex = 2,
                incomingCardIndex = 0,
                newBackCardIndex = 1,
            ),
            DockCardStackSwipeState.create(
                cardCount = 3,
                activeCardIndex = 2,
                direction = DockCardStackSwipeDirection.NEXT,
                content = DockCardStackContent.APPS,
                wrapAround = true,
            ),
        )
    }

    @Test
    fun wrappedSwipeDoesNotTransitionASingleCardStack() {
        assertNull(
            DockCardStackSwipeState.create(
                cardCount = 1,
                activeCardIndex = 0,
                direction = DockCardStackSwipeDirection.NEXT,
                content = DockCardStackContent.APPS,
                wrapAround = true,
            ),
        )
    }

    @Test
    fun hybridFocusSurvivesDockReorderByAppIdentity() {
        val mail = app("mail")
        val chat = app("chat")
        val focus = HybridDockFocus(appIdentity = mail, notificationKey = LauncherNotificationKey("mail:1"))

        assertEquals(
            focus,
            reconcileHybridDockFocus(
                focus = focus,
                eligibleAppIdentities = listOf(chat, mail),
                notificationKeysByApp = mapOf(mail to listOf(LauncherNotificationKey("mail:1"))),
            ),
        )
    }

    @Test
    fun hybridFocusUsesNearestRemainingAppAndNotificationWhenContentDisappears() {
        val calendar = app("calendar")
        val chat = app("chat")
        val mail = app("mail")

        assertEquals(
            HybridDockFocus(appIdentity = mail, notificationKey = LauncherNotificationKey("mail:2")),
            reconcileHybridDockFocus(
                focus = HybridDockFocus(appIdentity = chat, notificationKey = LauncherNotificationKey("chat:1")),
                eligibleAppIdentities = listOf(calendar, mail),
                notificationKeysByApp =
                    mapOf(
                        mail to listOf(LauncherNotificationKey("mail:1"), LauncherNotificationKey("mail:2")),
                    ),
                appFallbackIndex = 1,
                notificationFallbackIndex = 1,
            ),
        )
    }

    @Test
    fun hybridFocusKeepsTheAppCardWhenItHasNoNotifications() {
        val mail = app("mail")

        assertEquals(
            HybridDockFocus(appIdentity = mail),
            reconcileHybridDockFocus(
                focus = HybridDockFocus(appIdentity = mail, notificationKey = LauncherNotificationKey("mail:1")),
                eligibleAppIdentities = listOf(mail),
                notificationKeysByApp = emptyMap(),
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

    private fun app(name: String): AppIdentity =
        AppIdentity(
            packageName = AppPackageName("com.riffle.$name"),
            activityName = AppActivityName(".$name"),
            profile = AppProfile.personal(),
        )
}
