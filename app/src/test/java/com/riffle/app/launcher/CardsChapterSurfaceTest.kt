package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.CardsChapter
import com.riffle.core.domain.launcher.cards.CardsChapterId
import com.riffle.core.domain.launcher.cards.CardsChapterPlanner
import com.riffle.core.domain.launcher.cards.CardsChapterPreferences
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardsChapterSurfaceTest {
    @Test
    fun overviewAccessMessagesDistinguishUnavailableStates() {
        assertEquals(
            CardsOverviewAccessMessage(
                title = "Notification access needed",
                message = "Allow notification access to show your Cards chapters.",
                action = LauncherShellAction.RequestNotificationAccess,
                actionLabel = "Allow notification access",
            ),
            cardsOverviewAccessMessage(NotificationAccessStatus.NOT_GRANTED),
        )
        assertEquals(
            CardsOverviewAccessMessage(
                title = "Notification access was revoked",
                message = "Restore notification access to update your Cards chapters.",
                action = LauncherShellAction.RequestNotificationAccess,
                actionLabel = "Restore notification access",
            ),
            cardsOverviewAccessMessage(NotificationAccessStatus.REVOKED),
        )
        assertEquals(
            CardsOverviewAccessMessage(
                title = "Checking notification access",
                message = "Cards will update when notification access is available.",
            ),
            cardsOverviewAccessMessage(NotificationAccessStatus.UNKNOWN),
        )
        assertNull(cardsOverviewAccessMessage(NotificationAccessStatus.GRANTED))
    }

    @Test
    fun appChapterKeepsEveryNotificationAvailableForScrolling() {
        val profile = AppProfile.personal()
        val chapter =
            CardsChapter.App(
                id = CardsChapterId.App(AppPackageName("com.riffle.mail"), profile.id),
                notificationGroup =
                    AppNotificationGroup(
                        packageName = AppPackageName("com.riffle.mail"),
                        profileId = profile.id,
                        latestCategory = NotificationCategory.EMAIL,
                        latestAgeBucket = NotificationAgeBucket.RECENT,
                        notifications =
                            (1..24).map { index ->
                                LauncherNotification(
                                    key = LauncherNotificationKey("mail-$index"),
                                    packageName = AppPackageName("com.riffle.mail"),
                                    profileId = profile.id,
                                    postedAtEpochMillis = index.toLong(),
                                )
                            },
                    ),
                isPinned = false,
            )

        assertEquals(24, cardsAppChapterNotifications(chapter).size)
        assertEquals(LauncherNotificationKey("mail-24"), cardsAppChapterNotifications(chapter).last().key)
    }

    @Test
    fun navigatorKeepsEveryChapterReachableWhenThePlanExceedsCompactWidth() {
        val profile = AppProfile.personal()
        val chapterIds =
            (1..12).map { index ->
                CardsChapterId.App(AppPackageName("com.riffle.app$index"), profile.id)
            }
        val state =
            CardsChapterPlanner().state(
                notificationGroups = emptyList(),
                preferences = CardsChapterPreferences(pinnedChapterIds = chapterIds),
            )

        assertEquals(
            listOf(CardsChapterId.Overview) + chapterIds,
            cardsChapterNavigatorChapterIds(state),
        )
    }

    @Test
    fun labelsAppChaptersWithTheMatchingProfiledApp() {
        val personal = AppProfile.personal()
        val app =
            InstalledApp(
                identity =
                    AppIdentity(
                        packageName = AppPackageName("com.riffle.mail"),
                        activityName = AppActivityName(".Main"),
                        profile = personal,
                    ),
                label = "Mail",
            )
        val chapter =
            CardsChapter.App(
                id = CardsChapterId.App(AppPackageName("com.riffle.mail"), personal.id),
                notificationGroup = null,
                isPinned = true,
            )

        assertEquals("Mail", chapter.label(listOf(app)))
    }

    @Test
    fun overviewSummariesKeepPlannerOrderAndDescribeLatestNotificationContent() {
        val profile = AppProfile.personal()
        val state =
            CardsChapterPlanner().state(
                notificationGroups =
                    listOf(
                        group(packageName = "com.riffle.mail", postedAtEpochMillis = 10L),
                        group(packageName = "com.riffle.chat", postedAtEpochMillis = 20L),
                    ),
                preferences =
                    CardsChapterPreferences(
                        pinnedChapterIds = listOf(CardsChapterId.App(AppPackageName("com.riffle.mail"), profile.id)),
                    ),
            )

        val summaries = cardsOverviewChapterSummaries(state, apps = emptyList())

        assertEquals(
            listOf("Mail", "Chat"),
            summaries.map(CardsOverviewChapterSummary::label),
        )
        assertEquals("Mail subject", summaries.first().latestTitle)
        assertEquals("Mail preview", summaries.first().latestContent)
        assertEquals("Email · Recent · 1 notification", summaries.first().metadata)
        assertEquals(
            "Mail. 1 notification. Mail subject. Mail preview. Email · Recent · 1 notification. Open chapter",
            summaries.first().contentDescription,
        )
    }

    private fun group(
        packageName: String,
        postedAtEpochMillis: Long,
    ): AppNotificationGroup =
        AppNotificationGroup(
            packageName = AppPackageName(packageName),
            profileId = AppProfile.personal().id,
            latestCategory = NotificationCategory.EMAIL,
            latestAgeBucket = NotificationAgeBucket.RECENT,
            notifications =
                listOf(
                    LauncherNotification(
                        key = LauncherNotificationKey(packageName),
                        packageName = AppPackageName(packageName),
                        profileId = AppProfile.personal().id,
                        title = "${packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }} subject",
                        text = "${packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }} preview",
                        postedAtEpochMillis = postedAtEpochMillis,
                    ),
                ),
        )
}
