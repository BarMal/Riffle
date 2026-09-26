package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Test

private fun testDockShortcut(id: String): AppShortcutItem =
    AppShortcutItem(
        id = LauncherItemId(id),
        appIdentity =
            AppIdentity(
                packageName = AppPackageName("com.example.$id"),
                activityName = AppActivityName(".MainActivity"),
            ),
        label = id,
    )

/**
 * How the dock's run is divided between its two sections.
 *
 * The dynamic section is sized from its own
 * [com.riffle.core.domain.launcher.home.DockModel.notificationSlotCount] cap alone -- a fixed
 * budget, drawn in full whenever the run has room, regardless of how many entries a mode actually
 * has to show there right now (see that property's own KDoc). The static side is sized from its
 * own [com.riffle.core.domain.launcher.home.DockModel.capacity] cap afterwards, from whatever room
 * the dynamic section has left -- notifications go first, and the static side scrolls the rest away
 * rather than the other way around. These pin that arithmetic.
 */
class DockDynamicSectionMetricsTest {
    @Test
    fun dynamicSectionAlwaysTakesTheFullSlotCountWhenTheRunHasRoom() {
        // A slot count of five at a 44dp icon and 12dp spacing: 5*44 + 4*12.
        assertEquals(
            268,
            dockDynamicSectionMainAxisDp(
                notificationSlotCount = 5,
                entryExtentDp = 44,
                entrySpacingDp = 12,
                maxRunMainAxisDp = 560,
            ),
        )
    }

    @Test
    fun dynamicSectionDoesNotShrinkForASmallerSlotCount() {
        // A slot count of two: 2*44 + 1*12, whatever the run could otherwise fit.
        assertEquals(
            100,
            dockDynamicSectionMainAxisDp(
                notificationSlotCount = 2,
                entryExtentDp = 44,
                entrySpacingDp = 12,
                maxRunMainAxisDp = 560,
            ),
        )
    }

    @Test
    fun dynamicSectionTakesOnlyWhatTheRunHasRoomForOnATightScreen() {
        // 200 of run, 17 for the rule between the sections: the section draws in full first and is
        // simply clipped to what is left, however little that leaves the static side.
        assertEquals(
            183,
            dockDynamicSectionMainAxisDp(
                notificationSlotCount = 12,
                entryExtentDp = 44,
                entrySpacingDp = 12,
                maxRunMainAxisDp = 200,
            ),
        )
    }

    @Test
    fun aZeroSlotCountMeansNoSection() {
        assertEquals(
            0,
            dockDynamicSectionMainAxisDp(
                notificationSlotCount = 0,
                entryExtentDp = 44,
                entrySpacingDp = 12,
                maxRunMainAxisDp = 560,
            ),
        )
    }

    @Test
    fun theStaticSideIsCappedToWhatTheDynamicSectionLeaves() {
        // A 560dp run, a dynamic section that draws in full at 212dp (a 4-icon slot count, 44dp/12dp)
        // plus the 17dp rule leaves 331dp for the static side -- less than capacity alone would draw
        // for 10 icons at this icon size (10*44 + 9*12 = 548dp), so the static side is capped, not
        // the dynamic one.
        val surfaceMetrics =
            dockSurfaceMetrics(
                dock =
                    DockModel(
                        capacity = 10,
                        items = List(10) { index -> testDockShortcut("app-$index") },
                        notificationSlotCount = 4,
                        iconSizeDp = 44,
                        itemSpacingDp = 12,
                    ),
                isEditing = false,
                availableMainAxisDp = 560,
                runsHorizontally = true,
            )

        assertEquals(212, surfaceMetrics?.dynamicSectionMainAxisDp)
        assertEquals(331, surfaceMetrics?.containerMainAxisDp)
    }

    @Test
    fun theDynamicSectionNeverDisplacesTheStaticSideEntirely() {
        // A 200dp run, one pinned icon, and a notification slot count demanding far more than fits
        // (12 icons at 44dp/12dp wants 660dp). Without a floor the dynamic section would claim
        // everything past its own 17dp rule (183dp), leaving the static side 0dp -- not scrolled,
        // gone. The floor reserves one icon's worth (44 + 2*14 = 72dp) for the static side first, so
        // the dynamic section is capped to 111dp (200 - 72 - 17) instead, and the pinned icon still
        // draws.
        val surfaceMetrics =
            dockSurfaceMetrics(
                dock =
                    DockModel(
                        capacity = 1,
                        items = listOf(testDockShortcut("app-0")),
                        notificationSlotCount = 12,
                        iconSizeDp = 44,
                        itemSpacingDp = 12,
                    ),
                isEditing = false,
                availableMainAxisDp = 200,
                runsHorizontally = true,
            )

        assertEquals(111, surfaceMetrics?.dynamicSectionMainAxisDp)
        assertEquals(72, surfaceMetrics?.containerMainAxisDp)
        assertEquals(true, (surfaceMetrics?.contentViewportMainAxisDp ?: 0) > 0)
    }

