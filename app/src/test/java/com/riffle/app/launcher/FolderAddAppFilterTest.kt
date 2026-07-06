package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderAddAppFilterTest {
    @Test
    fun removesAppsAlreadyPlacedOnHome() {
        val camera = app(label = "Camera")
        val calendar = app(label = "Calendar")
        val maps = app(label = "Maps")
        val layout =
            layoutWith(
                shortcut(id = "camera", app = camera),
                FolderItem(
                    id = LauncherItemId("folder:tools"),
                    label = "Tools",
                    items = listOf(shortcut(id = "folder-calendar", app = calendar).copy(placement = null)),
                    placement = GridPlacement(cell = GridCell(column = 1, row = 0)),
                ),
            )

        assertEquals(listOf(maps), listOf(camera, calendar, maps).filterFolderAddCandidates(layout))
    }

    @Test
    fun returnsAllAppsForBlankQuery() {
        val apps = listOf(app(label = "Camera"), app(label = "Calendar"))

        assertEquals(apps, apps.filterFolderAddCandidates("  "))
    }

    @Test
    fun filtersAppsByLabelWithoutChangingOrder() {
        val camera = app(label = "Camera")
        val calendar = app(label = "Calendar")
        val clock = app(label = "Clock")
        val apps = listOf(camera, calendar, clock)

        assertEquals(listOf(camera, calendar), apps.filterFolderAddCandidates("ca"))
    }

    @Test
    fun normalizesFolderAddSearchWhitespaceAndCase() {
        val googleMaps = app(label = "Google Maps")
        val googleMessages = app(label = "Google Messages")
        val apps = listOf(googleMaps, googleMessages)

        assertEquals(listOf(googleMaps), apps.filterFolderAddCandidates("  GOOGLE   maps "))
    }

    @Test
    fun filtersAppsByPackageName() {
        val camera = app(label = "Camera", packageName = "com.android.camera")
        val calendar = app(label = "Calendar", packageName = "com.google.calendar")
        val apps = listOf(camera, calendar)

        assertEquals(listOf(calendar), apps.filterFolderAddCandidates("google"))
    }

    @Test
    fun filtersAppsByActivityName() {
        val settings = app(label = "Settings", activityName = ".HomeSettingsActivity")
        val camera = app(label = "Camera", activityName = ".CaptureActivity")
        val apps = listOf(settings, camera)

        assertEquals(listOf(settings), apps.filterFolderAddCandidates("home"))
    }

    @Test
    fun filtersAppsByProfileTypeAndId() {
        val personalCamera = app(label = "Camera", profile = AppProfile.personal())
        val workDocs = app(label = "Docs", profile = AppProfile.work())
        val companySheets = app(label = "Sheets", profile = AppProfile(AppProfileId("company"), AppProfileType.WORK))
        val apps = listOf(personalCamera, workDocs, companySheets)

        assertEquals(listOf(workDocs, companySheets), apps.filterFolderAddCandidates("work"))
        assertEquals(listOf(companySheets), apps.filterFolderAddCandidates("company"))
        assertEquals(listOf(personalCamera), apps.filterFolderAddCandidates("personal"))
    }

    @Test
    fun filtersAppsByMultipleQueryTokensAcrossFields() {
        val personalMaps = app(label = "Maps", packageName = "com.google.maps", profile = AppProfile.personal())
        val workDocs = app(label = "Docs", packageName = "com.google.docs", profile = AppProfile.work())
        val workCalendar = app(label = "Calendar", packageName = "com.android.calendar", profile = AppProfile.work())
        val apps = listOf(personalMaps, workDocs, workCalendar)

        assertEquals(listOf(workDocs), apps.filterFolderAddCandidates("google work"))
        assertEquals(listOf(workCalendar), apps.filterFolderAddCandidates("calendar work"))
        assertEquals(emptyList<InstalledApp>(), apps.filterFolderAddCandidates("maps work"))
    }

    @Test
    fun returnsAllAppsForAllProfileFilter() {
        val apps =
            listOf(
                app(label = "Camera", profile = AppProfile.personal()),
                app(label = "Docs", profile = AppProfile.work()),
            )

        assertEquals(apps, apps.filterFolderAddCandidates(AppDrawerProfileFilter.ALL))
    }

    @Test
    fun filtersAppsBySelectedProfile() {
        val personalCamera = app(label = "Camera", profile = AppProfile.personal())
        val workDocs = app(label = "Docs", profile = AppProfile.work())
        val privateVault = app(label = "Vault", profile = AppProfile.private())
        val companySheets = app(label = "Sheets", profile = AppProfile(AppProfileId("company"), AppProfileType.WORK))
        val apps = listOf(personalCamera, workDocs, privateVault, companySheets)

        assertEquals(listOf(personalCamera), apps.filterFolderAddCandidates(AppDrawerProfileFilter.PERSONAL))
        assertEquals(listOf(workDocs, companySheets), apps.filterFolderAddCandidates(AppDrawerProfileFilter.WORK))
        assertEquals(listOf(privateVault), apps.filterFolderAddCandidates(AppDrawerProfileFilter.PRIVATE))
    }

    @Test
    fun describesEmptyFolderAddCandidatesWhenNoAppsAreLeft() {
        val apps = emptyList<InstalledApp>()

        assertEquals(
            "No apps left to add",
            apps.folderAddEmptyText(query = "", profileFilter = AppDrawerProfileFilter.ALL),
        )
    }

    @Test
    fun describesEmptyFolderAddCandidatesWhenFiltersHideMatches() {
        val apps = listOf(app(label = "Camera"))

        assertEquals(
            "No matching apps",
            apps.folderAddEmptyText(query = "docs", profileFilter = AppDrawerProfileFilter.ALL),
        )
        assertEquals(
            "No matching apps",
            apps.folderAddEmptyText(query = "", profileFilter = AppDrawerProfileFilter.WORK),
        )
    }

    @Test
    fun resultSummaryDescribesFolderAddCandidateCounts() {
        assertEquals(
            "2 apps left to add",
            folderAddResultSummaryText(
                totalCandidateCount = 2,
                resultCount = 2,
                query = "",
                profileFilter = AppDrawerProfileFilter.ALL,
            ),
        )
        assertEquals(
            "1 app matching, 3 apps left to add",
            folderAddResultSummaryText(
                totalCandidateCount = 3,
                resultCount = 1,
                query = "camera",
                profileFilter = AppDrawerProfileFilter.ALL,
            ),
        )
        assertEquals(
            "0 apps matching, 3 apps left to add",
            folderAddResultSummaryText(
                totalCandidateCount = 3,
                resultCount = 0,
                query = "",
                profileFilter = AppDrawerProfileFilter.WORK,
            ),
        )
    }

    @Test
    fun candidateKeysIncludeProfileId() {
        val personalCamera = app(label = "Camera", profile = AppProfile.personal())
        val workCamera = app(label = "Camera", profile = AppProfile.work())

        assertEquals("personal:com.riffle.camera/.MainActivity", personalCamera.folderAddCandidateKey())
        assertEquals("work:com.riffle.camera/.MainActivity", workCamera.folderAddCandidateKey())
    }

    @Test
    fun showsClearFiltersWhenFolderAddQueryOrProfileFilterIsActive() {
        assertFalse(
            shouldShowFolderAddClearFilters(
                query = "",
                profileFilter = AppDrawerProfileFilter.ALL,
            ),
        )
        assertTrue(
            shouldShowFolderAddClearFilters(
                query = "camera",
                profileFilter = AppDrawerProfileFilter.ALL,
            ),
        )
        assertTrue(
            shouldShowFolderAddClearFilters(
                query = "",
                profileFilter = AppDrawerProfileFilter.WORK,
            ),
        )
    }

    private fun app(
        label: String,
        packageName: String = "com.riffle.${label.lowercase()}",
        activityName: String = ".MainActivity",
        profile: AppProfile = AppProfile.personal(),
    ): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName(packageName),
                    activityName = AppActivityName(activityName),
                    profile = profile,
                ),
            label = label,
        )

    private fun layoutWith(vararg items: LauncherItem) =
        HomeLayoutDefaults.standard().copy(
            pages = listOf(HomeLayoutDefaults.standard().selectedPage.copy(items = items.toList())),
        )

    private fun shortcut(
        id: String,
        app: InstalledApp,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(id),
            appIdentity = app.identity,
            label = app.label,
            placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
        )
}
