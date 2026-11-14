package com.riffle.app.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.riffle.app.launcher.HOME_DOCK_PULL_TEST_TAG
import com.riffle.app.launcher.HOME_DOCK_TEST_TAG
import com.riffle.app.launcher.HOME_HANDLE_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.HomeEntranceFrame
import com.riffle.app.launcher.HomeEntranceFrameKey
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.cards.AdaptiveStagePosture
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeEntrance
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.settings.MotionSettings
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Dock's entrance when Home appears (a fade and a small rise from the Dock's own edge) through the real
 * [HomeDestination]: the Dock is in its final place from the very first frame and never travels (its layout bounds are
 * the same at every frame of the entrance, bottom and side), the entrance changes only alpha and a translation toward
 * the Dock's edge (about 14 dp, to nothing), a reduced-motion entrance is a plain fade, it does not replay when a
 * sheet comes and goes over Home but does when Home was stopped, and the Dock area's own size easing does not run on
 * the first frames (it still eases a later change).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockEntranceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var shell by mutableStateOf(LauncherShellState())

    /** The lifecycle Home sees, driven by the test: the Compose test activity cannot be stopped and still be read. */
    private val lifecycleOwner =
        object : LifecycleOwner {
            override val lifecycle: Lifecycle get() = registry
            val registry: LifecycleRegistry by lazy { LifecycleRegistry.createUnsafe(this) }
        }

    private fun lifecycle(event: Lifecycle.Event) =
        composeRule.runOnUiThread { lifecycleOwner.registry.handleLifecycleEvent(event) }

    private fun show(state: LauncherShellState) {
        shell = state
        composeRule.mainClock.autoAdvance = false
        lifecycle(Lifecycle.Event.ON_CREATE)
        lifecycle(Lifecycle.Event.ON_START)
        lifecycle(Lifecycle.Event.ON_RESUME)
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                ScreenshotBackdrop {
                    HomeDestination(
                        state = shell,
                        appIconLoader = SolidColorAppIconLoader(),
                        stream = emptyStreamHost(),
                        adaptiveStageWindowLayout =
                            screenshotWindowLayout(
                                if (shell.homeLayoutSet.activeKey.deviceClass == HomeLayoutDeviceClass.PHONE) {
                                    AdaptiveStagePosture.COMPACT
                                } else {
                                    AdaptiveStagePosture.UNFOLDED
                                },
                            ),
                        onAction = {},
                    )
                }
            }
        }
    }

    private fun phone(sizing: ScreenshotFixtures.HomeSizing = ScreenshotFixtures.HomeSizing()) =
        ScreenshotFixtures.homeState(HomeLayoutDeviceClass.PHONE, DockPosition.BOTTOM, sizing).withHomePages(3)

    private class Sample(
        /** The Dock node's bounds: it sits outside the entrance's layer, so these are its layout bounds. */
        val layout: Rect,
        /** The Dock's strip: inside the entrance's layer, so these are where it is drawn. */
        val drawn: Rect,
        val entrance: HomeEntranceFrame,
    )

    private fun sample(): Sample {
        val outer = composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).fetchSemanticsNode()
        val inner = composeRule.onNodeWithTag(HOME_DOCK_TEST_TAG).fetchSemanticsNode()
        return Sample(outer.boundsInRoot, inner.boundsInRoot, outer.config[HomeEntranceFrameKey]())
    }

    /** Every frame from the first, for [millis]. */
    private fun frames(millis: Int = ENTRANCE_MILLIS): List<Sample> {
        val samples = mutableListOf(sample())
        repeat(millis / FRAME_MILLIS) {
            composeRule.mainClock.advanceTimeByFrame()
            samples += sample()
        }
        return samples
    }

    private val dp get() = composeRule.density.density

    private fun assertDockNeverTravels(
        samples: List<Sample>,
        edge: DockPosition,
    ) {
        val final = samples.last()
        samples.forEachIndexed { index, s ->
            assertEquals("frame $index: the Dock's layout bounds are its final ones", final.layout, s.layout)
            assertEquals("frame $index: the strip keeps its size", final.drawn.width, s.drawn.width, 1f)
            assertEquals("frame $index: the strip keeps its size", final.drawn.height, s.drawn.height, 1f)
            // The drawn strip is the final one shifted toward the Dock's edge by at most the rise, never past it.
            val toward =
                when (edge) {
                    DockPosition.BOTTOM -> s.drawn.top - final.drawn.top
                    DockPosition.RIGHT -> s.drawn.left - final.drawn.left
                    DockPosition.LEFT -> final.drawn.left - s.drawn.left
                    DockPosition.TOP -> final.drawn.top - s.drawn.top
                }
            val across =
                when (edge) {
                    DockPosition.BOTTOM, DockPosition.TOP -> s.drawn.left - final.drawn.left
                    else -> s.drawn.top - final.drawn.top
                }
            assertTrue("frame $index: shifted toward the edge ($toward px)", toward >= -1f)
            assertTrue("frame $index: by no more than the rise ($toward px)", toward <= HomeEntrance.RISE_DP * dp + 1f)
            assertEquals("frame $index: not at all across it", 0f, across, 1f)
        }
    }

    @Test
    fun aBottomDockIsInItsFinalPlaceFromTheFirstFrameAndRisesUpFromTheBottom() {
        show(phone())

        val samples = frames()

        assertDockNeverTravels(samples, DockPosition.BOTTOM)
        // The first frame is invisible and the full rise away; the last is opaque and in place.
        assertEquals(0f, samples.first().entrance.alpha, 0.01f)
        assertEquals(HomeEntrance.RISE_DP, samples.first().entrance.translationYDp, 0.5f)
        assertEquals(0f, samples.first().entrance.translationXDp, 0f)
        assertEquals(1f, samples.last().entrance.alpha, 0.01f)
        assertEquals(0f, samples.last().entrance.translationYDp, 0.5f)
        assertEquals(
            "drawn at the first frame: the rise below the final place",
            HomeEntrance.RISE_DP * dp,
            samples.first().drawn.top - samples.last().drawn.top,
            1f,
        )
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun aRightDockIsInItsFinalPlaceFromTheFirstFrameAndComesInFromTheSide() {
        show(ScreenshotFixtures.homeState(HomeLayoutDeviceClass.FOLDABLE, DockPosition.RIGHT).withHomePages(3))

        val samples = frames()

        assertDockNeverTravels(samples, DockPosition.RIGHT)
        assertEquals(HomeEntrance.RISE_DP, samples.first().entrance.translationXDp, 0.5f)
        assertEquals(0f, samples.first().entrance.translationYDp, 0f)
        assertEquals(0f, samples.last().entrance.translationXDp, 0.5f)
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun aLeftDockComesInFromTheLeft() {
        show(ScreenshotFixtures.homeState(HomeLayoutDeviceClass.TABLET, DockPosition.LEFT).withHomePages(3))

        val samples = frames()

        assertDockNeverTravels(samples, DockPosition.LEFT)
        assertEquals(-HomeEntrance.RISE_DP, samples.first().entrance.translationXDp, 0.5f)
    }

    @Test
    fun theEntranceOnlyEverFadesInAndRisesToItsPlace() {
        show(phone())

        val frames = frames().map { it.entrance }

        frames.zipWithNext().forEach { (before, after) ->
            assertTrue("alpha never falls", after.alpha >= before.alpha - 1e-3f)
            assertTrue("the rise never grows", after.translationYDp <= before.translationYDp + 1e-3f)
        }
        assertTrue(frames.all { it.alpha in 0f..1f && it.translationYDp in 0f..HomeEntrance.RISE_DP })
    }

    @Test
    fun thePageHandleComesInWithTheDock() {
        show(phone())

        val start = composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).fetchSemanticsNode().boundsInRoot
        repeat(ENTRANCE_MILLIS / FRAME_MILLIS) { composeRule.mainClock.advanceTimeByFrame() }
        val end = composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).fetchSemanticsNode().boundsInRoot

        // The handle is drawn the rise lower at the first frame (it is inside the same entrance), and in place after.
        assertEquals(HomeEntrance.RISE_DP * dp, start.top - end.top, 1f)
        assertEquals(end.width, start.width, 1f)
        assertEquals(end.left, start.left, 1f)
    }

    @Test
    fun underReducedMotionItIsAPlainShortFadeWithNoRise() {
        show(
            phone().copy(
                launcherSettings =
                    ScreenshotFixtures.homeState().launcherSettings.copy(
                        motion = MotionSettings(reducedMotionPreference = ReducedMotionPreference.ON),
                    ),
            ),
        )

        val samples = frames(millis = REDUCED_MILLIS)

        assertTrue("no rise at any frame", samples.all { it.entrance.translationYDp == 0f })
        assertDockNeverTravels(samples, DockPosition.BOTTOM)
        assertEquals(0f, samples.first().entrance.alpha, 0.01f)
        assertEquals(1f, samples.last().entrance.alpha, 0.01f)
    }

    @Test
    fun aSheetOverHomeDoesNotReplayItButHomeBeingStoppedDoes() {
        show(phone())
        frames()
        assertEquals(1f, sample().entrance.alpha, 0.01f)

        // Search opens over Home and closes: Home was never hidden, so the Dock is simply there.
        composeRule.runOnUiThread {
            shell = shell.copy(destination = ShellDestination.SEARCH)
            Snapshot.sendApplyNotifications()
        }
        repeat(FRAMES_FOR_A_SHEET) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.runOnUiThread {
            shell = shell.copy(destination = ShellDestination.HOME)
            Snapshot.sendApplyNotifications()
        }
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals("not replayed after a sheet", 1f, sample().entrance.alpha, 0.001f)

        // The activity stops (another app, the lock screen) and comes back: Home appears again.
        lifecycle(Lifecycle.Event.ON_PAUSE)
        lifecycle(Lifecycle.Event.ON_STOP)
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals("hidden while Home is stopped", 0f, sample().entrance.alpha, 0.01f)
        lifecycle(Lifecycle.Event.ON_START)
        lifecycle(Lifecycle.Event.ON_RESUME)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        val replaying = sample().entrance
        assertTrue("replaying: ${replaying.alpha}", replaying.alpha < 1f)
        repeat(ENTRANCE_MILLIS / FRAME_MILLIS) { composeRule.mainClock.advanceTimeByFrame() }
        assertEquals(1f, sample().entrance.alpha, 0.01f)
    }

    @Test
    fun theDockAreaDoesNotEaseItsSizeOnTheFirstFramesButDoesEaseALaterChange() {
        val small = ScreenshotFixtures.HomeSizing(dockIconSizeDp = SMALL_ICON_DP)
        val large = ScreenshotFixtures.HomeSizing(dockIconSizeDp = LARGE_ICON_DP)
        show(phone(small))

        // A change on the very first frames (Home settling) lands at once: the next frame already has the final size.
        composeRule.runOnUiThread {
            shell = phone(large)
            Snapshot.sendApplyNotifications()
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        val early = sample().layout.height
        repeat(ENTRANCE_MILLIS / FRAME_MILLIS) { composeRule.mainClock.advanceTimeByFrame() }
        val settled = sample().layout.height
        assertEquals("a change during the entrance snaps", settled, early, 1f)

        // The same kind of change once Home has settled is eased: it is part way a couple of frames in.
        composeRule.runOnUiThread {
            shell = phone(small)
            Snapshot.sendApplyNotifications()
        }
        repeat(FRAMES_PART_WAY) { composeRule.mainClock.advanceTimeByFrame() }
        val part = sample().layout.height
        repeat(ENTRANCE_MILLIS / FRAME_MILLIS) { composeRule.mainClock.advanceTimeByFrame() }
        val later = sample().layout.height
        assertTrue("eased: $part is between $settled and $later", part - later > 2f && settled - part > 2f)
    }

    private companion object {
        const val FRAME_MILLIS = 16
        const val ENTRANCE_MILLIS = 960
        const val REDUCED_MILLIS = 160
        const val FRAMES_FOR_A_SHEET = 30
        const val FRAMES_PART_WAY = 20
        const val SMALL_ICON_DP = 44
        const val LARGE_ICON_DP = 64
    }
}
