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
 * [com.riffle.core.domain.launcher.home.DockModel.notificationSlotCount] cap, drawn in full
 * whenever the run has room, never padded out beyond how many entries actually exist. The static
 * side is sized from its own [com.riffle.core.domain.launcher.home.DockModel.capacity] cap
 * afterwards, from whatever room the dynamic section has left -- notifications go first, and the
 * static side scrolls the rest away rather than the other way around. These pin that arithmetic.
 */
class DockDynamicSectionMetricsTest {
    @Test
    fun dynamicSectionTakesWhatTheEntriesNeedWhenTheRunHasRoom() {
        // Four entries at a 44dp icon and 12dp spacing: 4*44 + 3*12.
        assertEquals(
            212,
            dockDynamicSectionMainAxisDp(
                entryCount = 4,
                notificationSlotCount = 5,
                entryExtentDp = 44,
                entrySpacingDp = 12,
                maxRunMainAxisDp = 560,
            ),
        )
    }

    @Test
    fun dynamicSectionShrinksToFewerEntriesThanTheSlotCountAllows() {
        // Only two entries even though the slot count allows five: 2*44 + 1*12.
        assertEquals(
            100,
            dockDynamicSectionMainAxisDp(
                entryCount = 2,
                notificationSlotCount = 5,
                entryExtentDp = 44,
                entrySpacingDp = 12,
                maxRunMainAxisDp = 560,
            ),
        )
    }

    @Test
    fun dynamicSectionNeverExceedsItsOwnSlotCountRegardlessOfHowManyEntriesThereAre() {
        // Five entries but a slot count of two: 2*44 + 1*12, the rest scrolls (DockDynamicSection).
        assertEquals(
            100,
            dockDynamicSectionMainAxisDp(
                entryCount = 5,
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
                entryCount = 12,
                notificationSlotCount = 12,
                entryExtentDp = 44,
                entrySpacingDp = 12,
                maxRunMainAxisDp = 200,
            ),
        )
    }

    @Test
    fun noEntriesMeansNoSection() {
        assertEquals(
            0,
            dockDynamicSectionMainAxisDp(
                entryCount = 0,
                notificationSlotCount = 3,
                entryExtentDp = 44,
                entrySpacingDp = 12,
                maxRunMainAxisDp = 560,
            ),
        )
    }

    @Test
    fun aZeroSlotCountMeansNoSectionEvenWithEntries() {
        assertEquals(
            0,
            dockDynamicSectionMainAxisDp(
                entryCount = 3,
                notificationSlotCount = 0,
                entryExtentDp = 44,
                entrySpacingDp = 12,
                maxRunMainAxisDp = 560,
            ),
        )
    }

    @Test
    fun theStaticSideIsCappedToWhatTheDynamicSectionLeaves() {
        // A 560dp run, a dynamic section that draws in full at 212dp (4 entries, 44dp/12dp) plus the
        // 17dp rule leaves 331dp for the static side -- less than capacity alone would draw for 10
        // icons at this icon size (10*44 + 9*12 = 548dp), so the static side is capped, not the
        // dynamic one.
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
                dynamicEntryCount = 4,
            )

        assertEquals(212, surfaceMetrics?.dynamicSectionMainAxisDp)
        assertEquals(331, surfaceMetrics?.containerMainAxisDp)
    }

    @Test
    fun aDockWithNoNotificationsSizesTheStaticSideAsIfTheDynamicSectionDidNotExist() {
        val withNotifications =
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
                dynamicEntryCount = 0,
            )
        val withoutDynamicSection =
            dockSurfaceMetrics(
                dock =
                    DockModel(
                        capacity = 10,
                        items = List(10) { index -> testDockShortcut("app-$index") },
                        notificationSlotCount = 0,
                        iconSizeDp = 44,
                        itemSpacingDp = 12,
                    ),
                isEditing = false,
                availableMainAxisDp = 560,
                runsHorizontally = true,
                dynamicEntryCount = 0,
            )

        assertEquals(0, withNotifications?.dynamicSectionMainAxisDp)
        assertEquals(withoutDynamicSection?.containerMainAxisDp, withNotifications?.containerMainAxisDp)
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
    fun stripWithNoEntriesDoesNotDrawTheDynamicSection() {
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

        assertEquals(false, dockSurfaceStripShowsDynamicSection(metrics, dynamicEntries = emptyList()))
        assertEquals(240, dockSurfaceStripMainAxisDp(metrics, showDynamicSection = false))
    }

    @Test
    fun stripWithEntriesDrawsTheDynamicSectionAtTheFullSurfaceWidth() {
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
        val entries =
            listOf(
                DockDynamicEntry(
                    key = "app",
                    label = "App",
                    identity = null,
                    badgeCount = 1,
                    isSelected = false,
                    contentDescription = "App, 1 notification",
                    intent = null,
                ),
            )

        assertEquals(true, dockSurfaceStripShowsDynamicSection(metrics, dynamicEntries = entries))
        assertEquals(
            metrics.surfaceMainAxisDp,
            dockSurfaceStripMainAxisDp(metrics, showDynamicSection = true),
        )
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
