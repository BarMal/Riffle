package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LayoutSettingsTest {
    // Postures

    @Test
    fun phonesAreFoldedAndEveryWideClassIsUnfolded() {
        assertEquals(LayoutPosture.FOLDED, HomeLayoutDeviceClass.PHONE.layoutPosture())
        assertEquals(LayoutPosture.FOLDED, HomeLayoutDeviceClass.PHONE_LANDSCAPE.layoutPosture())
        assertEquals(LayoutPosture.UNFOLDED, HomeLayoutDeviceClass.FOLDABLE.layoutPosture())
        assertEquals(LayoutPosture.UNFOLDED, HomeLayoutDeviceClass.TABLET.layoutPosture())
        assertEquals(LayoutPosture.UNFOLDED, HomeLayoutDeviceClass.DESKTOP.layoutPosture())
    }

    // Defaults (ADR 0003, Amendment 2)

    @Test
    fun foldedDefaultsToSidewaysPagesWithTheIndicatorAndQuickScrollOn() {
        val folded = LayoutSettings().folded
        assertEquals(PageScrollDirection.HORIZONTAL, folded.pageScroll)
        assertEquals(PageIndicatorPlacement.BESIDE_DOCK, folded.indicator)
        assertTrue(folded.quickScroll)
    }

    @Test
    fun unfoldedDefaultsToUpAndDownPagesWithTheIndicatorAndQuickScrollOn() {
        val unfolded = LayoutSettings().unfolded
        assertEquals(PageScrollDirection.VERTICAL, unfolded.pageScroll)
        assertEquals(PageIndicatorPlacement.BESIDE_DOCK, unfolded.indicator)
        assertTrue(unfolded.quickScroll)
    }

    @Test
    fun theDockEdgeDefaultsAreBottomFoldedAndRightOnAnUnfoldedFoldable() {
        assertEquals(DockPosition.BOTTOM, HomeLayoutDeviceClass.PHONE.defaultDockEdge)
        assertEquals(DockPosition.BOTTOM, HomeLayoutDeviceClass.PHONE_LANDSCAPE.defaultDockEdge)
        assertEquals(DockPosition.RIGHT, HomeLayoutDeviceClass.FOLDABLE.defaultDockEdge)
        // A tablet and a desktop window keep the left rail they always had.
        assertEquals(DockPosition.LEFT, HomeLayoutDeviceClass.TABLET.defaultDockEdge)
        assertEquals(DockPosition.LEFT, HomeLayoutDeviceClass.DESKTOP.defaultDockEdge)
    }

    @Test
    fun aDeviceClassReadsItsPosturesSettings() {
        val settings =
            LayoutSettings()
                .withPageScroll(LayoutPosture.FOLDED, PageScrollDirection.VERTICAL)
                .withQuickScroll(LayoutPosture.UNFOLDED, false)
        assertEquals(PageScrollDirection.VERTICAL, settings.forDeviceClass(HomeLayoutDeviceClass.PHONE).pageScroll)
        assertEquals(
            PageScrollDirection.VERTICAL,
            settings.forDeviceClass(HomeLayoutDeviceClass.PHONE_LANDSCAPE).pageScroll,
        )
        assertFalse(settings.forDeviceClass(HomeLayoutDeviceClass.FOLDABLE).quickScroll)
        assertFalse(settings.forDeviceClass(HomeLayoutDeviceClass.TABLET).quickScroll)
    }

    // Independence

    @Test
    fun eachPostureIsChangedOnItsOwn() {
        val start = LayoutSettings()
        val scrolled = start.withPageScroll(LayoutPosture.UNFOLDED, PageScrollDirection.HORIZONTAL)
        assertEquals(PageScrollDirection.HORIZONTAL, scrolled.unfolded.pageScroll)
        assertEquals(start.folded, scrolled.folded)

        val hidden = start.withIndicator(LayoutPosture.FOLDED, PageIndicatorPlacement.OFF)
        assertEquals(PageIndicatorPlacement.OFF, hidden.folded.indicator)
        assertEquals(start.unfolded, hidden.unfolded)

        val still = start.withQuickScroll(LayoutPosture.FOLDED, false)
        assertFalse(still.folded.quickScroll)
        assertTrue(still.unfolded.quickScroll)
    }

    // What the indicator does

    @Test
    fun offDrawsNoDotsAndSoDragsNothing() {
        val off = PostureLayoutSettings.FOLDED_DEFAULT.copy(indicator = PageIndicatorPlacement.OFF)
        assertFalse(off.showsDots)
        assertFalse(off.dragsToScroll)
    }

    @Test
    fun withQuickScrollOffTheDotsOnlyShowWhereYouAre() {
        val still = PostureLayoutSettings.FOLDED_DEFAULT.copy(quickScroll = false)
        assertTrue(still.showsDots)
        assertFalse(still.dragsToScroll)
        assertTrue(PostureLayoutSettings.FOLDED_DEFAULT.dragsToScroll)
    }

    // Which device class Settings edits for a posture's Dock edge

    @Test
    fun theDockEdgeIsEditedOnThePosturesOwnClassOrTheOneBeingHeldInIt() {
        assertEquals(
            HomeLayoutDeviceClass.PHONE,
            LayoutPosture.FOLDED.dockDeviceClass(active = HomeLayoutDeviceClass.FOLDABLE),
        )
        assertEquals(
            HomeLayoutDeviceClass.FOLDABLE,
            LayoutPosture.UNFOLDED.dockDeviceClass(active = HomeLayoutDeviceClass.PHONE),
        )
        assertEquals(
            HomeLayoutDeviceClass.PHONE_LANDSCAPE,
            LayoutPosture.FOLDED.dockDeviceClass(active = HomeLayoutDeviceClass.PHONE_LANDSCAPE),
        )
        assertEquals(
            HomeLayoutDeviceClass.TABLET,
            LayoutPosture.UNFOLDED.dockDeviceClass(active = HomeLayoutDeviceClass.TABLET),
        )
    }

    // Stored ids

    @Test
    fun storedIdsAreAContractAndUnknownOnesAreNull() {
        assertEquals("horizontal", PageScrollDirection.HORIZONTAL.storedId)
        assertEquals("vertical", PageScrollDirection.VERTICAL.storedId)
        assertEquals("beside_dock", PageIndicatorPlacement.BESIDE_DOCK.storedId)
        assertEquals("off", PageIndicatorPlacement.OFF.storedId)
        PageScrollDirection.entries.forEach { assertEquals(it, PageScrollDirection.fromStoredId(it.storedId)) }
        PageIndicatorPlacement.entries.forEach { assertEquals(it, PageIndicatorPlacement.fromStoredId(it.storedId)) }
        assertNull(PageScrollDirection.fromStoredId("diagonal"))
        assertNull(PageScrollDirection.fromStoredId(null))
        assertNull(PageIndicatorPlacement.fromStoredId("in_the_dock"))
    }

    // App peek-through (Amendment 2, U2)

    @Test
    fun appsBehindTheCardsDefaultsToHiddenFoldedAndBehindAndAroundUnfoldedAndIsSetPerPosture() {
        assertEquals(AppsBehindCards.HIDDEN, LayoutSettings().folded.appsBehind)
        assertEquals(AppsBehindCards.BEHIND_AND_AROUND, LayoutSettings().unfolded.appsBehind)
        val around = LayoutSettings().withAppsBehind(LayoutPosture.UNFOLDED, AppsBehindCards.AROUND)
        assertEquals(AppsBehindCards.AROUND, around.unfolded.appsBehind)
        assertEquals(AppsBehindCards.HIDDEN, around.folded.appsBehind)
        assertEquals(PageScrollDirection.VERTICAL, around.unfolded.pageScroll)
        val foldedOn = LayoutSettings().withAppsBehind(LayoutPosture.FOLDED, AppsBehindCards.BEHIND_AND_AROUND)
        assertEquals(AppsBehindCards.BEHIND_AND_AROUND, foldedOn.folded.appsBehind)
        assertEquals(PageScrollDirection.HORIZONTAL, foldedOn.folded.pageScroll)
    }

    @Test
    fun homeStaysBehindTheStreamForTwoOfTheThreeChoices() {
        fun posture(choice: AppsBehindCards) =
            PostureLayoutSettings(PageScrollDirection.HORIZONTAL, appsBehind = choice)
        assertFalse(posture(AppsBehindCards.HIDDEN).appPeekThrough)
        assertTrue(posture(AppsBehindCards.AROUND).appPeekThrough)
        assertTrue(PostureLayoutSettings(PageScrollDirection.HORIZONTAL).appPeekThrough)
    }

    @Test
    fun aStoredBooleanReadsAsTheOldBehaviourAndAnIdAsItself() {
        assertEquals(AppsBehindCards.BEHIND_AND_AROUND, AppsBehindCards.fromStored(true))
        assertEquals(AppsBehindCards.HIDDEN, AppsBehindCards.fromStored(false))
        assertEquals(AppsBehindCards.AROUND, AppsBehindCards.fromStored("around"))
        assertEquals(null, AppsBehindCards.fromStored("nonsense"))
        assertEquals(null, AppsBehindCards.fromStored(3))
        AppsBehindCards.entries.forEach { assertEquals(it, AppsBehindCards.fromStored(it.storedId)) }
    }
}
