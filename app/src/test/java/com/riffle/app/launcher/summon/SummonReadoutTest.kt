package com.riffle.app.launcher.summon

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.summon.RingLayout
import com.riffle.core.domain.launcher.summon.RingPoint
import com.riffle.core.domain.launcher.summon.SummonPhase
import com.riffle.core.domain.launcher.summon.SummonSettings
import com.riffle.core.domain.launcher.summon.SummonSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SummonReadoutTest {
    private fun app(name: String) = AppIdentity(AppPackageName("pkg.$name"), AppActivityName("pkg.$name.Main"))

    private val mail = app("mail")
    private val maps = app("maps")
    private val phone = app("phone")

    private fun note(key: String) =
        LauncherNotification(
            key = LauncherNotificationKey(key),
            packageName = mail.packageName,
            title = "title $key",
            text = "text $key",
            postedAtEpochMillis = 0L,
        )

    private val model =
        SummonRingModel.from(
            settings =
                SummonSettings(
                    slots =
                        listOf(
                            SummonSlot.App(mail),
                            SummonSlot.App(maps),
                            SummonSlot.Glance(GlanceWidgetKind.NOW_PLAYING),
                            SummonSlot.Favourites,
                        ),
                ),
            layout =
                HomeLayout(
                    viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                    pages = listOf(LauncherPage(LauncherPageId("p0"), grid = GridDimensions(4, 5))),
                    selectedPageId = LauncherPageId("p0"),
                    dock =
                        DockModel(
                            capacity = 4,
                            items = listOf(AppShortcutItem(LauncherItemId("d"), phone, "Phone")),
                        ),
                ),
            installed = listOf(InstalledApp(mail, "Mail"), InstalledApp(maps, "Maps"), InstalledApp(phone, "Phone")),
            glanceLabel = { "Now playing" },
            favouritesLabel = "Favourites",
            notifications =
                listOf(
                    AppNotificationGroup(
                        packageName = mail.packageName,
                        profileId = mail.profile.id,
                        latestCategory = NotificationCategory.UNKNOWN,
                        latestAgeBucket = NotificationAgeBucket.entries.first(),
                        notifications = listOf(note("a"), note("b"), note("c"), note("d")),
                    ),
                ),
        )

    private fun open(
        aim: Int? = null,
        live: Boolean = false,
        favouritesOpen: Boolean = false,
        dockAim: Int? = null,
        centreY: Float = 1500f,
    ) = SummonPhase.Open(
        origin = RingPoint(500f, centreY),
        path = emptyList(),
        layout = RingLayout(RingPoint(500f, centreY), 300f, listOf(-90f, 0f, 90f, 180f), 90f, true),
        finger = RingPoint(500f, centreY),
        aim = aim,
        live = live,
        favouritesOpen = favouritesOpen,
        dockAim = dockAim,
    )

    @Test
    fun nothingAimedMeansNoReadout() {
        assertNull(summonReadout(open(), model))
    }

    @Test
    fun anAppsReadoutListsItsNewestNotificationsAndSaysItCanBeKept() {
        val readout = summonReadout(open(aim = 0), model)!!
        assertEquals("Mail", readout.entry.label)
        assertEquals(listOf("a", "b", "c"), readout.notifications.map { it.key.value })
        assertEquals(4, readout.notificationCount)
        assertEquals(SummonReadoutHint.OPEN_OR_KEEP, readout.hint)
    }

    @Test
    fun pushedThroughItSaysReleaseKeepsIt() {
        assertEquals(SummonReadoutHint.RELEASE_TO_KEEP, summonReadout(open(aim = 0, live = true), model)!!.hint)
    }

    @Test
    fun anAppWithNothingNewHasNoRows() {
        val readout = summonReadout(open(aim = 1), model)!!
        assertEquals("Maps", readout.entry.label)
        assertTrue(readout.notifications.isEmpty())
        assertEquals(SummonReadoutHint.RELEASE_TO_OPEN, readout.hint)
    }

    @Test
    fun aimingAtAFavouriteReadsThatApp() {
        val readout = summonReadout(open(aim = 3, favouritesOpen = true, dockAim = 0), model)!!
        assertEquals("Phone", readout.entry.label)
        assertEquals(SummonReadoutHint.RELEASE_TO_OPEN, readout.hint)
    }

    @Test
    fun favouritesAndGlancesSayWhatReleasingDoes() {
        assertEquals(SummonReadoutHint.SHOW_FAVOURITES, summonReadout(open(aim = 3), model)!!.hint)
        assertEquals(SummonReadoutHint.OPEN_OR_KEEP, summonReadout(open(aim = 2), model)!!.hint)
    }

    @Test
    fun theReadoutSitsAtTheTopUnlessTheRingIsHighOnTheScreen() {
        assertEquals(SummonReadoutPlacement.TOP, summonReadoutPlacement(ringCentreY = 1500f, height = 2000f))
        assertEquals(SummonReadoutPlacement.BOTTOM, summonReadoutPlacement(ringCentreY = 600f, height = 2000f))
    }

    @Test
    fun theGlowFadesWithDistanceFromTheLitEdge() {
        val near = summonReadoutGlow(distanceDp = 0f)
        val mid = summonReadoutGlow(distanceDp = 150f)
        val far = summonReadoutGlow(distanceDp = 1000f)
        assertEquals(1f, near)
        assertTrue(mid in 0.01f..0.99f)
        assertEquals(0f, far)
    }
}
