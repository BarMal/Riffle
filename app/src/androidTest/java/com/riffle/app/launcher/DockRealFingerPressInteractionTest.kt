package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * Closing the Stream by pressing the Dock the way a real finger does it (the owner's report: the Dock press did not
 * return Home on a phone). [DockPressInteractionTest] drags from the Dock's centre by a fifth of the window and lifts
 * within 160 ms; a person lands ON a tile or an app icon (the contextual row is edge to edge), drags a short way with
 * a wobble, rests and lifts, the touch lasting about half a second. These tests do that through the real
 * [HomeDestination]: the finger lands on a contextual tile or on a pinned app's icon in the open Stream's row, the
 * travel is bounded by the room left below the finger, and the time is the test clock's. The JVM twin is
 * `DockRealFingerPressTest`.
 */
@RunWith(AndroidJUnit4::class)
class DockRealFingerPressInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val docked = (0 until DOCKED_COUNT).map { index -> shortcut("docked$index") }
    private val sent = mutableListOf<LauncherShellAction>()
    private var shellShown by mutableStateOf(false)

    @Test
    fun aRealPressStartedOnAContextualTileReturnsHome() {
        setContent(streamShown = true)
        val took = realPress(landingOn(tileNodes()))
        // Build the report here: fetching nodes synchronises, which is not allowed inside runOnIdle.
        val why = report()
        composeRule.runOnIdle {
            assertTrue("the press was not a real one: $took ms", took >= MIN_REAL_PRESS_MILLIS)
            assertTrue("the Dock press did not return Home: $sent $why", LauncherShellAction.OpenHome in sent)
        }
    }

    @Test
    fun aRealPressStartedOnAPinnedAppInTheContextualRowReturnsHome() {
        setContent(streamShown = true)
        val pinned =
            composeRule.onAllNodes(
                SemanticsMatcher("a pinned app's icon") { node ->
                    node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("dock-item:") == true
                },
            ).fetchSemanticsNodes()
        realPress(landingOn(pinned))
        // Build the report here: fetching nodes synchronises, which is not allowed inside runOnIdle.
        val why = report()
        composeRule.runOnIdle {
            assertTrue("the Dock press did not return Home: $sent $why", LauncherShellAction.OpenHome in sent)
        }
    }

    @Test
    fun anUnhurriedPressHeldLongStillReturnsHome() {
        setContent(streamShown = true)
        realPress(landingOn(tileNodes()), dragMillis = 450L, restMillis = 600L)
        // Build the report here: fetching nodes synchronises, which is not allowed inside runOnIdle.
        val why = report()
        composeRule.runOnIdle {
            val message = "an unhurried press did not return Home: $sent $why"
            assertTrue(message, LauncherShellAction.OpenHome in sent)
        }
        assertTrue(
            "the Dock was left choosing ${report()}",
            composeRule.onAllNodesWithTag(DOCK_CHOICE_LABEL_TEST_TAG).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun aThumbThatDriftsAlongTheRowButStaysOnItsTileStillReturnsHome() {
        setContent(streamShown = true)
        realPress(landingOn(tileNodes()), driftDp = 18f)
        // Build the report here: fetching nodes synchronises, which is not allowed inside runOnIdle.
        val why = report()
        composeRule.runOnIdle {
            assertTrue("a drifting press did not return Home: $sent $why", LauncherShellAction.OpenHome in sent)
        }
    }

    @Test
    fun aMostlyHorizontalDragOnATileScrollsTheRowAndDoesNotPress() {
        setContent(streamShown = true)
        val landing = landingOn(tileNodes())
        val before = composeRule.onNodeWithTag(DOCK_CONTEXTUAL_ROW_TEST_TAG).fetchSemanticsNode().positionInRoot
        val tile = tileNodes().minByOrNull { abs(it.positionInRoot.x + it.size.width / 2f - landing.x) }!!
        val tileX = tile.positionInRoot.x
        val density = composeRule.density.density
        composeRule.onRoot().performTouchInput {
            down(landing)
            // 120 dp along the row with a little downward drift (well short of a press's 28 dp).
            repeat(STEPS) { step ->
                moveTo(landing + Offset(-120f * density * (step + 1) / STEPS, 6f * density * (step + 1) / STEPS), 16L)
            }
            up()
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.waitForIdle()
        val moved =
            composeRule.onAllNodesWithTag(tile.config[SemanticsProperties.TestTag]).fetchSemanticsNodes().firstOrNull()
        val scrolled = moved == null || abs(moved.positionInRoot.x - tileX) > 20f
        assertTrue("the row did not scroll: $before ${report()}", scrolled)
        // Build the report here: fetching nodes synchronises, which is not allowed inside runOnIdle.
        val why = report()
        composeRule.runOnIdle { assertTrue("a horizontal drag pressed the Dock: $sent $why", sent.isEmpty()) }
    }

    @Test
    fun aRealPressFromHomeStillOpensTheStream() {
        setContent(streamShown = false)
        val dock = composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).fetchSemanticsNode()
        realPress(Offset(dock.positionInRoot.x + dock.size.width / 2f, dock.positionInRoot.y + dock.size.height / 2f))
        composeRule.runOnIdle { assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), sent) }
    }

    private var lastLanding: Offset? = null
    private var rowProbeBefore: Float? = null
    private var rowProbeAfter: Float? = null

    /** Where the first contextual tile in view starts along the row: how far the row has scrolled, as a position. */
    private fun rowProbeX(): Float? = tileNodes().firstOrNull()?.positionInRoot?.x

    /** What the Dock decided (claimed or not, what started choosing, the selection, the outcome) and what moved. */
    private fun report(): String {
        val dock = composeRule.onAllNodesWithTag(HOME_DOCK_PULL_TEST_TAG).fetchSemanticsNodes().firstOrNull()
        val decided = dock?.config?.getOrNull(DockPressDiagnosticsKey)?.describe() ?: "no Dock diagnostics"
        val root = composeRule.onRoot().fetchSemanticsNode().size
        return "[landing=$lastLanding root=${root.width}x${root.height} density=${composeRule.density.density} " +
            "rowProbeX before=$rowProbeBefore after=$rowProbeAfter | $decided]"
    }

    private fun tileNodes(): List<SemanticsNode> =
        TILES.flatMap { label ->
            composeRule.onAllNodesWithTag(dockDynamicSectionTileTestTag(label)).fetchSemanticsNodes()
        }

    /** The centre of the most central node whose whole extent is on screen (a clipped one is half under the edge). */
    private fun landingOn(nodes: List<SemanticsNode>): Offset {
        val width = composeRule.onRoot().fetchSemanticsNode().size.width
        val node =
            nodes
                .filter { candidate ->
                    candidate.positionInRoot.x >= 0f && candidate.positionInRoot.x + candidate.size.width <= width
                }
                .minByOrNull { candidate -> abs(candidate.positionInRoot.x + candidate.size.width / 2f - width / 2f) }
        assertNotNull("nothing to land on is in view", node)
        return Offset(node!!.positionInRoot.x + node.size.width / 2f, node.positionInRoot.y + node.size.height / 2f)
    }

    /**
     * A real press: lands on [landing] (root coordinates), drags down over [dragMillis] with a thumb's wobble
     * ([driftDp] more sideways by the end), rests for [restMillis] with a tremor and lifts. The travel is the smaller
     * of 44 dp and what is left above the bottom of the screen. Returns how long the touch took on the test clock.
     */
    private fun realPress(
        landing: Offset,
        dragMillis: Long = 350L,
        restMillis: Long = 150L,
        driftDp: Float = 0f,
    ): Long {
        val density = composeRule.density.density
        val rootHeight = composeRule.onRoot().fetchSemanticsNode().size.height
        val travelPx = min(WANT_DP * density, rootHeight - landing.y - SCREEN_EDGE_MARGIN_DP * density)
        assertTrue("no room for a real press: ${travelPx / density} dp", travelPx / density >= REACHABLE_DP)
        lastLanding = landing
        rowProbeBefore = rowProbeX()
        val random = Random(SEED)
        val started = composeRule.mainClock.currentTime
        composeRule.onRoot().performTouchInput { down(landing) }
        val steps = (dragMillis / STEP_MILLIS).toInt()
        for (step in 1..steps) {
            val fraction = step / steps.toFloat()
            val eased = fraction * fraction * (3f - 2f * fraction)
            val wobble = (random.nextFloat() - 0.5f) * 2f * WOBBLE_DP * density
            val target = landing + Offset(wobble + driftDp * density * eased, travelPx * eased)
            composeRule.onRoot().performTouchInput { moveTo(target, delayMillis = STEP_MILLIS) }
            composeRule.mainClock.advanceTimeBy(STEP_MILLIS)
        }
        for (step in 1..(restMillis / STEP_MILLIS).toInt()) {
            val tremor = Offset((random.nextFloat() - 0.5f) * density, (random.nextFloat() - 0.5f) * density)
            val target = landing + Offset(driftDp * density, travelPx) + tremor
            composeRule.onRoot().performTouchInput { moveTo(target, delayMillis = STEP_MILLIS) }
            composeRule.mainClock.advanceTimeBy(STEP_MILLIS)
        }
        composeRule.onRoot().performTouchInput { up() }
        val took = composeRule.mainClock.currentTime - started
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.waitForIdle()
        rowProbeAfter = rowProbeX()
        return took
    }

    private fun setContent(streamShown: Boolean) {
        shellShown = streamShown
        val layout =
            HomeLayoutDefaults.standard().let { standard ->
                standard.copy(
                    viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                    dock = standard.dock.copy(capacity = DOCK_CAPACITY, items = docked),
                )
            }
        val state =
            LauncherShellState(
                homeLayout = layout,
                installedApps = docked.map { item -> InstalledApp(identity = item.appIdentity, label = item.label) },
            )
        val host = streamHost()
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
        if (streamShown) {
            composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { tileNodes().isNotEmpty() }
            composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        }
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
        val TILES = listOf("Now", "Recents", "Agenda", "Tasks", "Feeds")
        const val DOCKED_COUNT = 4
        const val DOCK_CAPACITY = 4
        const val SEED = 17
        const val STEPS = 10
        const val STEP_MILLIS = 16L
        const val SETTLE_MILLIS = 1_000L
        const val WAIT_MILLIS = 5_000L
        const val WOBBLE_DP = 3f
        const val WANT_DP = 44f
        const val MIN_REAL_PRESS_MILLIS = 450L

        /** How close to the bottom of the screen a thumb goes. */
        const val SCREEN_EDGE_MARGIN_DP = 8f

        /** The Dock's own press threshold, with a margin: the room has to be there for a press to be possible. */
        const val REACHABLE_DP = 32f
        const val NOW = 1_767_225_600_000L
    }
}
