package com.riffle.app.screenshots

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.riffle.app.launcher.HOME_DOCK_PULL_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.WORKSPACE_MENU_ACTION_LABEL
import com.riffle.app.launcher.WorkspaceMenuHost
import com.riffle.app.launcher.WorkspaceMenuUiState
import com.riffle.core.domain.launcher.workspace.menu.WorkspaceMenuAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The dock's accessibility actions are one list: "Open Library" and "Open Stream" from the pull, and "Workspace menu"
 * (reachable until P8). Two semantics modifiers on one node do not merge their custom actions, so a regression here
 * would silently drop one set from TalkBack.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockAccessibilityActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theDockOffersTheWorkspaceMenuAlongsideTheLibraryAndStreamActions() {
        val menuActions = mutableListOf<WorkspaceMenuAction>()
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = ScreenshotFixtures.homeState(),
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = emptyStreamHost(),
                    workspaceMenu = WorkspaceMenuHost(WorkspaceMenuUiState(), { menuActions += it }),
                    onAction = {},
                )
            }
        }

        val actions =
            composeRule
                .onNodeWithTag(HOME_DOCK_PULL_TEST_TAG)
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
        assertEquals(listOf("Open Library", "Open Stream", WORKSPACE_MENU_ACTION_LABEL), actions.map { it.label })
        assertTrue(menuActions.isEmpty())
    }
}
