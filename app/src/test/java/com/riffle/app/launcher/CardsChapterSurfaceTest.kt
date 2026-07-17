package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.AppProfileId
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

        val summaries =
            cardsOverviewChapterSummaries(
                state = state,
                apps = emptyList(),
                profileContentVisibility = mapOf(profile.id to AppProfileContentVisibility.VISIBLE),
            )

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

    @Test
    fun overviewSummariesRedactNotificationTextForQuietLockedAndUnavailableProfiles() {
        val personal = AppProfile.personal()
        val work = AppProfile.work()
        val private = AppProfile.private()
        val unavailable = AppProfile(AppProfileId("removed"), work.type)
        val state =
            CardsChapterPlanner().state(
                notificationGroups =
                    listOf(
                        group(packageName = "com.riffle.unavailable", profile = unavailable, postedAtEpochMillis = 40L),
                        group(packageName = "com.riffle.quiet", profile = work, postedAtEpochMillis = 30L),
                        group(packageName = "com.riffle.locked", profile = private, postedAtEpochMillis = 20L),
                        group(packageName = "com.riffle.visible", profile = personal, postedAtEpochMillis = 10L),
                    ),
            )

        val summaries =
            cardsOverviewChapterSummaries(
                state = state,
                apps = emptyList(),
                profileContentVisibility =
                    mapOf(
                        work.id to AppProfileContentVisibility.REDACTED_QUIET,
                        private.id to AppProfileContentVisibility.REDACTED_LOCKED,
                        personal.id to AppProfileContentVisibility.VISIBLE,
                    ),
            )

        assertEquals("Unavailable notification", summaries[0].latestTitle)
        assertEquals("Profile content is unavailable", summaries[0].latestContent)
        assertEquals("Quiet notification", summaries[1].latestTitle)
        assertEquals("Profile is paused", summaries[1].latestContent)
        assertEquals("Locked notification", summaries[2].latestTitle)
        assertEquals("Profile is locked", summaries[2].latestContent)
        assertEquals("Visible subject", summaries[3].latestTitle)
        assertEquals("Visible preview", summaries[3].latestContent)
        assertEquals(
            "Quiet. 1 notification. Quiet notification. Profile is paused. " +
                "Email · Recent · 1 notification. Open chapter",
            summaries[1].contentDescription,
        )
    }

    private fun group(
        packageName: String,
        profile: AppProfile = AppProfile.personal(),
        postedAtEpochMillis: Long,
    ): AppNotificationGroup =
        AppNotificationGroup(
            packageName = AppPackageName(packageName),
            profileId = profile.id,
            latestCategory = NotificationCategory.EMAIL,
            latestAgeBucket = NotificationAgeBucket.RECENT,
            notifications =
                listOf(
                    LauncherNotification(
                        key = LauncherNotificationKey(packageName),
                        packageName = AppPackageName(packageName),
                        profileId = profile.id,
                        title = "${packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }} subject",
                        text = "${packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }} preview",
                        postedAtEpochMillis = postedAtEpochMillis,
                    ),
                ),
        )
}
