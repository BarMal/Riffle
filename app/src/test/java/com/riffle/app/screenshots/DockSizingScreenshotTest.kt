package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.Dock
import com.riffle.app.launcher.DockDynamicEntry
import com.riffle.app.launcher.DockDynamicEntryIntent
import com.riffle.app.launcher.DockDynamicSymbol
import com.riffle.app.launcher.DockInteractions
import com.riffle.app.launcher.HOME_DOCK_SURFACE_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.dockCrossAxisDp
import com.riffle.app.launcher.dockItemTestTag
import com.riffle.app.launcher.stream.glyph
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.stream.StreamSection
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Home with the Dock at the proportions the owner's reference shows: the bar is one icon plus its chrome tall, a pinned
 * icon is the size of a Home grid icon, and a dock with more pins than fit keeps their size and scrolls (the dynamic
 * side takes only its entries and never squeezes them). Five pins fit; eight scroll; with the live apps and the Stream
 * button beside them and without any live app; compact and expanded.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockSizingScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fivePinsFitBesideTheDynamicSide() {
        // The phone template: Home and dock icons are the same 44 dp, and five pins plus a tile fit.
        render(ScreenshotFixtures.homeState(sizing = sizing(pins = 5)))
        assertDockHeight(dockIconSizeDp = 44)
    }

    @Test
    fun fivePinsFitWithNoDynamicSide() {
        render(ScreenshotFixtures.homeState(sizing = sizing(pins = 5)), withStream = false)
        assertDockHeight(dockIconSizeDp = 44)
    }

    @Test
    fun eightPinsScrollAtFullIconSize() {
        render(ScreenshotFixtures.homeState(sizing = sizing(pins = 8)))
        assertDockHeight(dockIconSizeDp = 44)
    }

    @Test
    fun eightPinsScrollWithNoDynamicSide() {
        render(ScreenshotFixtures.homeState(sizing = sizing(pins = 8)), withStream = false)
        assertDockHeight(dockIconSizeDp = 44)
    }

    @Test
    fun ownerSizesSixPinsBesideLiveAppsAndTheStreamButton() {
        // Icons at 56 dp both on Home and in the Dock (the reference screenshot's proportions).
        render(ScreenshotFixtures.homeState(sizing = sizing(pins = 6, dockIcon = 56, homeIcon = 56)))
        assertDockHeight(dockIconSizeDp = 56)
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun ownerSizesFivePinsDark() {
        render(
            ScreenshotFixtures.homeState(sizing = sizing(pins = 5, dockIcon = 56, homeIcon = 56)),
            withStream = false,
        )
        assertDockHeight(dockIconSizeDp = 56)
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun eightPinsUnfolded() {
        render(ScreenshotFixtures.homeState(HomeLayoutDeviceClass.FOLDABLE, sizing = sizing(pins = 8)))
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun fivePinsUnfolded() {
        render(
            ScreenshotFixtures.homeState(HomeLayoutDeviceClass.FOLDABLE, sizing = sizing(pins = 5)),
            withStream = false,
        )
    }

    private fun sizing(
        pins: Int,
        dockIcon: Int? = null,
        homeIcon: Int? = null,
    ) = ScreenshotFixtures.HomeSizing(ScreenshotFixtures.pins(pins), dockIcon, homeIcon)

    private fun assertDockHeight(dockIconSizeDp: Int) {
        composeRule.onNodeWithTag(HOME_DOCK_SURFACE_TEST_TAG).assertHeightIsEqualTo(dockCrossAxisDp(dockIconSizeDp).dp)
    }

    @Test
    fun sixPinsBesideTwoLiveAppsAndTheStreamButton() {
        renderDock(pins = 6, iconSizeDp = 56, liveApps = 2)
        assertDockHeight(dockIconSizeDp = 56)
    }

    @Test
    fun eightPinsBesideOneLiveAppAndTheStreamButton() {
        renderDock(pins = 8, iconSizeDp = 44, liveApps = 1)
        assertDockHeight(dockIconSizeDp = 44)
    }

    @Test
    fun fivePinsAloneFill() {
        renderDock(pins = 5, iconSizeDp = 56, liveApps = null)
        assertDockHeight(dockIconSizeDp = 56)
    }

    @Test
    fun aCrowdedSideDockNeverRestsWithAnIconCutInHalf() = crowdedSideDock()

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun aCrowdedSideDockNeverRestsWithAnIconCutInHalfDark() = crowdedSideDock()

    /**
     * A right-edge Dock with more pins than its run can show, beside live apps: every pinned icon at rest is whole or
     * entirely out of view.
     */
    private fun crowdedSideDock() {
        val iconLoader = SolidColorAppIconLoader()
        val pinned = ScreenshotFixtures.pins(10)
        val items = pinned.map { app -> ScreenshotFixtures.shortcut(app) }
        val icon = 52
        composeRule.setContent {
            ScreenshotBackdrop {
                Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.CenterEnd) {
                    Dock(
                        dock = DockModel(capacity = pinned.size, items = items, iconSizeDp = icon),
                        isEditing = false,
                        notificationGroupsByApp = emptyList(),
                        appShortcutsByApp = emptyMap(),
                        appIconLoader = iconLoader,
                        position = DockPosition.RIGHT,
                        interactions = DockInteractions(onAction = {}),
                        dynamicEntries =
                            ScreenshotFixtures.gridApps.take(2).mapIndexed { index, app ->
                                liveEntry(app, badgeCount = index + 1)
                            } + tileEntry,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val density = composeRule.density.density

        fun assertWhole(moment: String) {
            val tall = icon * density
            items.forEach { item ->
                val bounds =
                    composeRule.onNodeWithTag(dockItemTestTag(item.id)).fetchSemanticsNode().boundsInRoot
                val visible = bounds.height
                assertTrue(
                    "$moment: ${item.label} is cut: $visible of $tall",
                    visible < 1f || abs(visible - tall) < 1.5f,
                )
            }
        }
        assertWhole("at rest")
        composeRule.captureScreen()
    }

    /** The Dock alone: [liveApps] live apps and a tile on its dynamic side, none at all when null. */
    private fun renderDock(
        pins: Int,
        iconSizeDp: Int,
        liveApps: Int?,
    ) {
        val iconLoader = SolidColorAppIconLoader()
        val pinned = ScreenshotFixtures.pins(pins)
        val live = ScreenshotFixtures.gridApps.take(liveApps ?: 0)
        composeRule.setContent {
            ScreenshotBackdrop {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Dock(
                        dock =
                            DockModel(
                                capacity = pinned.size,
                                items = pinned.map { app -> ScreenshotFixtures.shortcut(app) },
                                iconSizeDp = iconSizeDp,
                            ),
                        isEditing = false,
                        notificationGroupsByApp = emptyList(),
                        appShortcutsByApp = emptyMap(),
                        appIconLoader = iconLoader,
                        interactions = DockInteractions(onAction = {}),
                        dynamicEntries =
                            liveApps?.let {
                                live.mapIndexed { index, app -> liveEntry(app, badgeCount = index + 1) } + tileEntry
                            }.orEmpty(),
                    )
                }
            }
        }
        composeRule.captureScreen()
    }

    private fun liveEntry(
        app: InstalledApp,
        badgeCount: Int,
    ) = DockDynamicEntry(
        key = "live:${app.label}",
        label = app.label,
        identity = app.identity,
        badgeCount = badgeCount,
        isSelected = false,
        contentDescription = "${app.label}, $badgeCount new",
        intent = DockDynamicEntryIntent.Delegate,
    )

    private val tileEntry =
        DockDynamicEntry(
            key = "ctx:now",
            label = "Now",
            identity = null,
            badgeCount = 0,
            isSelected = false,
            contentDescription = "Now",
            intent = DockDynamicEntryIntent.Delegate,
            symbol = DockDynamicSymbol.Glyph(StreamSection.NOW.glyph()),
        )

    private fun render(
        state: LauncherShellState,
        withStream: Boolean = true,
    ) {
        val iconLoader = SolidColorAppIconLoader()
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = state,
                    appIconLoader = iconLoader,
                    stream = if (withStream) emptyStreamHost() else null,
                    onAction = {},
                )
            }
        }
        composeRule.captureScreen()
    }
}
