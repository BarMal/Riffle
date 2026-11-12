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
 * [com.riffle.core.domain.launcher.home.DockModel.liveAppSlots] cap (plus now playing and the Stream button), drawn
 * in full whenever the run has room, never padded out beyond how many entries actually exist. The static
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
                slotLimit = 5,
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
                slotLimit = 5,
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
                slotLimit = 2,
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
                slotLimit = 12,
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
                slotLimit = 3,
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
                slotLimit = 0,
                entryExtentDp = 44,
                entrySpacingDp = 12,
                maxRunMainAxisDp = 560,
            ),
        )
    }

    @Test
    fun theStaticSideIsCappedToWhatTheDynamicSectionLeaves() {
        // A 560dp run, a dynamic section that draws in full at 228dp (4 entries, 48dp/12dp) plus the
        // 17dp rule leaves 315dp for the static side -- less than capacity alone would draw for 10
        // icons at this icon size (10*48 + 9*12 = 588dp), so the static side is capped, not the
        // dynamic one.
        val surfaceMetrics =
            dockSurfaceMetrics(
                dock =
                    DockModel(
                        capacity = 10,
                        items = List(10) { index -> testDockShortcut("app-$index") },
                        liveAppSlots = 2,
                        iconSizeDp = 48,
                        itemSpacingDp = 12,
                    ),
                isEditing = false,
                availableMainAxisDp = 560,
                runsHorizontally = true,
                dynamicEntryCount = 4,
            )

        assertEquals(228, surfaceMetrics?.dynamicSectionMainAxisDp)
        assertEquals(315, surfaceMetrics?.containerMainAxisDp)
    }

    @Test
    fun theDynamicSectionNeverDisplacesTheStaticSideEntirely() {
        // A 200dp run, one pinned icon, and a notification section demanding far more than fits
        // (12 entries at 48dp/12dp wants 708dp). Without a floor the dynamic section would claim
        // everything past its own 17dp rule (183dp), leaving the static side 0dp -- not scrolled,
        // gone. The floor reserves one icon's worth (48 + 2*14 = 76dp) for the static side first, so
        // the dynamic section is capped to 107dp (200 - 76 - 17) instead, and the pinned icon still
        // draws.
        val surfaceMetrics =
            dockSurfaceMetrics(
                dock =
                    DockModel(
                        capacity = 1,
                        items = listOf(testDockShortcut("app-0")),
                        liveAppSlots = 10,
                        iconSizeDp = 48,
                        itemSpacingDp = 12,
                    ),
                isEditing = false,
                availableMainAxisDp = 200,
                runsHorizontally = true,
                dynamicEntryCount = 12,
            )

        assertEquals(107, surfaceMetrics?.dynamicSectionMainAxisDp)
        assertEquals(76, surfaceMetrics?.containerMainAxisDp)
        assertEquals(true, (surfaceMetrics?.contentViewportMainAxisDp ?: 0) > 0)
    }

    @Test
    fun aDockWithNoNotificationsSizesTheStaticSideAsIfTheDynamicSectionDidNotExist() {
        val withNotifications =
            dockSurfaceMetrics(
                dock =
                    DockModel(
                        capacity = 10,
                        items = List(10) { index -> testDockShortcut("app-$index") },
                        liveAppSlots = 2,
                        iconSizeDp = 48,
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
                        liveAppSlots = 0,
                        iconSizeDp = 48,
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
                        iconSizeDp = 48,
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
                        iconSizeDp = 48,
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
                        iconSizeDp = 48,
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
                        iconSizeDp = 48,
                        itemSpacingDp = 12,
                        availableContentMainAxisDp = 212,
                    ),
                dynamicSectionMainAxisDp = 100,
            )

        assertEquals(240 + 17 + 100, metrics.surfaceMainAxisDp)
    }

    private fun phoneDock(
        pins: Int,
        liveAppSlots: Int = 3,
    ) = DockModel(
        capacity = pins,
        items = List(pins) { index -> testDockShortcut("app-$index") },
        liveAppSlots = liveAppSlots,
        iconSizeDp = 56,
        itemSpacingDp = 10,
    )

    @Test
    fun fivePinsAtTheHomeIconSizeFitOnAPhoneAndDoNotScroll() {
        val metrics = dockSurfaceMetrics(phoneDock(pins = 5), isEditing = false, availableMainAxisDp = 404)!!

        assertEquals(56, metrics.slotMetrics.iconSizeDp)
        assertEquals(88, dockCrossAxisDp(metrics.slotMetrics.iconSizeDp))
        assertEquals(
            false,
            dockPinnedRunScrolls(5, metrics.slotMetrics, metrics.contentViewportMainAxisDp),
        )
    }

    @Test
    fun eightPinsKeepTheirSizeAndScroll() {
        val metrics = dockSurfaceMetrics(phoneDock(pins = 8), isEditing = false, availableMainAxisDp = 404)!!

        assertEquals(56, metrics.slotMetrics.iconSizeDp)
        assertEquals(88, dockCrossAxisDp(metrics.slotMetrics.iconSizeDp))
        assertEquals(true, dockPinnedRunScrolls(8, metrics.slotMetrics, metrics.contentViewportMainAxisDp))
    }

    @Test
    fun sixPinsBesideTwoDynamicEntriesAreNotSqueezedAndTheRunScrolls() {
        // The owner's phone: six pins, one live app and the Stream button on 404 dp. The dynamic side takes exactly
        // its two compact 48 dp entries (2 x 48 + 10), the pins keep 56 dp and scroll in what is left.
        val metrics =
            dockSurfaceMetrics(
                phoneDock(pins = 6),
                isEditing = false,
                availableMainAxisDp = 404,
                dynamicEntryCount = 2,
            )!!

        assertEquals(2 * 48 + 10, metrics.dynamicSectionMainAxisDp)
        assertEquals(404 - 106 - DOCK_SECTION_DIVIDER_MAIN_AXIS_DP, metrics.containerMainAxisDp)
        assertEquals(56, metrics.slotMetrics.iconSizeDp)
        assertEquals(88, dockCrossAxisDp(metrics.slotMetrics.iconSizeDp))
        assertEquals(true, dockPinnedRunScrolls(6, metrics.slotMetrics, metrics.contentViewportMainAxisDp))
    }

    @Test
    fun theDynamicSideTakesOnlyWhatItsEntriesNeedUpToItsSlotLimit() {
        val one = dockSurfaceMetrics(phoneDock(pins = 3), false, 404, dynamicEntryCount = 1)!!
        val limited = dockSurfaceMetrics(phoneDock(pins = 3, liveAppSlots = 0), false, 404, dynamicEntryCount = 9)!!

        // One entry: one compact tile. Zero live app slots still leave now playing and the Stream button.
        assertEquals(48, one.dynamicSectionMainAxisDp)
        assertEquals(2 * 48 + 10, limited.dynamicSectionMainAxisDp)
    }

    @Test
    fun aCrowdedDynamicSideLeavesThreePinsVisibleAtFullSize() {
        val metrics =
            dockSurfaceMetrics(
                phoneDock(pins = 8, liveAppSlots = 5),
                isEditing = false,
                availableMainAxisDp = 404,
                dynamicEntryCount = 7,
            )!!

        // Three pins and the dock padding are reserved for the pinned side; the dynamic side gets the rest and scrolls.
        val staticFloor = dockStaticFloorMainAxisDp(slotCount = 8, iconSizeDp = 56, spacingDp = 10)
        assertEquals(404 - staticFloor - DOCK_SECTION_DIVIDER_MAIN_AXIS_DP, metrics.dynamicSectionMainAxisDp)
        assertEquals(56, metrics.slotMetrics.iconSizeDp)
    }
}
