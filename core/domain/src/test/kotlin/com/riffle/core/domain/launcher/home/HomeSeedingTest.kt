package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HomeSeedingTest {
    private fun app(
        name: String,
        pkg: String = "pkg.${name.lowercase()}",
        profile: AppProfile = AppProfile.personal(),
    ) = InstalledApp(
        identity = AppIdentity(AppPackageName(pkg), AppActivityName("Main"), profile),
        label = name,
    )

    private val phone = app("Phone")
    private val messages = app("Messages")
    private val browser = app("Browser")
    private val camera = app("Camera")
    private val others = listOf(app("Notes"), app("Clock"), app("Zebra"))
    private val installed = listOf(phone, messages, browser, camera) + others
    private val roles = listOf(phone, messages, browser, camera).map { it.identity.packageName }

    private fun fresh() = HomeLayoutDefaults.standard()

    private fun HomeLayout.homeLabels() = pages.flatMap { it.items }.map { (it as AppShortcutItem).label }

    @Test
    fun allAppsSeedsTheDockWithTheRolesAndPlacesTheRestAToZ() {
        val seeded = HomeSeeding.seed(fresh(), installed, roles, HomeStart.ALL_APPS, compact = true)

        assertEquals(
            listOf("Phone", "Messages", "Browser", "Camera"),
            seeded.dock.items.map { (it as AppShortcutItem).label },
        )
        assertEquals(listOf("Clock", "Notes", "Zebra"), seeded.homeLabels())
    }

    @Test
    fun appsPlacedInTheDockAreNotAlsoPlacedOnHome() {
        val seeded = HomeSeeding.seed(fresh(), installed, roles, HomeStart.ALL_APPS, compact = true)

        assertTrue(seeded.homeLabels().none { it in setOf("Phone", "Messages", "Browser", "Camera") })
    }

    @Test
    fun startEmptyLeavesOneEmptyPageButStillSeedsTheDock() {
        val seeded = HomeSeeding.seed(fresh(), installed, roles, HomeStart.EMPTY, compact = true)

        assertEquals(1, seeded.pages.size)
        assertTrue(seeded.pages.single().items.isEmpty())
        assertEquals(4, seeded.dock.items.size)
    }

    @Test
    fun aRoleWithNoLaunchableAppIsSkippedSilentlyAndRepeatsAreDropped() {
        val unresolved = listOf(AppPackageName("pkg.missing"), phone.identity.packageName, phone.identity.packageName)

        val seeded = HomeSeeding.seed(fresh(), installed, unresolved, HomeStart.EMPTY, compact = true)

        assertEquals(listOf("Phone"), seeded.dock.items.map { (it as AppShortcutItem).label })
    }

    @Test
    fun theDockTakesAtMostFourAndPrefersThePersonalProfile() {
        val work = app("Phone", pkg = phone.identity.packageName.value, profile = AppProfile.work())
        val five = roles + AppPackageName("pkg.notes")

        val apps = HomeSeeding.dockApps(listOf(work, phone, messages, browser, camera) + others, five)

        assertEquals(4, apps.size)
        assertEquals(phone.identity, apps.first().identity)
    }

    @Test
    fun theCompactChoiceDecidesHowFullTheSeededPagesAre() {
        val cells = fresh().pages.first().grid.cellCount
        val many = (1..cells).map { app("App%02d".format(it)) }

        val compact = HomeSeeding.seed(fresh(), many, emptyList(), HomeStart.ALL_APPS, compact = true)
        val roomy = HomeSeeding.seed(fresh(), many, emptyList(), HomeStart.ALL_APPS, compact = false)

        assertEquals(listOf(cells), compact.pages.map { it.items.size })
        assertEquals(listOf(cells * 3 / 4, cells - cells * 3 / 4), roomy.pages.map { it.items.size })
    }

    @Test
    fun aHomeThatIsNotFreshIsNeverSeededAndSeedingTwiceChangesNothing() {
        val once = HomeSeeding.seed(fresh(), installed, roles, HomeStart.ALL_APPS, compact = true)

        assertSame(once, HomeSeeding.seed(once, installed + app("New"), roles, HomeStart.ALL_APPS, compact = true))
    }

    @Test
    fun aRemovedAppIsNotReAddedBecauseNothingRunsTheSeedAgain() {
        val once = HomeSeeding.seed(fresh(), installed, roles, HomeStart.ALL_APPS, compact = true)
        val removed = once.copy(pages = once.pages.map { page -> page.copy(items = page.items.drop(1)) })

        val again = HomeSeeding.seed(removed, installed, roles, HomeStart.ALL_APPS, compact = true)

        assertEquals(removed, again)
    }
}
