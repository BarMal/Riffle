package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.AppVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneratedLauncherPageSpecTest {
    @Test
    fun everyGeneratedLauncherPageKindHasAStableSpec() {
        val specs = GeneratedLauncherPageSpecs.all

        assertEquals(GeneratedLauncherPageKind.entries.toSet(), specs.map { spec -> spec.kind }.toSet())
        assertEquals(specs.size, specs.map { spec -> spec.defaultPageIdPrefix }.toSet().size)
        specs.forEach { spec ->
            assertTrue(spec.defaultPageIdPrefix.startsWith("generated:"))
            assertTrue(spec.requiredDataSources.isNotEmpty())
        }
    }

    @Test
    fun generatedPageIdsAreDeterministicFromSpecPrefixAndInstance() {
        val spec = GeneratedLauncherPageKind.TODAY.spec

        assertEquals(LauncherPageId("generated:today:1"), spec.defaultPageId())
        assertEquals(LauncherPageId("generated:today:3"), spec.defaultPageId(instance = 3))
        assertEquals(spec.defaultPageId(instance = 3), spec.defaultPageId(instance = 3))
    }

    @Test
    fun generatedPageIdsRejectNonPositiveInstances() {
        assertFailsWith<IllegalArgumentException> {
            GeneratedLauncherPageKind.TODAY.spec.defaultPageId(instance = 0)
        }
    }

    @Test
    fun appBackedPagesRequireVisibleInstalledApps() {
        val enabledHiddenAndExcludedApps =
            GeneratedLauncherPageData(
                installedApps =
                    listOf(
                        app(label = "Disabled", enabled = false),
                        app(label = "Hidden", visibility = AppVisibility.HIDDEN),
                        app(label = "Excluded", visibility = AppVisibility.EXCLUDED),
                    ),
                appCategoriesAvailable = true,
                favouriteAppsAvailable = true,
                usageStatsAvailable = true,
            )
        val visibleApps =
            enabledHiddenAndExcludedApps.copy(
                installedApps = enabledHiddenAndExcludedApps.installedApps + app(label = "Camera"),
            )

        assertFalse(GeneratedLauncherPageKind.APP.spec.canCreateWith(enabledHiddenAndExcludedApps))
        assertFalse(GeneratedLauncherPageKind.TODAY.spec.canCreateWith(enabledHiddenAndExcludedApps))
        assertFalse(GeneratedLauncherPageKind.FAVOURITES.spec.canCreateWith(enabledHiddenAndExcludedApps))
        assertFalse(GeneratedLauncherPageKind.FREQUENTLY_USED.spec.canCreateWith(enabledHiddenAndExcludedApps))

        assertTrue(GeneratedLauncherPageKind.APP.spec.canCreateWith(visibleApps))
        assertTrue(GeneratedLauncherPageKind.TODAY.spec.canCreateWith(visibleApps))
        assertTrue(GeneratedLauncherPageKind.FAVOURITES.spec.canCreateWith(visibleApps))
        assertTrue(GeneratedLauncherPageKind.FREQUENTLY_USED.spec.canCreateWith(visibleApps))
    }

    @Test
    fun categoryPagesRequireVisibleInstalledAppsAndCategoryData() {
        val visibleApps = GeneratedLauncherPageData(installedApps = listOf(app(label = "Camera")))

        assertEquals(
            setOf(
                GeneratedLauncherPageDataSource.INSTALLED_APPS,
                GeneratedLauncherPageDataSource.APP_CATEGORIES,
            ),
            GeneratedLauncherPageKind.CATEGORY.spec.requiredDataSources,
        )
        assertFalse(GeneratedLauncherPageKind.CATEGORY.spec.canCreateWith(visibleApps))
        assertFalse(
            GeneratedLauncherPageKind.CATEGORY.spec.canCreateWith(
                GeneratedLauncherPageData(appCategoriesAvailable = true),
            ),
        )
        assertTrue(
            GeneratedLauncherPageKind.CATEGORY.spec.canCreateWith(
                visibleApps.copy(appCategoriesAvailable = true),
            ),
        )
    }

    @Test
    fun profilePagesRequireVisibleInstalledAppsForMatchingProfileType() {
        val personalData = GeneratedLauncherPageData(installedApps = listOf(app(label = "Camera")))
        val workData =
            GeneratedLauncherPageData(
                installedApps = listOf(app(label = "Docs", profile = AppProfile.work())),
            )
        val privateData =
            GeneratedLauncherPageData(
                installedApps = listOf(app(label = "Vault", profile = AppProfile.private())),
            )

        assertEquals(
            setOf(
                GeneratedLauncherPageDataSource.INSTALLED_APPS,
                GeneratedLauncherPageDataSource.APP_PROFILES,
            ),
            GeneratedLauncherPageKind.PERSONAL.spec.requiredDataSources,
        )
        assertEquals(
            setOf(
                GeneratedLauncherPageDataSource.INSTALLED_APPS,
                GeneratedLauncherPageDataSource.APP_PROFILES,
            ),
            GeneratedLauncherPageKind.WORK.spec.requiredDataSources,
        )

        assertTrue(GeneratedLauncherPageKind.PERSONAL.spec.canCreateWith(personalData))
        assertFalse(GeneratedLauncherPageKind.WORK.spec.canCreateWith(personalData))
        assertTrue(GeneratedLauncherPageKind.WORK.spec.canCreateWith(workData))
        assertFalse(GeneratedLauncherPageKind.PERSONAL.spec.canCreateWith(workData))
        assertFalse(GeneratedLauncherPageKind.WORK.spec.canCreateWith(privateData))
        assertFalse(GeneratedLauncherPageKind.PERSONAL.spec.canCreateWith(privateData))
    }

    @Test
    fun notificationCardsRequireNotificationCardData() {
        val spec = GeneratedLauncherPageKind.NOTIFICATION_CARDS.spec

        assertEquals(
            setOf(GeneratedLauncherPageDataSource.NOTIFICATION_CARDS),
            spec.requiredDataSources,
        )
        assertFalse(
            spec.canCreateWith(
                GeneratedLauncherPageData(installedApps = listOf(app(label = "Messages"))),
            ),
        )
        assertTrue(
            spec.canCreateWith(
                GeneratedLauncherPageData(notificationCardsAvailable = true),
            ),
        )
    }

    private fun app(
        label: String,
        profile: AppProfile = AppProfile.personal(),
        enabled: Boolean = true,
        visibility: AppVisibility = AppVisibility.VISIBLE,
    ): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                    profile =
                        if (profile.type == AppProfileType.PERSONAL) {
                            AppProfile.personal()
                        } else {
                            profile
                        },
                ),
            label = label,
            enabled = enabled,
            visibility = visibility,
        )
}
