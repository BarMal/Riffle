package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedPageSurfaceTest {
    @Test
    fun unavailableNotificationAccessExplainsHowToEnableCards() {
        assertEquals(
            GeneratedNotificationCardsPageState.Message(
                "Notification access needed",
                "Allow notification access to show your notification cards.",
            ),
            generatedNotificationCardsPageState(
                groups = emptyList(),
                notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED,
                apps = emptyList(),
            ),
        )
    }

    @Test
    fun grantedAccessWithoutNotificationsShowsEmptyState() {
        assertEquals(
            GeneratedNotificationCardsPageState.Message("No notifications", "New notifications will appear here."),
            generatedNotificationCardsPageState(
                groups = emptyList(),
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
                apps = emptyList(),
            ),
        )
    }

    @Test
    fun notificationCardKeysKeepAmbiguousConcatenatedValuesDistinct() {
        val first = notificationGroup(packageName = "com.example.app1", profileId = "23")
        val second = notificationGroup(packageName = "com.example.app12", profileId = "3")

        assertEquals(
            first.packageName.value + first.profileId.value,
            second.packageName.value + second.profileId.value,
        )
        assertNotEquals(generatedNotificationCardKey(first), generatedNotificationCardKey(second))
    }

    @Test
    fun generatedCardClearDescriptionNamesTheAppAndClearableNotifications() {
        val group =
            notificationGroup(packageName = "com.example.chat", profileId = "personal").copy(
                notifications =
                    listOf(
                        notification(packageName = "com.example.chat", key = "chat:1"),
                        notification(packageName = "com.example.chat", key = "chat:2"),
                    ),
            )

        assertEquals(
            "Clear Chat notifications",
            generatedNotificationCardClearContentDescription(DockNotificationCardState(app = null, group = group)),
        )
    }

    @Test
    fun appBackedGeneratedCardProvidesLaunchActionAndTapToOpenDescription() {
        val app = installedApp(label = "Chat", packageName = "com.example.chat")
        val card =
            DockNotificationCardState(
                app = app,
                group = notificationGroup(packageName = "com.example.chat", profileId = "personal"),
            )

        assertEquals(
            LauncherShellAction.LaunchApp(app.identity),
            generatedNotificationCardLaunchAction(card),
        )
        assertTrue(generatedNotificationCardContentDescription(card).contains("Tap to open"))
    }

    @Test
    fun generatedCardsUseFocusedStackEntries() {
        val cards =
            listOf(
                DockNotificationCardState(null, notificationGroup("com.example.first", "personal")),
                DockNotificationCardState(null, notificationGroup("com.example.second", "personal")),
            )

        val entries = generatedNotificationCardStackEntries(cards, focusedCardIndex = 1)

        assertEquals(2, entries.size)
        assertEquals(1, entries.maxBy { entry -> entry.order }.cardIndex)
    }

    @Test
    fun artworkCacheCachesDecodeFailuresAndEvictsLeastRecentlyUsedArtwork() {
        var decodeCalls = 0
        val cache =
            TimeScapeArtworkCache<Int>(maxEntries = 2) {
                decodeCalls += 1
                if (it == "corrupt") null else it?.length
            }

        assertEquals(null, cache.getOrDecode("corrupt-card", "corrupt"))
        assertEquals(null, cache.getOrDecode("corrupt-card", "corrupt"))
        assertEquals(1, decodeCalls)

        assertEquals(1, cache.getOrDecode("a", "a"))
        assertEquals(2, cache.getOrDecode("bb", "bb"))
        assertEquals(1, cache.getOrDecode("a", "a"))
        assertEquals(3, cache.getOrDecode("ccc", "ccc"))
        assertEquals(2, cache.getOrDecode("bb", "bb"))

        assertEquals(5, decodeCalls)
        assertEquals(2, cache.sizeForTest())
    }

    @Test
    fun artworkSourceKeysSeparatePayloadsWithTheSameStringHashCode() {
        val firstPayload = "Aa"
        val secondPayload = "BB"
        assertEquals(firstPayload.hashCode(), secondPayload.hashCode())

        val first =
            DockNotificationCardState(
                app = null,
                group =
                    notificationGroup("com.example.artwork", "personal").copy(
                        notifications =
                            listOf(
                                notification(
                                    packageName = "com.example.artwork",
                                    key = "artwork",
                                    artwork = firstPayload,
                                ),
                            ),
                    ),
            )
        val second =
            first.copy(
                group =
                    first.group.copy(
                        notifications =
                            listOf(
                                notification(
                                    packageName = "com.example.artwork",
                                    key = "artwork",
                                    artwork = secondPayload,
                                ),
                            ),
                    ),
            )

        val revisions = TimeScapeArtworkRevisionStore()
        revisions.replace(listOf(first.group))
        val firstKey = generatedNotificationArtworkSourceKey(first, revisions)
        revisions.replace(listOf(second.group))
        val secondKey = generatedNotificationArtworkSourceKey(second, revisions)

        assertNotEquals(firstKey, secondKey)
    }

    @Test
    fun artworkRevisionsArePreparedForARefreshBurstBeforeCardRendering() {
        val groups =
            (1..100).map { index ->
                notificationGroup("com.example.burst$index", "personal").copy(
                    notifications =
                        listOf(
                            notification(
                                packageName = "com.example.burst$index",
                                key = "burst-$index",
                                artwork = "artwork-$index",
                            ),
                        ),
                )
            }
        val revisions = TimeScapeArtworkRevisionStore()

        revisions.replace(groups)

        val card = DockNotificationCardState(app = null, group = groups.last())
        assertNotEquals(null, generatedNotificationArtworkSourceKey(card, revisions))
    }

    private fun notificationGroup(
        packageName: String,
        profileId: String,
    ) = AppNotificationGroup(
        packageName = AppPackageName(packageName),
        profileId = AppProfileId(profileId),
        latestCategory = NotificationCategory.UNKNOWN,
        latestAgeBucket = NotificationAgeBucket.NOW,
        notifications = emptyList(),
    )

    private fun notification(
        packageName: String,
        key: String,
        artwork: String? = null,
    ) = LauncherNotification(
        key = LauncherNotificationKey(key),
        packageName = AppPackageName(packageName),
        canDismiss = true,
        largeIconPngBase64 = artwork,
        postedAtEpochMillis = 1L,
    )

    private fun installedApp(
        label: String,
        packageName: String,
    ) = InstalledApp(
        identity = AppIdentity(AppPackageName(packageName), AppActivityName(".MainActivity")),
        label = label,
    )
}
