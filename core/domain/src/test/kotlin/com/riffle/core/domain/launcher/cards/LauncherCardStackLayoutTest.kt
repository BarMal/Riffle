package com.riffle.core.domain.launcher.cards

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.notifications.AppNotificationGroupKey
import kotlin.test.Test
import kotlin.test.assertEquals

class LauncherCardStackLayoutTest {
    private val policy = CardStackLayoutPolicy()

    @Test
    fun handlesEmptyAndSingleCardStacks() {
        assertEquals(emptyList(), policy.entries(emptyList()))

        val card = appCard("mail")
        val entries = policy.entries(listOf(card))

        assertEquals(listOf(card), entries.map { it.card })
        assertEquals(listOf(0), entries.map { it.layout.depth })
    }

    @Test
    fun preservesMixedCardsAndFocusesTheRequestedCard() {
        val app = appCard("mail")
        val notification = notificationCard("chat")
        val entries = policy.entries(listOf(app, notification), activeCardId = notification.id)

        assertEquals(listOf(app, notification), entries.map { it.card })
        assertEquals(listOf(1, 0), entries.map { it.layout.depth })
        assertEquals(notification, entries.last().card)
    }

    private fun appCard(id: String) =
        LauncherCard(
            id = LauncherCardId(id),
            sourceRef =
                LauncherCardSourceRef.App(
                    AppIdentity(
                        packageName = AppPackageName("com.riffle.$id"),
                        activityName = AppActivityName(".MainActivity"),
                        profile = AppProfile.personal(),
                    ),
                ),
        )

    private fun notificationCard(id: String) =
        LauncherCard(
            id = LauncherCardId(id),
            sourceRef =
                LauncherCardSourceRef.AppNotificationGroup(
                    AppNotificationGroupKey(AppPackageName("com.riffle.$id"), AppProfile.personal().id),
                ),
        )
}
