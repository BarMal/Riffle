package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.app.launcher.expressions.NoExpressionImageLoader
import com.riffle.app.launcher.stream.CachedStreamLayoutRepository
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.app.launcher.stream.StreamLayoutStorePort
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.dock.DockMotionConstants
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.stream.StreamActionGateway
import com.riffle.core.domain.launcher.stream.StreamActionResult
import com.riffle.core.domain.launcher.stream.StreamContentProvider
import com.riffle.core.domain.launcher.stream.StreamContext
import com.riffle.core.domain.launcher.stream.StreamLayouts
import com.riffle.core.domain.launcher.workspace.ItemSource
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hold to choose (ADR 0003, Amendment 3, One gesture, third depth) through the real [HomeDestination] with a Stream
 * over no sources (every section still has a page, so the Dock has a tile for each): a Dock pressed down past the
 * threshold and held, or slid sideways, starts choosing; the item under the finger is named in a label above the Dock;
 * lifting opens its page from Home, switches to it from the open Stream, and does nothing when the finger was taken
 * well above the Dock. What is asserted is outcomes (the page the Stream landed on, what the shell was sent), driving time
 * with the test clock, never by sleeping and never by animation timing. The decisions are tested in `core:domain`
 * (`DockChoiceTest`); the JVM twin is `DockHoldToChooseTest`. The finger is parked at the Dock's far end, so the last
 * tile is the one chosen whether or not the emulator's short screen lets the row scroll.
 */
