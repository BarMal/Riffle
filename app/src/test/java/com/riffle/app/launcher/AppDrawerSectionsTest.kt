package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDrawerSectionsTest {
    @Test
    fun sectionsPreserveAppOrderWithinFirstLetterBuckets() {
        val sections =
            AppDrawerSections.from(
                listOf(
                    app("Browser"),
                    app("Calculator"),
                    app("Calendar"),
                    app("Camera"),
                ),
            )

        assertEquals(listOf("B", "C"), sections.map { section -> section.title })
        assertEquals(listOf("B (1)", "C (3)"), sections.map { section -> section.displayTitle })
        assertEquals(listOf("Browser"), sections[0].apps.map { app -> app.label })
        assertEquals(listOf("Calculator", "Calendar", "Camera"), sections[1].apps.map { app -> app.label })
    }

    @Test
    fun sectionsUseOtherBucketForLabelsWithoutLeadingLetters() {
        val sections =
            AppDrawerSections.from(
                listOf(
                    app("  1Password"),
                    app("- Debug"),
                    app("Alpha"),
                ),
            )

        assertEquals(listOf("A", "#"), sections.map { section -> section.title })
        assertEquals(listOf("  1Password", "- Debug"), sections[1].apps.map { app -> app.label })
    }

    @Test
    fun sectionsPlaceOtherBucketLastWithinProfilePrefixes() {
        val sections =
            AppDrawerSections.from(
                listOf(
                    app("  1Password", profile = AppProfile.work()),
                    app("Calendar"),
                    app("Alpha", profile = AppProfile.work()),
                    app("- Debug"),
                ),
            )

        assertEquals(listOf("C", "#", "Work - A", "Work - #"), sections.map { section -> section.title })
    }

    @Test
    fun sectionsUseProfilePrefixesForWorkApps() {
        val sections =
            AppDrawerSections.from(
                listOf(
                    app("Calendar"),
                    app("Calendar", profile = AppProfile.work()),
                    app("Docs", profile = AppProfile.work()),
                ),
            )

        assertEquals(listOf("C", "Work - C", "Work - D"), sections.map { section -> section.title })
        assertEquals(listOf("Calendar"), sections[0].apps.map { app -> app.label })
        assertEquals(listOf("Calendar"), sections[1].apps.map { app -> app.label })
        assertEquals(listOf("Docs"), sections[2].apps.map { app -> app.label })
    }

    @Test
    fun sectionsUseProfilePrefixesForNonDefaultPersonalProfiles() {
        val sections =
            AppDrawerSections.from(
                listOf(
                    app("Browser"),
                    app(
                        label = "Browser",
                        profile = AppProfile(AppProfileId("secondary"), AppProfileType.PERSONAL),
                    ),
                ),
            )

        assertEquals(listOf("B", "Personal - B"), sections.map { section -> section.title })
    }

    @Test
    fun sectionsSortProfileBucketsInStableLauncherOrder() {
        val secondaryPersonal = AppProfile(AppProfileId("secondary"), AppProfileType.PERSONAL)

        val sections =
            AppDrawerSections.from(
                listOf(
                    app("Vault", profile = AppProfile.private()),
                    app("Docs", profile = AppProfile.work()),
                    app("Browser"),
                    app("Alpha", profile = secondaryPersonal),
                    app("Notes"),
                    app("Calendar", profile = AppProfile.work()),
                    app("Bank", profile = AppProfile.private()),
                ),
            )

        assertEquals(
            listOf("B", "N", "Personal - A", "Work - C", "Work - D", "Private - B", "Private - V"),
            sections.map { section -> section.title },
        )
    }

    @Test
    fun sectionsPlaceOtherBucketLastWithinEachProfileBucket() {
        val secondaryPersonal = AppProfile(AppProfileId("secondary"), AppProfileType.PERSONAL)

        val sections =
            AppDrawerSections.from(
                listOf(
                    app("  1Private", profile = AppProfile.private()),
                    app("  1Work", profile = AppProfile.work()),
                    app("  1Secondary", profile = secondaryPersonal),
                    app("  1Personal"),
                    app("Delta", profile = AppProfile.private()),
                    app("Charlie", profile = AppProfile.work()),
                    app("Beta", profile = secondaryPersonal),
                    app("Alpha"),
                ),
            )

        assertEquals(
            listOf("A", "#", "Personal - B", "Personal - #", "Work - C", "Work - #", "Private - D", "Private - #"),
            sections.map { section -> section.title },
        )
    }

    @Test
    fun sectionsGroupCategorizedAppsUnderTheirLauncherCategory() {
        val sections =
            AppDrawerSections.from(
                listOf(
                    app("Camera", category = "Image"),
                    app("Browser", category = "News"),
                    app("Gallery", category = " Image "),
                    app("Calculator"),
                ),
            )

        assertEquals(listOf("C", "Image", "News"), sections.map { section -> section.title })
        assertEquals(listOf("Camera", "Gallery"), sections[1].apps.map { app -> app.label })
    }

    @Test
    fun sectionsKeepCategoriesSeparatedByProfileAndIgnoreBlankCategoryNames() {
        val sections =
            AppDrawerSections.from(
                listOf(
                    app("Camera", category = "Image"),
                    app("Gallery", category = "Image", profile = AppProfile.work()),
                    app("Browser", category = " "),
                ),
            )

        assertEquals(listOf("B", "Image", "Work - Image"), sections.map { section -> section.title })
    }

    private fun app(
        label: String,
        profile: AppProfile = AppProfile.personal(),
        category: String? = null,
    ): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.example.${label.lowercase().replace(" ", ".")}"),
                    activityName = AppActivityName(".MainActivity"),
                    profile = profile,
                ),
            label = label,
            category = category,
        )
}
