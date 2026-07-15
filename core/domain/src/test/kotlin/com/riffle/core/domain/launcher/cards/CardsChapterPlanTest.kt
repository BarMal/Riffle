package com.riffle.core.domain.launcher.cards

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CardsChapterPlanTest {
    private val planner = CardsChapterPlanner()

    @Test
    fun alwaysIncludesOverviewBeforeNotificationChapters() {
        val plan = planner.plan(notificationGroups = listOf(group(packageName = "com.riffle.mail")))

        assertEquals(
            listOf(CardsChapterId.Overview, appId("com.riffle.mail")),
            plan.chapterIds,
        )
    }

    @Test
    fun separatesMatchingPackagesAcrossProfiles() {
        val plan =
            planner.plan(
                notificationGroups =
                    listOf(
                        group(packageName = "com.riffle.mail", profile = AppProfile.personal()),
                        group(packageName = "com.riffle.mail", profile = AppProfile.work()),
                    ),
            )

        assertEquals(
            listOf(
                CardsChapterId.Overview,
                appId("com.riffle.mail", AppProfile.personal()),
                appId("com.riffle.mail", AppProfile.work()),
            ),
            plan.chapterIds,
        )
    }

    @Test
    fun retainsPinnedEmptyChapterInUserOrder() {
        val emptyPinnedId = appId("com.riffle.calendar")
        val activePinnedId = appId("com.riffle.mail")
        val plan =
            planner.plan(
                notificationGroups = listOf(group(packageName = "com.riffle.mail")),
                pinnedChapterIds = listOf(emptyPinnedId, activePinnedId),
            )

        assertEquals(listOf(CardsChapterId.Overview, emptyPinnedId, activePinnedId), plan.chapterIds)
        val emptyChapter = plan.chapters[1] as CardsChapter.App
        assertNull(emptyChapter.notificationGroup)
        assertEquals(true, emptyChapter.isPinned)
    }

    @Test
    fun ordersTransientChaptersByRecencyThenStableIdentity() {
        val plan =
            planner.plan(
                notificationGroups =
                    listOf(
                        group(packageName = "com.riffle.zeta", postedAtEpochMillis = 100L),
                        group(packageName = "com.riffle.alpha", postedAtEpochMillis = 100L),
                        group(packageName = "com.riffle.mail", postedAtEpochMillis = 200L),
                    ),
            )

        assertEquals(
            listOf(
                CardsChapterId.Overview,
                appId("com.riffle.mail"),
                appId("com.riffle.alpha"),
                appId("com.riffle.zeta"),
            ),
            plan.chapterIds,
        )
    }

    @Test
    fun recoversToOverviewWhenSelectedTransientChapterDisappears() {
        val plan = planner.plan(notificationGroups = emptyList())

        assertEquals(
            CardsChapterId.Overview,
            planner.reconcileSelectedChapter(appId("com.riffle.mail"), plan),
        )
    }

    @Test
    fun preservesSelectedPinnedChapterWhenItsNotificationDisappears() {
        val pinnedId = appId("com.riffle.mail")
        val plan = planner.plan(notificationGroups = emptyList(), pinnedChapterIds = listOf(pinnedId))

        assertEquals(pinnedId, planner.reconcileSelectedChapter(pinnedId, plan))
    }

    private fun appId(
        packageName: String,
        profile: AppProfile = AppProfile.personal(),
    ) = CardsChapterId.App(AppPackageName(packageName), profile.id)

    private fun group(
        packageName: String,
        profile: AppProfile = AppProfile.personal(),
        postedAtEpochMillis: Long = 100L,
    ): AppNotificationGroup {
        return AppNotificationGroup(
            packageName = AppPackageName(packageName),
            profileId = profile.id,
            latestCategory = NotificationCategory.MESSAGE,
            latestAgeBucket = NotificationAgeBucket.RECENT,
            notifications =
                listOf(
                    LauncherNotification(
                        key = LauncherNotificationKey("$packageName-${profile.id.value}"),
                        packageName = AppPackageName(packageName),
                        profileId = profile.id,
                        postedAtEpochMillis = postedAtEpochMillis,
                    ),
                ),
        )
    }
}
