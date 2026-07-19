package com.riffle.app.launcher.notifications

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.cards.AppStageContentKind
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStageNotificationContentTest {
    @Test
    fun `projects media with stable id and focused supported actions`() {
        val notification = notification(key = "media", postedAt = 20, isMedia = true, canDismiss = true)
        val availability = NotificationStageActionAvailability { _, _ -> setOf(NotificationStageAction.Open) }

        val cards =
            appStageNotificationCards(
                notifications = listOf(notification),
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
                profileContentVisibility = mapOf(AppProfile.personal().id to AppProfileContentVisibility.VISIBLE),
                actionAvailability = availability,
            )

        assertEquals("stage-notification:personal:media", cards.single().content.id.value)
        assertEquals(AppStageContentKind.MEDIA, cards.single().content.kind)
        assertEquals(
            setOf(NotificationStageAction.Open, NotificationStageAction.Dismiss),
            cards.single().supportedActions,
        )
    }

    @Test
    fun `redacts quiet content and excludes locked content`() {
        val quiet = notification(key = "quiet", postedAt = 2)
        val locked = notification(key = "locked", postedAt = 3, profile = AppProfile.work().id)

        val cards =
            appStageNotificationCards(
                notifications = listOf(quiet, locked),
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
                profileContentVisibility =
                    mapOf(
                        AppProfile.personal().id to AppProfileContentVisibility.REDACTED_QUIET,
                        AppProfile.work().id to AppProfileContentVisibility.REDACTED_LOCKED,
                    ),
            )

        assertEquals(1, cards.size)
        assertTrue(cards.single().isRedacted)
        assertEquals("Hidden notification", cards.single().title)
        assertTrue(cards.single().supportedActions.isEmpty())
    }

    @Test
    fun `requires granted notification access`() {
        val cards =
            appStageNotificationCards(
                notifications = listOf(notification(key = "one", postedAt = 1)),
                notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED,
                profileContentVisibility = mapOf(AppProfile.personal().id to AppProfileContentVisibility.VISIBLE),
            )

        assertTrue(cards.isEmpty())
    }

    private fun notification(
        key: String,
        postedAt: Long,
        isMedia: Boolean = false,
        canDismiss: Boolean = false,
        profile: com.riffle.core.domain.launcher.apps.AppProfileId = AppProfile.personal().id,
    ) = LauncherNotification(
        key = LauncherNotificationKey(key),
        packageName = AppPackageName("com.example.$key"),
        profileId = profile,
        isMediaSession = isMedia,
        canDismiss = canDismiss,
        title = "Title $key",
        text = "Text $key",
        postedAtEpochMillis = postedAt,
    )
}
