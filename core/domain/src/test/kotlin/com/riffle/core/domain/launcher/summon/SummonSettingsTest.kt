package com.riffle.core.domain.launcher.summon

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.home.LauncherItemId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SummonSettingsTest {
    private fun app(name: String) = AppIdentity(AppPackageName("pkg.$name"), AppActivityName("pkg.$name.Main"))

    private fun appSlot(name: String) = SummonSlot.App(app(name))

    @Test
    fun defaultsAreAFullUnseededRingWithClockAndDateAndTheTestedTimings() {
        val settings = SummonSettings()
        assertEquals(8, settings.slotCount)
        assertTrue(settings.slots.isEmpty())
        assertEquals(SummonHeader.ClockAndDate, settings.header)
        assertEquals(
            SummonTimings(armMillis = 200, previewMillis = 350, acceptMillis = 450, slideMillis = 600),
            settings.timings,
        )
    }

    @Test
    fun seedingFillsTheRingFromTheGivenAppsWithFavouritesAtTheBottom() {
        val apps = (1..10).map { app("a$it") }
        val seeded = SummonSettings().seeded(apps)
        assertEquals(8, seeded.slots.size)
        // Eight positions clockwise from the top: index 4 is straight down.
        assertEquals(SummonSlot.Favourites, seeded.slots[4])
        assertEquals((1..7).map { SummonSlot.App(app("a$it")) }, seeded.slots.filterIsInstance<SummonSlot.App>())
    }

    @Test
    fun seedingSkipsDuplicateAppsAndCopesWithFewApps() {
        val seeded = SummonSettings(slotCount = 6).seeded(listOf(app("a"), app("a"), app("b")))
        assertEquals(listOf(appSlot("a"), appSlot("b"), SummonSlot.Favourites), seeded.slots)
    }

    @Test
    fun seedingNeverReplacesSlotsTheUserAlreadyHas() {
        val mine = SummonSettings(slots = listOf(appSlot("mine")))
        assertSame(mine, mine.seeded(listOf(app("other"))))
    }

    @Test
    fun theSlotCountIsClampedAndTheRingShowsOnlyThatMany() {
        assertEquals(MIN_SUMMON_SLOT_COUNT, SummonSettings().withSlotCount(1).slotCount)
        assertEquals(MAX_SUMMON_SLOT_COUNT, SummonSettings().withSlotCount(20).slotCount)
        val five = SummonSettings(slots = (1..8).map { appSlot("a$it") }).withSlotCount(5)
        assertEquals((1..5).map { appSlot("a$it") }, five.ringSlots)
        // Slots past the count are kept, so raising the count again brings them back.
        assertEquals(8, five.withSlotCount(8).ringSlots.size)
    }

    @Test
    fun movingASlotReordersTheRingAndIgnoresOutOfRangeIndices() {
        val settings = SummonSettings(slots = listOf(appSlot("a"), appSlot("b"), appSlot("c")))
        assertEquals(listOf(appSlot("c"), appSlot("a"), appSlot("b")), settings.moved(from = 2, to = 0).slots)
        assertEquals(listOf(appSlot("b"), appSlot("c"), appSlot("a")), settings.moved(from = 0, to = 2).slots)
        assertSame(settings, settings.moved(from = 5, to = 0))
        assertSame(settings, settings.moved(from = 0, to = -1))
    }

    @Test
    fun addingAppendsOnceAndRemovingDropsByIndex() {
        val settings = SummonSettings(slots = listOf(appSlot("a")))
        val added = settings.added(SummonSlot.Favourites)
        assertEquals(listOf(appSlot("a"), SummonSlot.Favourites), added.slots)
        assertSame(added, added.added(SummonSlot.Favourites))
        assertEquals(listOf(SummonSlot.Favourites), added.removed(0).slots)
        assertSame(added, added.removed(9))
    }

    @Test
    fun replacingWithASlotAlreadyOnTheRingSwapsTheTwo() {
        val settings = SummonSettings(slots = listOf(appSlot("a"), appSlot("b"), appSlot("c")))
        assertEquals(listOf(appSlot("a"), appSlot("x"), appSlot("c")), settings.replaced(1, appSlot("x")).slots)
        assertEquals(listOf(appSlot("c"), appSlot("b"), appSlot("a")), settings.replaced(0, appSlot("c")).slots)
        assertSame(settings, settings.replaced(7, appSlot("x")))
    }

    @Test
    fun everySlotKindCanSitOnTheRing() {
        val slots =
            listOf(
                appSlot("a"),
                SummonSlot.Folder(LauncherItemId("folder-1")),
                SummonSlot.Glance(GlanceWidgetKind.NOW_PLAYING),
                SummonSlot.Favourites,
            )
        assertEquals(slots, SummonSettings(slots = slots).ringSlots)
    }

    @Test
    fun timingsAreClampedToTheirRanges() {
        val wild = SummonTimings(armMillis = 1, previewMillis = 99_999, acceptMillis = 0, slideMillis = 5_000).clamped()
        assertEquals(SUMMON_ARM_MILLIS_RANGE.first, wild.armMillis)
        assertEquals(SUMMON_PREVIEW_MILLIS_RANGE.last, wild.previewMillis)
        assertEquals(SUMMON_ACCEPT_MILLIS_RANGE.first, wild.acceptMillis)
        assertEquals(SUMMON_SLIDE_MILLIS_RANGE.last, wild.slideMillis)
        assertEquals(SummonTimings(), SummonTimings().clamped())
    }

    @Test
    fun theHeaderCanHoldAClockAGlanceOrAWidgetPlacedOnHome() {
        val headers =
            listOf(
                SummonHeader.None,
                SummonHeader.Clock,
                SummonHeader.ClockAndDate,
                SummonHeader.Glance(GlanceWidgetKind.NEXT_EVENT),
                SummonHeader.HomeWidget(LauncherItemId("widget-7")),
            )
        headers.forEach { header -> assertEquals(header, SummonSettings().copy(header = header).header) }
    }

    @Test
    fun theIconSizeDefaultsToTheTestedSizeAndIsClampedToItsRange() {
        assertEquals(DEFAULT_SUMMON_ICON_SIZE_DP, SummonSettings().iconSizeDp)
        assertEquals(SUMMON_ICON_SIZE_DP_RANGE.first, SummonSettings().withIconSize(1).iconSizeDp)
        assertEquals(SUMMON_ICON_SIZE_DP_RANGE.last, SummonSettings().withIconSize(500).iconSizeDp)
        assertEquals(50, SummonSettings().withIconSize(50).iconSizeDp)
    }
}
