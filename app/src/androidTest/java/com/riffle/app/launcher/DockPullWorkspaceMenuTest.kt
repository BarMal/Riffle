package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
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
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.workspace.menu.WorkspaceMenuAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S9 (design 11.5, owner decision Q9): with the workspace menu present, the dock pull opens it instead of switching
 * between Home and Library. The claim rules are the mode pull's, so a pull into the edge or a drag along the dock's run
 * still does nothing else; the accessibility action is "Workspace menu" and the mode switch action is gone.
 */
@RunWith(AndroidJUnit4::class)
class DockPullWorkspaceMenuTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val docked = (0 until DOCKED_COUNT).map { index -> shortcut("docked$index") }
    private val menuActions = mutableListOf<WorkspaceMenuAction>()

    @Test
    fun aPullUpOpensTheWorkspaceMenuAndSwitchesNoMode() {
        val actions = setContent()

        pull(dyFraction = -0.6f)

        composeRule.runOnIdle {
            assertEquals(listOf<WorkspaceMenuAction>(WorkspaceMenuAction.Open), menuActions)
            assertTrue(actions.none { it is LauncherShellAction.SelectLauncherViewMode })
        }
    }

    @Test
    fun aPullIntoTheEdgeOpensNothing() {
        val actions = setContent()

        pull(dyFraction = 0.2f)

        composeRule.runOnIdle {
            assertTrue(menuActions.isEmpty())
            assertTrue(actions.none { it is LauncherShellAction.SelectLauncherViewMode })
        }
    }

    @Test
    fun aDragAlongTheRunOpensNothing() {
        setContent()

        pull(dyFraction = 0f, dxFraction = -0.4f)

        composeRule.runOnIdle { assertTrue(menuActions.isEmpty()) }
    }

    @Test
    fun whileTheWorkspacesLoadThePullIsInertAndSwitchesNoMode() {
        val actions = setContent(withMenu = false, modePullEnabled = false)

        pull(dyFraction = -0.6f)

        composeRule.runOnIdle {
            assertTrue(menuActions.isEmpty())
            assertTrue(actions.none { it is LauncherShellAction.SelectLauncherViewMode })
        }
    }

    @Test
    fun theDockOffersTheMenuAndNoModeSwitchAsAccessibilityActions() {
        setContent()
        val labels =
            composeRule
                .onNodeWithTag(HOME_DOCK_PULL_TEST_TAG)
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .map { it.label }

        assertTrue(WORKSPACE_MENU_ACTION_LABEL in labels)
        assertTrue(DOCK_PULL_TO_LIBRARY_LABEL !in labels)
    }

    private fun pull(
        dyFraction: Float,
        dxFraction: Float = 0f,
    ) {
        val root = composeRule.onRoot().fetchSemanticsNode().size
        val total = Offset(root.width * dxFraction, root.height * dyFraction)
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput {
            down(center)
            repeat(STEPS) { moveBy(total / STEPS.toFloat(), delayMillis = STEP_MILLIS) }
            up()
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.waitForIdle()
    }

    private fun setContent(
        withMenu: Boolean = true,
        modePullEnabled: Boolean = true,
    ): MutableList<LauncherShellAction> {
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
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDockModePullEnabled provides modePullEnabled) {
                    HomeDestination(
                        state = state,
                        appIconLoader = EmptyAppIconLoader,
                        workspaceMenu =
                            WorkspaceMenuHost(
                                WorkspaceMenuUiState(),
                                onAction = { menuActions += it },
                            ).takeIf { withMenu },
                        onAction = { action -> actions += action },
                    )
                }
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
        const val DOCKED_COUNT = 8
        const val DOCK_CAPACITY = 3
        const val STEPS = 10
        const val STEP_MILLIS = 16L
        const val SETTLE_MILLIS = 1_000L
    }
}
