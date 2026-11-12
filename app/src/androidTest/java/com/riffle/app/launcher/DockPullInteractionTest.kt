package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.workspace.menu.WorkspaceMenuAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The dock pull (ADR 0003, The Dock is the handle) through the real [HomeDestination], with no Stream (so the
 * dock has no dynamic side and every pull starts on the pinned side): a bottom dock pulled up far or fast enough
 * opens the Library through the shell's OpenAppDrawer; a short slow pull springs back; a pull into the edge or along
 * the dock's run does nothing but what it did before; the pull no longer opens the workspace menu; and the dock's
 * accessibility action performs the same open. Pulls from the dynamic side (the Stream) are covered by
 * DockPullScreenshotTest, which runs on the JVM.
 *
 * The shell is a recording lambda, so an open shows up as the one action it was asked for.
 */
@RunWith(AndroidJUnit4::class)
class DockPullInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val docked = (0 until DOCKED_COUNT).map { index -> shortcut("docked$index") }

    @Test
    fun aLongPullUpOpensTheLibrary() {
        val actions = setContent()

        pull(dyFraction = -0.6f, stepDelayMillis = FAST_STEP_MILLIS)

        composeRule.runOnIdle {
            assertEquals(listOf(OPEN_LIBRARY), actions)
        }
    }

    @Test
    fun aShortSlowPullSpringsBackWithoutOpening() {
        val actions = setContent()
        val restingBounds = dockBounds()

        pull(dyFraction = -0.1f, stepDelayMillis = SLOW_STEP_MILLIS)

        composeRule.runOnIdle { assertTrue("a short pull opened something: $actions", actions.isEmpty()) }
        assertEquals("the dock did not spring back", restingBounds, dockBounds())
    }

    @Test
    fun aPullIntoTheEdgeDoesNothing() {
        val actions = setContent()

        pull(dyFraction = 0.2f, stepDelayMillis = FAST_STEP_MILLIS)

        composeRule.runOnIdle { assertTrue("a pull into the edge opened something: $actions", actions.isEmpty()) }
    }

    // A side dock's pull is perpendicular to it: a right-edge dock is pulled left, and a vertical
    // swipe along it (the old dock swipe-up) must not switch mode.
    @Test
    fun aRightDockPulledLeftOpensTheLibrary() {
        val actions = setContent(DockPosition.RIGHT)

        pull(dyFraction = 0f, dxFraction = -0.6f, stepDelayMillis = FAST_STEP_MILLIS)

        composeRule.runOnIdle {
            assertEquals(listOf(OPEN_LIBRARY), actions)
        }
    }

    @Test
    fun aVerticalSwipeAlongARightDockDoesNotOpen() {
        val actions = setContent(DockPosition.RIGHT)

        pull(dyFraction = -0.6f, stepDelayMillis = FAST_STEP_MILLIS)

        composeRule.runOnIdle {
            assertTrue(
                "a swipe along a side dock opened the Library: $actions",
                actions.none { action -> action == LauncherShellAction.OpenAppDrawer },
            )
        }
    }

    @Test
    fun aDragAlongTheRunStillScrollsTheDock() {
        val actions = setContent()
        val firstItem = composeRule.onNodeWithTag(dockItemTestTag(docked.first().id))
        val before = firstItem.fetchSemanticsNode().boundsInRoot.left

        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput {
            down(center)
            repeat(STEPS) { moveBy(Offset(-width / 2f / STEPS, 0f), delayMillis = FAST_STEP_MILLIS) }
            up()
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)

        composeRule.runOnIdle { assertTrue("a run drag opened something: $actions", actions.isEmpty()) }
        val after = firstItem.fetchSemanticsNode().boundsInRoot.left
        assertTrue("the dock run did not scroll ($before -> $after)", after < before)
    }

    @Test
    fun theDockOffersTheLibraryAsAnAccessibilityAction() {
        val actions = setContent()
        val open =
            composeRule
                .onNodeWithTag(HOME_DOCK_PULL_TEST_TAG)
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .single { action -> action.label == "Open Library" }

        composeRule.runOnUiThread { open.action() }
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)

        composeRule.runOnIdle {
            assertEquals(listOf(OPEN_LIBRARY), actions)
        }
    }

    @Test
    fun thePullNoLongerOpensTheWorkspaceMenuButItsActionStaysReachable() {
        val menuActions = mutableListOf<WorkspaceMenuAction>()
        val actions = setContent(workspaceMenu = WorkspaceMenuHost(WorkspaceMenuUiState(), { menuActions += it }))

        val labels =
            composeRule
                .onNodeWithTag(HOME_DOCK_PULL_TEST_TAG)
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .map { action -> action.label }
        pull(dyFraction = -0.6f, stepDelayMillis = FAST_STEP_MILLIS)

        assertTrue(WORKSPACE_MENU_ACTION_LABEL in labels)
        composeRule.runOnIdle {
            assertTrue(menuActions.isEmpty())
            assertEquals(listOf(OPEN_LIBRARY), actions)
        }
    }

    /**
     * A pull from the middle of the dock by fractions of the window's width and height (negative is
     * left / up).
     */
    private fun pull(
        dyFraction: Float,
        stepDelayMillis: Long,
        dxFraction: Float = 0f,
    ) {
        val root = composeRule.onRoot().fetchSemanticsNode().size
        val total = Offset(root.width * dxFraction, root.height * dyFraction)
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput {
            down(center)
            repeat(STEPS) { moveBy(total / STEPS.toFloat(), delayMillis = stepDelayMillis) }
            up()
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.waitForIdle()
    }

    private fun dockBounds() = composeRule.onNodeWithTag(HOME_DOCK_TEST_TAG).fetchSemanticsNode().boundsInRoot

    private fun setContent(
        position: DockPosition = DockPosition.BOTTOM,
        launcherSettings: LauncherSettings = LauncherSettings(),
        workspaceMenu: WorkspaceMenuHost? = null,
    ): MutableList<LauncherShellAction> {
        val actions = mutableListOf<LauncherShellAction>()
        val layout =
            HomeLayoutDefaults.standard().let { standard ->
                standard.copy(
                    viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                    dock = standard.dock.copy(position = position, capacity = DOCK_CAPACITY, items = docked),
                )
            }
        val state =
            LauncherShellState(
                homeLayout = layout,
                installedApps = docked.map { item -> InstalledApp(identity = item.appIdentity, label = item.label) },
                launcherSettings = launcherSettings,
            )
        composeRule.setContent {
            MaterialTheme {
                HomeDestination(
                    state = state,
                    appIconLoader = EmptyAppIconLoader,
                    workspaceMenu = workspaceMenu,
                    onAction = { action -> actions += action },
                )
            }
        }
        composeRule.waitForIdle()
        return actions
    }

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
        val OPEN_LIBRARY = LauncherShellAction.OpenAppDrawer
        const val DOCKED_COUNT = 8
        const val DOCK_CAPACITY = 3
        const val STEPS = 10
        const val FAST_STEP_MILLIS = 16L
        const val SLOW_STEP_MILLIS = 120L
        const val SETTLE_MILLIS = 1_000L
    }
}