    @Test
    fun aDockWithNoDynamicSectionRunsExactlyAsLongAsItsStaticSide() {
        // The invariant every caller that predates the section relies on.
        val metrics =
            DockSurfaceMetrics(
                renderedSlotCount = 4,
                containerMainAxisDp = 240,
                contentViewportMainAxisDp = 212,
                slotMetrics =
                    dockSlotRenderMetrics(
                        slotCount = 4,
                        iconSizeDp = 44,
                        itemSpacingDp = 12,
                        availableContentMainAxisDp = 212,
                    ),
            )

        assertEquals(240, metrics.surfaceMainAxisDp)
    }

    @Test
    fun stripWithNoBudgetDoesNotDrawTheDynamicSection() {
        val metrics =
            DockSurfaceMetrics(
                renderedSlotCount = 4,
                containerMainAxisDp = 240,
                contentViewportMainAxisDp = 212,
                slotMetrics =
                    dockSlotRenderMetrics(
                        slotCount = 4,
                        iconSizeDp = 44,
                        itemSpacingDp = 12,
                        availableContentMainAxisDp = 212,
                    ),
                dynamicSectionMainAxisDp = 0,
            )

        assertEquals(false, dockSurfaceStripShowsDynamicSection(metrics))
        assertEquals(240, dockSurfaceStripMainAxisDp(metrics, showDynamicSection = false))
    }

    @Test
    fun stripWithBudgetDrawsTheDynamicSectionAtTheFullSurfaceWidthWhetherOrNotEntriesExistRightNow() {
        // The section is reserved from the budget alone -- no entries needed, and no entries taken
        // away either -- so a mode with nothing waiting right now still draws (and anchors) the same
        // full-width section a busy mode does.
        val metrics =
            DockSurfaceMetrics(
                renderedSlotCount = 4,
                containerMainAxisDp = 240,
                contentViewportMainAxisDp = 212,
                slotMetrics =
                    dockSlotRenderMetrics(
                        slotCount = 4,
                        iconSizeDp = 44,
                        itemSpacingDp = 12,
                        availableContentMainAxisDp = 212,
                    ),
                dynamicSectionMainAxisDp = 61,
            )

        assertEquals(true, dockSurfaceStripShowsDynamicSection(metrics))
        assertEquals(
            metrics.surfaceMainAxisDp,
            dockSurfaceStripMainAxisDp(metrics, showDynamicSection = true),
        )
    }

    @Test
    fun aCallerWithItsOwnShelfContentNeverDrawsTheStripsSection() {
        // ExpandedDockSurface's own case: the shelf's card row already shows the entries, so its
        // strip must not draw the section itself however wide the budget is -- only reserve room for
        // it (via containerMainAxisDp, computed the same way whether or not the strip draws it).
        val metrics =
            DockSurfaceMetrics(
                renderedSlotCount = 4,
                containerMainAxisDp = 240,
                contentViewportMainAxisDp = 212,
                slotMetrics =
                    dockSlotRenderMetrics(
                        slotCount = 4,
                        iconSizeDp = 44,
                        itemSpacingDp = 12,
                        availableContentMainAxisDp = 212,
                    ),
                dynamicSectionMainAxisDp = 61,
            )

        assertEquals(false, dockSurfaceStripShowsDynamicSection(metrics, drawsDynamicSection = false))
    }

    @Test
    fun aDockWithADynamicSectionRunsLongEnoughForBothAndTheRuleBetween() {
        val metrics =
            DockSurfaceMetrics(
                renderedSlotCount = 4,
                containerMainAxisDp = 240,
                contentViewportMainAxisDp = 212,
                slotMetrics =
                    dockSlotRenderMetrics(
                        slotCount = 4,
                        iconSizeDp = 44,
                        itemSpacingDp = 12,
                        availableContentMainAxisDp = 212,
                    ),
                dynamicSectionMainAxisDp = 100,
            )

        assertEquals(240 + 17 + 100, metrics.surfaceMainAxisDp)
    }
}
