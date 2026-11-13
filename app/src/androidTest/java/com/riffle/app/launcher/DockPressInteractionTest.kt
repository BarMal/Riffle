package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pressing the Dock down and releasing (ADR 0003, Amendment 3, One gesture) through the real [HomeDestination] with a
 * Stream (over no sources): a bottom Dock pressed toward its edge past the threshold and lifted asks the shell to open the
 * Stream, the same press while the Stream shows asks for Home, a press that stops short does nothing, and the Dock's
 * accessibility actions do the same without a gesture. The shell is a recording lambda. (The press is measured as the
 * finger's travel at the lift, so each test drags in steps and lifts at the end; the travel is a fraction of the window
 * like [DockPullInteractionTest]'s into-the-edge pull, which already runs on the emulator's short screen.)
 */
@RunWith(AndroidJUnit4::class)
class DockPressInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val docked = (0 until DOCKED_COUNT).map { index -> shortcut("docked$index") }

    @Test
    fun pressingTheDockDownAndReleasingOpensTheStream() {
        val actions = setContent(streamShown = false)

        press(dyFraction = 0.2f)

        composeRule.runOnIdle { assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), actions) }
    }

    @Test
    fun theSamePressWhileTheStreamShowsReturnsHome() {
        val actions = setContent(streamShown = true)

        press(dyFraction = 0.2f)

        composeRule.runOnIdle { assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), actions) }
    }

    @Test
    fun aPressThatStopsShortOfTheThresholdDoesNothing() {
        val actions = setContent(streamShown = false)

        // Past touch slop (16 dp, so it is a press and not a tap) but short of the 28 dp threshold.
        press(dyFraction = 0.0f, dyPx = with(composeRule.density) { SHORT_PRESS_DP.dp.toPx() })

        composeRule.runOnIdle { assertTrue("a short press did something: $actions", actions.isEmpty()) }
    }

    @Test
    fun aPressDrawnBackBeforeTheLiftDoesNothing() {
        val actions = setContent(streamShown = false)
        val root = composeRule.onRoot().fetchSemanticsNode().size

        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput {
            down(center)
            repeat(STEPS) { moveBy(Offset(0f, root.height * 0.2f / STEPS), delayMillis = STEP_MILLIS) }
            repeat(STEPS) { moveBy(Offset(0f, -root.height * 0.2f / STEPS), delayMillis = STEP_MILLIS) }
            up()
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.waitForIdle()

        composeRule.runOnIdle { assertTrue("a withdrawn press did something: $actions", actions.isEmpty()) }
    }

    @Test
    fun theDockOffersShowLivePagesAndBackToHomeAsAccessibilityActions() {
        val open = setContent(streamShown = false)
        val labels = dockActionLabels()
        assertEquals(listOf("Open Library", "Show live pages"), labels)
        invoke("Show live pages")
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.runOnIdle { assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), open) }
    }

    @Test
    fun whileTheStreamShowsTheDockOffersBackToHome() {
        val actions = setContent(streamShown = true)
        assertEquals(listOf("Open Library", "Back to Home"), dockActionLabels())
        invoke("Back to Home")
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.runOnIdle { assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), actions) }
    }

    private fun dockActionLabels(): List<String> =
        composeRule
            .onNodeWithTag(HOME_DOCK_PULL_TEST_TAG)
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
            .map { action -> action.label }

    private fun invoke(label: String) {
        val action =
            composeRule
                .onNodeWithTag(HOME_DOCK_PULL_TEST_TAG)
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .single { it.label == label }
        composeRule.runOnUiThread { action.action() }
    }

    /** A press from the middle of the Dock toward its edge, by a fraction of the window's height (or [dyPx]). */
    private fun press(
        dyFraction: Float,
        dyPx: Float = 0f,
    ) {
        val root = composeRule.onRoot().fetchSemanticsNode().size
        val total = Offset(0f, root.height * dyFraction + dyPx)
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput {
            down(center)
            repeat(STEPS) { moveBy(total / STEPS.toFloat(), delayMillis = STEP_MILLIS) }
            up()
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.waitForIdle()
    }

    private fun setContent(streamShown: Boolean): MutableList<LauncherShellAction> {
        val actions = mutableListOf<LauncherShellAction>()
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
                    streamShown = streamShown,
                    onAction = { action -> actions += action },
                )
            }
        }
        composeRule.waitForIdle()
        return actions
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
        const val DOCK_CAPACITY = 4
        const val STEPS = 10
        const val STEP_MILLIS = 16L
        const val SETTLE_MILLIS = 1_000L
        const val SHORT_PRESS_DP = 22
        const val NOW = 1_767_225_600_000L
    }
}
