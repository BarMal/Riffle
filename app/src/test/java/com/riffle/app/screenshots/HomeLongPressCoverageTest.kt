package com.riffle.app.screenshots

import com.riffle.app.launcher.HOME_DOCK_SURFACE_TEST_TAG
import com.riffle.core.domain.launcher.home.DockPosition
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Home's placed items, an empty page, a generated page's "More apps" and the Dock's body: folded and unfolded, with
 * a bottom and a side Dock.
 */
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class HomeLongPressCoverageTest : LongPressCoverageBase() {
    @Test
    fun homeFoldedBottomDock() {
        show(folded(DockPosition.BOTTOM))
        assertNoDeadPoint("home folded, bottom Dock", probe())
    }

    @Test
    fun homeFoldedSideDock() {
        show(folded(DockPosition.RIGHT))
        assertNoDeadPoint("home folded, right Dock", probe())
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun homeUnfoldedRightDock() {
        show(unfolded())
        assertNoDeadPoint("home unfolded, right Dock", probe())
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun homeUnfoldedBottomDock() {
        show(unfolded(DockPosition.BOTTOM))
        assertNoDeadPoint("home unfolded, bottom Dock", probe())
    }

    @Test
    fun anEmptyPageFolded() {
        show(folded(DockPosition.BOTTOM).onTheSecondPage())
        assertNoDeadPoint("an empty page, folded", probe())
    }

    @Test
    fun aGeneratedPageWithMoreAppsFolded() {
        show(folded(DockPosition.BOTTOM).withAGeneratedPage())
        assertNoDeadPoint("a generated page with More apps, folded", probe())
    }

    @Test
    fun theDockBodyBetweenItsIcons() {
        show(folded(DockPosition.BOTTOM))
        val result = composeRule.probeDockBody(haptics, HOME_DOCK_SURFACE_TEST_TAG)
        assertNoDeadPoint("the Dock's body between its icons, folded bottom", result)
    }
}
