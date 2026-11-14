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
 * [com.riffle.core.domain.launcher.home.DockModel.liveAppSlots] cap (plus now playing), drawn
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
        // A 560dp run, a dynamic section that draws in full at 228dp (4 entries: 3 live app slots and now playing,
        // 48dp/12dp) plus the
        // 17dp rule leaves 315dp for the static side -- less than capacity alone would draw for 10
        // icons at this icon size (10*48 + 9*12 = 588dp), so the static side is capped, not the
        // dynamic one. The 315dp leaves 287dp for the icons: five whole ones at the compacted 8dp gap
        // (5*48 + 4*8 = 272dp) and a sliver of a sixth. The pinned run shows the five whole icons only (it scrolls by
        // whole icons for the rest), never a sixth cut in half, and the Dock is as long as what it shows.
        val surfaceMetrics =
            dockSurfaceMetrics(
                dock =
                    DockModel(
                        capacity = 10,
                        items = List(10) { index -> testDockShortcut("app-$index") },
                        liveAppSlots = 3,
                        iconSizeDp = 48,
                        itemSpacingDp = 12,
                    ),
                isEditing = false,
                availableMainAxisDp = 560,
                runsHorizontally = true,
                dynamicEntryCount = 5,
            )

        assertEquals(228, surfaceMetrics?.dynamicSectionMainAxisDp)
        assertEquals(5 * 48 + 4 * DOCK_COMPACTED_SPACING_FLOOR_DP, surfaceMetrics?.contentViewportMainAxisDp)
        assertEquals(
            5 * 48 + 4 * DOCK_COMPACTED_SPACING_FLOOR_DP + 2 * DOCK_MAIN_AXIS_PADDING_DP,
            surfaceMetrics?.containerMainAxisDp,
        )
    }

    @Test
    fun aCrowdedPinnedRunNeverRestsWithAnIconCutInHalfWhateverTheRoomLeft() {
        // For every run a crowded side Dock may be given, the pinned viewport is a whole number of icons and gaps.
        (200..700 step 7).forEach { available ->
            val metrics =
                dockSurfaceMetrics(
                    dock =
                        DockModel(
                            capacity = 12,
                            items = List(12) { index -> testDockShortcut("app-$index") },
                            liveAppSlots = 3,
                            iconSizeDp = 48,
                            itemSpacingDp = 12,
                        ),
                    isEditing = false,
                    availableMainAxisDp = available,
                    runsHorizontally = false,
                    dynamicEntryCount = 5,
                ) ?: return@forEach
            val viewport = metrics.contentViewportMainAxisDp
            val stride = metrics.slotMetrics.iconSizeDp + metrics.slotMetrics.itemSpacingDp
            if (dockPinnedRunScrolls(12, metrics.slotMetrics, viewport)) {
                assertEquals(
                    "a whole number of icons at $available dp",
                    0,
                    (viewport + metrics.slotMetrics.itemSpacingDp) % stride,
                )
            }
        }
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
        // The owner's phone: six pins, one live app and now playing on 404 dp. The dynamic side takes exactly
        // its two compact 48 dp entries (2 x 48 + 10), the pins keep 56 dp and scroll in what is left.
        val metrics =
            dockSurfaceMetrics(
                phoneDock(pins = 6),
                isEditing = false,
                availableMainAxisDp = 404,
                dynamicEntryCount = 2,
            )!!

        assertEquals(2 * 48 + 10, metrics.dynamicSectionMainAxisDp)
        // 404 - 106 - 17 = 281 dp is left, 253 dp inside the Dock's padding: four whole 56 dp icons at the compacted 8 dp
        // gap (4 * 56 + 3 * 8 = 248 dp). The Dock is as long as those four, so no fifth icon is cut in half.
        assertEquals(4 * 56 + 3 * DOCK_COMPACTED_SPACING_FLOOR_DP, metrics.contentViewportMainAxisDp)
        assertEquals(
            4 * 56 + 3 * DOCK_COMPACTED_SPACING_FLOOR_DP + 2 * DOCK_MAIN_AXIS_PADDING_DP,
            metrics.containerMainAxisDp,
        )
        assertEquals(56, metrics.slotMetrics.iconSizeDp)
        assertEquals(88, dockCrossAxisDp(metrics.slotMetrics.iconSizeDp))
        assertEquals(true, dockPinnedRunScrolls(6, metrics.slotMetrics, metrics.contentViewportMainAxisDp))
    }

    @Test
    fun theDynamicSideTakesOnlyWhatItsEntriesNeedUpToItsSlotLimit() {
        val one = dockSurfaceMetrics(phoneDock(pins = 3), false, 404, dynamicEntryCount = 1)!!
        val limited = dockSurfaceMetrics(phoneDock(pins = 3, liveAppSlots = 0), false, 404, dynamicEntryCount = 9)!!

        // One entry: one compact tile. Zero live app slots still leave now playing.
        assertEquals(48, one.dynamicSectionMainAxisDp)
        assertEquals(48, limited.dynamicSectionMainAxisDp)
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

    @Test
    fun theContextualModeIsOneRowOfPinsEntriesAndTilesAsLongAsItsContentUpToTheRun() {
        // 3 pins (drawn at the row's 48 dp item, 10 apart = 164), a rule (17), 2 live entries and 3 tiles (48 dp,
        // 10 apart, a rule between groups). The Dock's 56 dp icon size is the Home mode's; the row has one item size.
        val dock = phoneDock(pins = 3)
        val roomy = dockSurfaceMetrics(dock, false, 560, dynamicEntryCount = 5, contextualTileCount = 3)!!
        val run = requireNotNull(roomy.contextualRun)

        assertEquals(164 + 17 + (5 * 48) + (3 * 10) + (17 + 2 * 10), run.contentDp)
        assertEquals(5, run.entries.size)
        // Everything fits in 560 dp, so the dock is as long as its row plus its padding and the row does not scroll.
        assertEquals(run.contentDp + 2 * DOCK_MAIN_AXIS_PADDING_DP, roomy.surfaceMainAxisDp)
    }

    @Test
    fun aRowLongerThanTheRunIsCappedAndScrolls() {
        val crowded =
            dockSurfaceMetrics(phoneDock(pins = 6), false, 404, dynamicEntryCount = 8, contextualTileCount = 5)!!
        assertEquals(404, crowded.surfaceMainAxisDp)
        assertEquals(true, requireNotNull(crowded.contextualRun).contentDp + 2 * DOCK_MAIN_AXIS_PADDING_DP > 404)
    }

    @Test
    fun theHomeModeHasNoContextualRunAndKeepsItsOwnLayout() {
        val home = dockSurfaceMetrics(phoneDock(pins = 3), false, 404, dynamicEntryCount = 2)!!
        assertEquals(null, home.contextualRun)
        assertEquals(2 * 48 + 10, home.dynamicSectionMainAxisDp)
    }

    @Test
    fun theContextualRowDrawsPinsLiveAppsAndTilesAsOneRowOfEqualItemsAndHomeKeepsItsSize() {
        val dock = phoneDock(pins = 6)
        val metrics = dockSurfaceMetrics(dock, false, 404, dynamicEntryCount = 2, contextualTileCount = 1)!!
        val run = requireNotNull(metrics.contextualRun)
        // 6 pins at the row's 48 dp item and the Dock's own 10 dp gap (6 * 48 + 5 * 10 = 338), a rule, then an entry.
        assertEquals(48, metrics.contextualSlotMetrics.iconSizeDp)
        assertEquals(338 + DOCK_SECTION_DIVIDER_MAIN_AXIS_DP, run.entries.first().startDp)
        assertEquals(listOf(48), (run.pinned + run.entries).map { it.endDp - it.startDp }.distinct())
        // The Home mode is untouched: the pinned icons keep the Dock's 56 dp.
        assertEquals(56, dockSurfaceMetrics(dock, false, 404)!!.slotMetrics.iconSizeDp)
    }

    @Test
    fun sixItemsAreWholeInTheViewportOfAnyPhoneWideDock() {
        listOf(320, 360, 392, 411, 480).forEach { width ->
            val dock = phoneDock(pins = 6)
            val metrics = dockSurfaceMetrics(dock, false, width, dynamicEntryCount = 8, contextualTileCount = 5)!!
            val viewport = metrics.contextualMainAxisDp - 2 * DOCK_MAIN_AXIS_PADDING_DP
            val pitch = 48 + metrics.contextualSlotMetrics.itemSpacingDp
            // Whole items across the viewport: n * 48 + (n - 1) * gap <= viewport.
            val whole = (viewport + metrics.contextualSlotMetrics.itemSpacingDp) / pitch
            assertEquals("$width dp wide shows $whole items", true, whole >= 6 || width < 360)
        }
    }

    @Test
    fun aDockWithNoPinsAndNothingDynamicKeepsABodyOneIconLong() {
        val empty = DockModel(capacity = 3, items = emptyList(), iconSizeDp = 56, itemSpacingDp = 10)
        val metrics = dockSurfaceMetrics(empty, false, 404)!!
        assertEquals(56 + 2 * DOCK_MAIN_AXIS_PADDING_DP, metrics.surfaceMainAxisDp)
    }
}
