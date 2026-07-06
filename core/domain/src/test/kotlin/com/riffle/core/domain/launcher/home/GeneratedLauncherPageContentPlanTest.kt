package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.AppVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.notifications.AppNotificationGroupKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneratedLauncherPageContentPlanTest {
    private val planner = GeneratedLauncherPageContentPlanner()

    @Test
    fun appBackedPlansUseVisibleAppsInStableIdentityOrder() {
        val input =
            GeneratedLauncherPageContentPlanInput(
                installedApps =
                    listOf(
                        app(packageName = "com.riffle.zeta"),
                        app(packageName = "com.riffle.hidden", visibility = AppVisibility.HIDDEN),
                        app(packageName = "com.riffle.alpha", activityName = ".Secondary"),
                        app(packageName = "com.riffle.disabled", enabled = false),
                        app(packageName = "com.riffle.alpha"),
                    ),
            )

        val plan = planner.plan(kind = GeneratedLauncherPageKind.APP, input = input)

        assertTrue(plan.canCreate)
        assertEquals(GeneratedLauncherPageKind.APP.spec.defaultPageId(), plan.pageId)
        assertEquals(GeneratedLauncherPageKind.APP.spec.requiredDataSources, plan.requiredDataSources)
        assertEquals(
            listOf(
                appItem(packageName = "com.riffle.alpha"),
                appItem(packageName = "com.riffle.alpha", activityName = ".Secondary"),
                appItem(packageName = "com.riffle.zeta"),
            ),
            plan.items,
        )
        assertEquals(
            plan,
            planner.plan(
                kind = GeneratedLauncherPageKind.APP,
                input = input.copy(installedApps = input.installedApps.reversed()),
            ),
        )
    }

    @Test
    fun profilePlansOnlyIncludeMatchingVisibleProfileApps() {
        val personal = app(packageName = "com.riffle.personal", profile = AppProfile.personal())
        val workB = app(packageName = "com.riffle.work.b", profile = workProfile("b"))
        val private = app(packageName = "com.riffle.private", profile = AppProfile.private())
        val workA = app(packageName = "com.riffle.work.a", profile = workProfile("a"))
        val input = GeneratedLauncherPageContentPlanInput(installedApps = listOf(personal, workB, private, workA))

        val workPlan = planner.plan(kind = GeneratedLauncherPageKind.WORK, input = input)
        val personalPlan = planner.plan(kind = GeneratedLauncherPageKind.PERSONAL, input = input)

        assertTrue(workPlan.canCreate)
        assertEquals(
            listOf(
                appItem(packageName = "com.riffle.work.a", profile = workProfile("a")),
                appItem(packageName = "com.riffle.work.b", profile = workProfile("b")),
            ),
            workPlan.items,
        )
        assertTrue(personalPlan.canCreate)
        assertEquals(listOf(appItem(packageName = "com.riffle.personal")), personalPlan.items)
    }

    @Test
    fun profilePlansStayUnavailableWhenOnlyOtherProfilesHaveApps() {
        val input =
            GeneratedLauncherPageContentPlanInput(
                installedApps =
                    listOf(
                        app(packageName = "com.riffle.personal", profile = AppProfile.personal()),
                    ),
            )

        val workPlan = planner.plan(kind = GeneratedLauncherPageKind.WORK, input = input)

        assertFalse(workPlan.canCreate)
        assertEquals(emptyList(), workPlan.items)
        assertEquals(GeneratedLauncherPageKind.WORK.spec.requiredDataSources, workPlan.requiredDataSources)
    }

    @Test
    fun notificationCardPlansUseGroupKeysInStableOrder() {
        val input =
            GeneratedLauncherPageContentPlanInput(
                notificationGroupKeys =
                    listOf(
                        notificationGroupKey(packageName = "com.riffle.mail", profileId = "work"),
                        notificationGroupKey(packageName = "com.riffle.chat", profileId = "personal"),
                        notificationGroupKey(packageName = "com.riffle.chat", profileId = "personal"),
                    ),
            )

        val plan = planner.plan(kind = GeneratedLauncherPageKind.NOTIFICATION_CARDS, input = input)

        assertTrue(plan.canCreate)
        assertEquals(
            listOf(
                notificationGroupItem(packageName = "com.riffle.chat", profileId = "personal"),
                notificationGroupItem(packageName = "com.riffle.mail", profileId = "work"),
            ),
            plan.items,
        )
    }

    @Test
    fun generatedKindsWithoutTypedContentSourcesReturnAcceptedEmptyDescriptors() {
        val input =
            GeneratedLauncherPageContentPlanInput(
                installedApps = listOf(app(packageName = "com.riffle.camera")),
                appCategoriesAvailable = true,
                favouriteAppsAvailable = true,
                usageStatsAvailable = true,
            )

        val plans =
            planner.plansFor(
                kinds =
                    listOf(
                        GeneratedLauncherPageKind.CATEGORY,
                        GeneratedLauncherPageKind.FAVOURITES,
                        GeneratedLauncherPageKind.FREQUENTLY_USED,
                    ),
                input = input,
            )

        assertEquals(3, plans.size)
        plans.forEach { plan ->
            assertTrue(plan.canCreate)
            assertEquals(emptyList(), plan.items)
            assertEquals(plan.kind.spec.requiredDataSources, plan.requiredDataSources)
        }
    }

    @Test
    fun unavailablePlansKeepSpecConstraintsAndNoContent() {
        val plan =
            planner.plan(
                kind = GeneratedLauncherPageKind.FAVOURITES,
                input =
                    GeneratedLauncherPageContentPlanInput(
                        installedApps = listOf(app(packageName = "com.riffle.camera")),
                    ),
                pageId = LauncherPageId("generated:favourites:7"),
            )

        assertFalse(plan.canCreate)
        assertEquals(LauncherPageId("generated:favourites:7"), plan.pageId)
        assertEquals(GeneratedLauncherPageKind.FAVOURITES.spec.requiredDataSources, plan.requiredDataSources)
        assertEquals(emptyList(), plan.items)
    }

    private fun app(
        packageName: String,
        activityName: String = ".MainActivity",
        profile: AppProfile = AppProfile.personal(),
        enabled: Boolean = true,
        visibility: AppVisibility = AppVisibility.VISIBLE,
    ): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName(packageName),
                    activityName = AppActivityName(activityName),
                    profile =
                        if (profile.type == AppProfileType.PERSONAL) {
                            AppProfile.personal()
                        } else {
                            profile
                        },
                ),
            label = packageName.substringAfterLast('.'),
            enabled = enabled,
            visibility = visibility,
        )

    private fun appItem(
        packageName: String,
        activityName: String = ".MainActivity",
        profile: AppProfile = AppProfile.personal(),
    ): GeneratedLauncherPageContentItem.App =
        GeneratedLauncherPageContentItem.App(
            identity =
                AppIdentity(
                    packageName = AppPackageName(packageName),
                    activityName = AppActivityName(activityName),
                    profile = profile,
                ),
        )

    private fun notificationGroupKey(
        packageName: String,
        profileId: String,
    ): AppNotificationGroupKey =
        AppNotificationGroupKey(
            packageName = AppPackageName(packageName),
            profileId = AppProfileId(profileId),
        )

    private fun notificationGroupItem(
        packageName: String,
        profileId: String,
    ): GeneratedLauncherPageContentItem.NotificationGroup =
        GeneratedLauncherPageContentItem.NotificationGroup(
            key = notificationGroupKey(packageName = packageName, profileId = profileId),
        )

    private fun workProfile(id: String): AppProfile =
        AppProfile(
            id = AppProfileId(id),
            type = AppProfileType.WORK,
        )
}
