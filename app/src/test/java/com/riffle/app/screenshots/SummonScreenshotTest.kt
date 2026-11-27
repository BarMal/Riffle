package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.summon.SummonHeaderArea
import com.riffle.app.launcher.summon.SummonMenu
import com.riffle.app.launcher.summon.SummonRing
import com.riffle.app.launcher.summon.SummonRingModel
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
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
import com.riffle.core.domain.launcher.settings.EffectiveFeatures
import com.riffle.core.domain.launcher.summon.RingBounds
import com.riffle.core.domain.launcher.summon.RingPoint
import com.riffle.core.domain.launcher.summon.SummonHeader
import com.riffle.core.domain.launcher.summon.SummonPhase
import com.riffle.core.domain.launcher.summon.SummonRingGeometry
import com.riffle.core.domain.launcher.summon.SummonSettings
import com.riffle.core.domain.launcher.summon.SummonSlot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Summon over the stand-in wallpaper, in the light theme a phone on a dark wallpaper often runs: the clock, the
 * menu and the ring's label, star and Favourites row must all read without a scrim behind them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class SummonScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun restingClockAndDateReadOnTheWallpaper() {
        composeRule.setContent {
            ScreenshotBackdrop {
                SummonHeaderArea(
                    header = SummonHeader.ClockAndDate,
                    homeLayout = layout,
                    widgetViewFactory = EmptyHomeWidgetViewFactory,
                    onAction = {},
                    modifier = Modifier.padding(24.dp),
                    nowMillis = { FIXED_NOW },
                )
            }
        }
        composeRule.captureScreen()
    }

    @Test
    fun theMenusButtonsRead() {
        composeRule.setContent {
            ScreenshotBackdrop {
                SummonMenu(
                    model = model,
                    features = EffectiveFeatures(),
                    appIconLoader = SolidColorAppIconLoader(),
                    onAction = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.captureScreen()
    }

    @Test
    fun theRingWithEightFavouritesKeepsTheRowOnScreen() {
        composeRule.setContent { ScreenshotBackdrop { OpenRing(aim = 2, favouritesOpen = true, dockAim = 6) } }
        composeRule.captureScreen()
    }

    @Test
    fun theRingsLabelAndStarRead() {
        composeRule.setContent { ScreenshotBackdrop { OpenRing(aim = 0, favouritesOpen = false, dockAim = null) } }
        composeRule.captureScreen()
    }

    @Composable
    private fun OpenRing(
        aim: Int,
        favouritesOpen: Boolean,
        dockAim: Int?,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val width = with(density) { maxWidth.toPx() }
            val height = with(density) { maxHeight.toPx() }
            val radius = with(density) { 108.dp.toPx() }
            val half = with(density) { 28.dp.toPx() }
            val centre = RingPoint(width / 2f, height * 0.42f)
            val ring =
                SummonRingGeometry.layout(
                    centre = centre,
                    count = model.level(emptyList()).size,
                    bounds = RingBounds(half, half, width - half, height - half),
                    radius = radius,
                    clearance = with(density) { 34.dp.toPx() },
                )
            Box(Modifier.fillMaxSize()) {
                SummonRing(
                    phase =
                        SummonPhase.Open(
                            origin = centre,
                            path = emptyList(),
                            layout = ring,
                            finger = ring.position(aim),
                            aim = aim,
                            favouritesOpen = favouritesOpen,
                            dockAim = dockAim,
                        ),
                    model = model,
                    appIconLoader = SolidColorAppIconLoader(),
                    reducedMotion = true,
                    onDockIconPlaced = { _, _ -> },
                    hint = "Release to open",
                    heightPx = height,
                )
            }
        }
    }

    private companion object {
        const val FIXED_NOW = 1_795_773_600_000L

        fun app(name: String) = AppIdentity(AppPackageName("pkg.$name"), AppActivityName("pkg.$name.Main"))

        val ringApps = listOf("maps", "mail", "music", "camera", "notes")
        val dockApps = listOf("phone", "messages", "browser", "photos", "clock", "files", "podcasts", "wallet")

        val layout =
            HomeLayout(
                viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                pages = listOf(LauncherPage(LauncherPageId("p0"), grid = GridDimensions(4, 5))),
                selectedPageId = LauncherPageId("p0"),
                dock =
                    DockModel(
                        capacity = dockApps.size,
                        items = dockApps.map { AppShortcutItem(LauncherItemId("d-$it"), app(it), it) },
                    ),
            )

        val model =
            SummonRingModel.from(
                settings =
                    SummonSettings(
                        slots =
                            ringApps.take(3).map { SummonSlot.App(app(it)) } +
                                SummonSlot.Favourites +
                                SummonSlot.Glance(GlanceWidgetKind.NOW_PLAYING) +
                                ringApps.drop(3).map { SummonSlot.App(app(it)) },
                    ),
                layout = layout,
                installed = (ringApps + dockApps).map { InstalledApp(app(it), it.replaceFirstChar(Char::uppercase)) },
                glanceLabel = { "Now playing" },
                favouritesLabel = "Favourites",
            )
    }
}
