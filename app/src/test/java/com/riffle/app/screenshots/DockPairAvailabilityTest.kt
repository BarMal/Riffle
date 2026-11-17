package com.riffle.app.screenshots

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.riffle.app.launcher.LauncherShellContent
import com.riffle.app.launcher.LocalSplitFacts
import com.riffle.app.launcher.SplitFacts
import com.riffle.app.launcher.dockItemTestTag
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.AppPairItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.settings.AppPairsMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * App pairs are hidden from the Dock where the system cannot split, and come back when it can (Settings > Dock > App
 * pairs): the other items keep their order, the Dock lays out as if the pair were not there, and its size changes ride
 * the Dock's own spring. Folding is a window of 353 dp and unfolding one of 724 dp, as `LocalSplitFacts` reports it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockPairAvailabilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var facts by mutableStateOf(FOLDED)

    private val pair =
        AppPairItem(
            LauncherItemId("dock-pair:1"),
            ScreenshotFixtures.shortcut(ScreenshotFixtures.mail).copy(id = LauncherItemId("pair-mail")),
            ScreenshotFixtures.shortcut(ScreenshotFixtures.chat).copy(id = LauncherItemId("pair-chat")),
        )

    private fun state(mode: AppPairsMode = AppPairsMode.AUTOMATIC): LauncherShellState {
        val base = ScreenshotFixtures.homeState()
        val items =
            listOf(
                ScreenshotFixtures.shortcut(ScreenshotFixtures.camera),
                pair,
                ScreenshotFixtures.shortcut(ScreenshotFixtures.browser),
            )
        val dock = base.homeLayout.dock.copy(items = items, capacity = 5)
        val settings = base.launcherSettings.copy(dock = base.launcherSettings.dock.copy(appPairs = mode))
        return base.copy(homeLayout = base.homeLayout.copy(dock = dock), launcherSettings = settings)
    }

    private fun show(state: LauncherShellState) {
        composeRule.setContent {
            ScreenshotBackdrop {
                CompositionLocalProvider(LocalSplitFacts provides facts) {
                    LauncherShellContent(
                        state = state,
                        appIconLoader = SolidColorAppIconLoader(),
                        stream = emptyStreamHost(),
                        onAction = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private val camera = dockItemTestTag(LauncherItemId("com.riffle.sample.camera"))
    private val browser = dockItemTestTag(LauncherItemId("com.riffle.sample.browser"))
    private val pairTag = dockItemTestTag(pair.id)

    private fun orderOnScreen(): List<String> = composeRule.sampleDock().pins.sortedBy { it.rect.left }.map { it.tag }

    @Test
    fun foldedTheDockLaysOutWithoutThePairAndUnfoldedItIsBack() {
        show(state())
        assertEquals(listOf(camera, browser), orderOnScreen())
        val foldedBody = composeRule.sampleDock().body

        composeRule.runOnUiThread { facts = UNFOLDED }
        composeRule.waitForIdle()

        assertEquals(listOf(camera, pairTag, browser), orderOnScreen())
        assertTrue(
            "the Dock did not grow for the pair",
            composeRule.sampleDock().body.width > foldedBody.width,
        )

        composeRule.runOnUiThread { facts = FOLDED }
        composeRule.waitForIdle()

        assertEquals(listOf(camera, browser), orderOnScreen())
        assertEquals(foldedBody.width, composeRule.sampleDock().body.width, 1f)
    }

    @Test
    fun aHiddenPairLeavesNoGapAndTheDockIsLaidOutAsIfItWereNeverStored() {
        var shown by mutableStateOf(state())
        composeRule.setContent {
            ScreenshotBackdrop {
                CompositionLocalProvider(LocalSplitFacts provides facts) {
                    LauncherShellContent(
                        state = shown,
                        appIconLoader = SolidColorAppIconLoader(),
                        stream = emptyStreamHost(),
                        onAction = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val hidden = composeRule.sampleDock()

        val plain = state()
        val withoutPair = plain.homeLayout.dock.items.filter { it !is AppPairItem }
        composeRule.runOnUiThread {
            shown =
                plain.copy(
                    homeLayout = plain.homeLayout.copy(dock = plain.homeLayout.dock.copy(items = withoutPair)),
                )
        }
        composeRule.waitForIdle()
        val never = composeRule.sampleDock()

        assertEquals(never.body, hidden.body)
        assertEquals(never.pins.map { it.tag to it.rect }, hidden.pins.map { it.tag to it.rect })
    }

    @Test
    fun theDockGrowsOnItsSpringWhenThePairComesBackAndNeverJumps() {
        show(state())
        val before = composeRule.sampleDock().body.width
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnUiThread { facts = UNFOLDED }
        val widths = mutableListOf(composeRule.sampleDock().body.width)
        repeat(FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            widths += composeRule.sampleDock().body.width
        }

        val log = widths.mapIndexed { i, w -> "$i:${w.toInt()}" }.joinToString(" ")
        assertTrue("the Dock did not grow: $log", widths.last() > before + 10f)
        widths.zipWithNext().forEach { (a, b) -> assertTrue("it shrank while growing: $log", b >= a - 1f) }
        assertTrue(
            "it jumped instead of easing: $log",
            widths.count { it > before + 2f && it < widths.last() - 2f } >= 3,
        )
        assertTrue("it overshot: $log", widths.max() <= widths.last() + 1f)
    }

    @Test
    fun alwaysShowShowsThePairOnAFoldedDevice() {
        show(state(AppPairsMode.ALWAYS_SHOW))
        assertEquals(listOf(camera, pairTag, browser), orderOnScreen())
    }

    @Test
    fun offHidesThePairEverywhere() {
        facts = UNFOLDED
        show(state(AppPairsMode.OFF))
        assertEquals(listOf(camera, browser), orderOnScreen())
    }

    @Test
    fun anOldAndroidVersionHidesThePairEvenOnALargeWindow() {
        facts = SplitFacts(sdkInt = 36, windowWidthDp = 724, inMultiWindow = false)
        show(state())
        assertEquals(listOf(camera, browser), orderOnScreen())
    }

    @Test
    fun theLauncherBeingInMultiWindowHidesThePair() {
        facts = SplitFacts(sdkInt = 37, windowWidthDp = 724, inMultiWindow = true)
        show(state())
        assertEquals(listOf(camera, browser), orderOnScreen())
    }

    private companion object {
        val FOLDED = SplitFacts(sdkInt = 37, windowWidthDp = 353, inMultiWindow = false)
        const val FRAMES = 60
        val UNFOLDED = SplitFacts(sdkInt = 37, windowWidthDp = 724, inMultiWindow = false)
    }
}