@RunWith(AndroidJUnit4::class)
class DockHoldToChooseInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val docked = (0 until DOCKED_COUNT).map { index -> shortcut("docked$index") }
    private val sent = mutableListOf<LauncherShellAction>()
    private var shellShown by mutableStateOf(false)
    private lateinit var host: StreamHost

    @Test
    fun heldDownThenSlidOverATileAndLiftedOpensThatPageFromHome() {
        setContent(streamShown = false)
        pressAndHold()

        // The finger at the row's end: it scrolls there if it can, and the last tile is under the finger either way.
        holdAtEnd()
        assertEquals(LAST_TILE, label())
        lift()

        awaitLanded(LAST_TILE)
        composeRule.runOnIdle {
            assertTrue("the lift did not open the Stream: $sent", LauncherShellAction.OpenStream in sent)
        }
        assertNull("the label stayed", label())
    }

    @Test
    fun slidingSidewaysWhilePressedChoosesWithoutHolding() {
        setContent(streamShown = false)
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput {
            down(center)
            repeat(STEPS) { moveBy(Offset(0f, PRESS_PX / STEPS), delayMillis = STEP_MILLIS) }
            moveBy(Offset(SLIDE_PX, 0f), delayMillis = STEP_MILLIS)
        }
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { label() != null }
        assertNotNull(label())
        lift()
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertTrue(LauncherShellAction.OpenStream in sent) }
    }

    @Test
    fun choosingWhileTheStreamIsOutSwitchesThePageAndKeepsTheStream() {
        setContent(streamShown = true)
        pressAndHold()

        holdAtEnd()
        assertEquals(LAST_TILE, label())
        lift()

        awaitLanded(LAST_TILE)
        composeRule.runOnIdle {
            assertTrue("choosing closed the Stream: $sent", LauncherShellAction.OpenHome !in sent)
        }
    }

    @Test
    fun aFingerTakenWellAboveTheDockCancelsAndNothingChanges() {
        setContent(streamShown = false)
        pressAndHold()
        val dockHeight = composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).fetchSemanticsNode().size.height
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput {
            moveBy(Offset(0f, -(PRESS_PX + dockHeight * 2.5f)), delayMillis = STEP_MILLIS)
        }
        composeRule.mainClock.advanceTimeByFrame()
        assertNull("a cancelling finger still named an item", label())
        lift()
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertTrue("a cancelled choice did something: $sent", sent.isEmpty()) }
    }

    @Test
    fun aQuickPressStillOpensTheStreamWithoutChoosing() {
        setContent(streamShown = false)
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput {
            down(center)
            repeat(STEPS) { moveBy(Offset(0f, PRESS_PX / STEPS), delayMillis = STEP_MILLIS) }
            up()
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), sent) }
        assertNull(label())
    }

    /** What the choice's label says now, or null when there is none. Read on the test thread, not in runOnUiThread. */
    private fun label(): String? =
        composeRule.onAllNodesWithTag(DOCK_CHOICE_LABEL_TEST_TAG).fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrNull(DockChoiceLabelKey)

    /** Presses the Dock down and holds the finger there until choosing has begun (the hold passes on the clock). */
    private fun pressAndHold() {
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput {
            down(center)
            repeat(STEPS) { moveBy(Offset(0f, PRESS_PX / STEPS), delayMillis = STEP_MILLIS) }
        }
        composeRule.mainClock.advanceTimeBy(HOLD_PLUS_MILLIS)
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { label() != null }
        composeRule.mainClock.advanceTimeByFrame()
    }

    /**
     * Parks the finger at the Dock's far end (the row scrolls there while it can, then stops) until the last tile is
     * the one under it: no tile position is guessed, so it holds whether or not the row scrolls on this screen.
     */
    private fun holdAtEnd() {
        val width = composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).fetchSemanticsNode().size.width
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput {
            moveTo(Offset(width - END_INSET_PX, center.y))
        }
        repeat(EDGE_FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { label() == LAST_TILE }
    }

    private fun lift() {
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
    }

    /** The Stream's pager lands on the page of [tile] once the shell has answered and the fan has run. */
    private fun awaitLanded(tile: String) {
        val key = tile.lowercase()
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { host.dock.currentKey == key }
        composeRule.waitForIdle()
    }

    private fun setContent(streamShown: Boolean) {
        shellShown = streamShown
        val layout =
            HomeLayoutDefaults.standard().let { standard ->
                standard.copy(
                    viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                    dock = standard.dock.copy(capacity = DOCKED_COUNT, items = docked),
                )
            }
        val state =
            LauncherShellState(
                homeLayout = layout,
                installedApps = docked.map { item -> InstalledApp(identity = item.appIdentity, label = item.label) },
            )
        host = streamHost()
        composeRule.setContent {
            MaterialTheme {
                HomeDestination(
                    state = state,
                    appIconLoader = EmptyAppIconLoader,
                    stream = host,
                    streamShown = shellShown,
                    onAction = { action ->
                        sent += action
                        if (action == LauncherShellAction.OpenStream) shellShown = true
                        if (action == LauncherShellAction.OpenHome) shellShown = false
                    },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun streamHost(): StreamHost =
        StreamHost(
            provider =
                StreamContentProvider(
                    object : SourceRegistry {
                        override fun descriptors(): List<SourceDescriptor> = emptyList()

                        override fun source(id: SourceId): ItemSource? = null
                    },
                    context = { StreamContext(nowEpochMillis = NOW) },
                ),
            layouts =
                CachedStreamLayoutRepository(
                    object : StreamLayoutStorePort {
                        override suspend fun read(): StreamLayouts? = null

                        override suspend fun write(layouts: StreamLayouts) = Unit
                    },
                    CoroutineScope(Dispatchers.Unconfined + Job()),
                ),
            actions =
                object : StreamActionGateway {
                    override fun perform(
                        content: com.riffle.core.domain.launcher.stream.StreamContent,
                        action: com.riffle.core.domain.launcher.stream.StreamAction,
                    ): StreamActionResult = StreamActionResult.NotOffered
                },
            imageLoader = NoExpressionImageLoader,
        )

    private fun shortcut(name: String): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(name),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.$name"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = name,
        )

    private companion object {
        const val DOCKED_COUNT = 4
        const val STEPS = 10
        const val STEP_MILLIS = 16L
        const val PRESS_PX = 120f
        const val SLIDE_PX = 100f
        const val SETTLE_MILLIS = 1_000L
        const val WAIT_MILLIS = 5_000L
        const val END_INSET_PX = 4f
        const val EDGE_FRAMES = 60
        const val LAST_TILE = "Feeds"
        const val HOLD_PLUS_MILLIS = DockMotionConstants.DEFAULT_HOLD_MILLIS + 120L
        const val NOW = 1_767_225_600_000L
    }
}
