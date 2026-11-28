package com.riffle.app.launcher.summon

import com.riffle.app.launcher.LauncherHapticEvent
import com.riffle.app.launcher.LauncherShellAction
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
import com.riffle.core.domain.launcher.summon.RingPoint
import com.riffle.core.domain.launcher.summon.SummonEffect
import com.riffle.core.domain.launcher.summon.SummonSettings
import com.riffle.core.domain.launcher.summon.SummonSlot
import com.riffle.core.domain.launcher.summon.SummonSlotRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SummonEffectsTest {
    private val maps = AppIdentity(AppPackageName("pkg.maps"), AppActivityName("pkg.maps.Main"))
    private val phone = AppIdentity(AppPackageName("pkg.phone"), AppActivityName("pkg.phone.Main"))

    private val model =
        SummonRingModel.from(
            SummonSettings(
                slots =
                    listOf(
                        SummonSlot.App(maps),
                        SummonSlot.Glance(GlanceWidgetKind.NOW_PLAYING),
                        SummonSlot.Favourites,
                    ),
            ),
            HomeLayout(
                viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                pages = listOf(LauncherPage(LauncherPageId("p0"), grid = GridDimensions(4, 5))),
                selectedPageId = LauncherPageId("p0"),
                dock = DockModel(capacity = 4, items = listOf(AppShortcutItem(LauncherItemId("d"), phone, "Phone"))),
            ),
            listOf(InstalledApp(maps, "Maps"), InstalledApp(phone, "Phone")),
            glanceLabel = { "Now playing" },
            favouritesLabel = "Favourites",
        )

    @Test
    fun launchingAnAppOrAFavouriteStartsThatApp() {
        assertEquals(LauncherShellAction.LaunchApp(maps), summonActionFor(SummonEffect.Launch(emptyList(), 0), model))
        assertEquals(LauncherShellAction.LaunchApp(phone), summonActionFor(SummonEffect.LaunchFavourite(0), model))
    }

    @Test
    fun releasingOnAGlanceOpensTheStream() {
        assertEquals(LauncherShellAction.OpenStream, summonActionFor(SummonEffect.Launch(emptyList(), 1), model))
    }

    @Test
    fun aSwipeBeforeTheRingArmsOpensSearchDownwardsAndTheLibraryUpwards() {
        val from = RingPoint(500f, 1000f)
        assertEquals(
            LauncherShellAction.OpenSearch,
            summonActionFor(SummonEffect.PassedThrough(from, RingPoint(510f, 1200f)), model),
        )
        assertEquals(
            LauncherShellAction.OpenAppDrawer,
            summonActionFor(SummonEffect.PassedThrough(from, RingPoint(490f, 800f)), model),
        )
        assertNull(summonActionFor(SummonEffect.PassedThrough(from, RingPoint(800f, 1010f)), model))
    }

    @Test
    fun everythingElseLaunchesNothing() {
        listOf(
            SummonEffect.Armed,
            SummonEffect.Cancelled,
            SummonEffect.MenuRequested,
            // A kept card is shown by Home itself, not launched.
            SummonEffect.KeepCard(emptyList(), 1),
            SummonEffect.ReleasedOn(emptyList(), 2, SummonSlotRole.FAVOURITES),
            SummonEffect.Launch(emptyList(), 9),
            SummonEffect.LaunchFavourite(9),
        ).forEach { assertNull(summonActionFor(it, model)) }
    }

    @Test
    fun hapticsFollowTheGrammarAndCancellingIsSilent() {
        assertEquals(LauncherHapticEvent.GESTURE_START, summonHapticFor(SummonEffect.Armed))
        assertEquals(LauncherHapticEvent.DETENT, summonHapticFor(SummonEffect.Aimed(emptyList(), 0)))
        assertEquals(LauncherHapticEvent.DETENT, summonHapticFor(SummonEffect.AimedDock(0)))
        assertEquals(LauncherHapticEvent.BOUNDARY, summonHapticFor(SummonEffect.OpenedFolder(listOf(1))))
        assertEquals(LauncherHapticEvent.BOUNDARY, summonHapticFor(SummonEffect.WentBack(emptyList())))
        assertEquals(LauncherHapticEvent.BOUNDARY, summonHapticFor(SummonEffect.ReachedReadout(emptyList(), 1)))
        assertEquals(LauncherHapticEvent.COMMIT, summonHapticFor(SummonEffect.Launch(emptyList(), 0)))
        assertEquals(LauncherHapticEvent.COMMIT, summonHapticFor(SummonEffect.LaunchFavourite(0)))
        assertNull(summonHapticFor(SummonEffect.Cancelled))
    }
}
