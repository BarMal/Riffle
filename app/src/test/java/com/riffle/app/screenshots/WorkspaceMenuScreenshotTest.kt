package com.riffle.app.screenshots

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.WORKSPACE_MENU_FALLBACK_TEST_TAG
import com.riffle.app.launcher.WORKSPACE_MENU_HANDLE_TEST_TAG
import com.riffle.app.launcher.WORKSPACE_MENU_PANEL_TEST_TAG
import com.riffle.app.launcher.WorkspaceMenuHost
import com.riffle.app.launcher.WorkspaceMenuLayer
import com.riffle.app.launcher.WorkspaceMenuUiState
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutCapabilities
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageHost
import com.riffle.core.domain.launcher.workspace.PageRole
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.menu.PageSetGroupRef
import com.riffle.core.domain.launcher.workspace.menu.WorkspaceMenuAction
import com.riffle.core.domain.launcher.workspace.menu.WorkspaceMenuPlanner
import com.riffle.core.domain.launcher.workspace.menu.WorkspaceMenuState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The dock's workspace menu (#1351) drawn over the stand-in wallpaper: open with several workspaces,
 * a Finder and a page-set; a single workspace with no Finder; the fall-back notice; the closed handle;
 * and the unfolded layout with the dock on the left edge. The menu layer is drawn directly (the
 * feature is off in the real shell), with the dock's thickness passed as a fixed extent.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class WorkspaceMenuScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val lens = Lens(sources = listOf(SourceId("apps")))
    private val grouped = Lens(sources = listOf(SourceId("notifications")), group = LensGroup.ByGroupKey)

    private fun page(
        id: String,
        expression: ExpressionKind = ExpressionKind.LIST,
        role: PageRole = PageRole.STANDARD,
    ) = PageContainer(ContainerId(id), PageContent.Bound(LensBinding(lens, expression)), role)

    private fun workspace(
        id: String,
        name: String,
        vararg pages: PageHost,
    ) = Workspace(WorkspaceId(id), name, pages.toList())

    private val now = page("now")
    private val inbox = PageSetContainer(ContainerId("inbox"), LensBinding(grouped, ExpressionKind.CARD_STACK))
    private val finder = page("finder", ExpressionKind.ALPHA_LIST, PageRole.FINDER)

    private val groups =
        mapOf(ContainerId("inbox") to listOf(PageSetGroupRef("mail", "Mail"), PageSetGroupRef("chat", "Chat")))

    private fun layoutOf(
        activeId: String,
        vararg workspaces: Workspace,
    ) = WorkspaceSet(
        mapOf(
            HomeLayoutDeviceClass.PHONE to
                LayoutWorkspaces(workspaces.toList(), WorkspaceId(activeId), workspaces.first().id),
        ),
    )

    private val full =
        layoutOf(
            "timescape",
            workspace("home", "Home", now),
            workspace("timescape", "TimeScape", now, inbox, finder),
            workspace("work", "Work", now),
        )

    private fun render(
        set: WorkspaceSet,
        edge: DockPosition = DockPosition.BOTTOM,
        open: Boolean = true,
        capabilities: LayoutCapabilities = LayoutCapabilities(),
        actions: MutableList<WorkspaceMenuAction> = mutableListOf(),
    ) {
        val model = WorkspaceMenuPlanner.plan(set, HomeLayoutDeviceClass.PHONE, capabilities, groups)
        composeRule.setContent {
            ScreenshotBackdrop {
                WorkspaceMenuLayer(
                    host =
                        WorkspaceMenuHost(
                            state =
                                if (open) {
                                    WorkspaceMenuUiState(WorkspaceMenuState(isOpen = true), model)
                                } else {
                                    WorkspaceMenuUiState()
                                },
                            onAction = { actions += it },
                        ),
                    dockEdge = edge,
                    dockExtent = DOCK_EXTENT,
                    reducedMotion = true,
                )
            }
        }
    }

    @Test
    fun openCompactWithFinderAndPageSet() {
        render(full)
        composeRule.onNodeWithTag(WORKSPACE_MENU_PANEL_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Finder").assertIsDisplayed()
        composeRule.captureScreen()
    }

    @Test
    @Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE + ScreenshotDevices.NIGHT)
    fun openCompactDark() {
        render(full)
        composeRule.captureScreen()
    }

    @Test
    fun singleWorkspaceWithoutFinder() {
        render(layoutOf("home", workspace("home", "Home", now)))
        composeRule.onNodeWithText("Edit workspace").assertIsDisplayed()
        composeRule.onNodeWithText("Finder").assertDoesNotExist()
        composeRule.captureScreen()
    }

    @Test
    fun fallbackNoticeWhenTheActiveWorkspaceCannotBeDrawn() {
        val set =
            layoutOf(
                "fancy",
                workspace("home", "Home", now),
                workspace("fancy", "Fancy", page("stack", ExpressionKind.CARD_STACK)),
            )
        render(set, capabilities = LayoutCapabilities(setOf(ExpressionKind.LIST)))
        composeRule.onNodeWithTag(WORKSPACE_MENU_FALLBACK_TEST_TAG).assertIsDisplayed()
        composeRule.captureScreen()
    }

    @Test
    fun closedShowsTheHandleAndItOpens() {
        val actions = mutableListOf<WorkspaceMenuAction>()
        render(full, open = false, actions = actions)
        composeRule.onNodeWithTag(WORKSPACE_MENU_HANDLE_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(WORKSPACE_MENU_HANDLE_TEST_TAG).performClick()
        assertEquals(listOf<WorkspaceMenuAction>(WorkspaceMenuAction.Open), actions)
        composeRule.captureScreen()
    }

    @Test
    fun choosingRowsReportsActions() {
        val actions = mutableListOf<WorkspaceMenuAction>()
        render(full, actions = actions)
        composeRule.onNodeWithText("Work").performClick()
        composeRule.onNodeWithText("Finder").performClick()
        composeRule.onNodeWithText("Edit workspace").performClick()
        assertEquals(
            listOf(
                WorkspaceMenuAction.SwitchWorkspace(WorkspaceId("work")),
                WorkspaceMenuAction.OpenFinder,
                WorkspaceMenuAction.EditWorkspace,
            ),
            actions,
        )
    }

    @Test
    @Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun openUnfoldedWithTheDockOnTheLeftEdge() {
        render(full, edge = DockPosition.LEFT)
        composeRule.onNodeWithTag(WORKSPACE_MENU_PANEL_TEST_TAG).assertIsDisplayed()
        composeRule.captureScreen()
    }

    @Test
    @Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun openUnfoldedSingleWorkspaceWithoutFinderOnTheRightEdge() {
        render(layoutOf("home", workspace("home", "Home", now)), edge = DockPosition.RIGHT)
        composeRule.captureScreen()
    }

    private companion object {
        val DOCK_EXTENT = 72.dp
    }
}
