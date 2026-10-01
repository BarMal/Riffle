package com.riffle.app.screenshots.containers

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.workspace.WORKSPACE_PREVIEW_DOCK_TEST_TAG
import com.riffle.app.launcher.workspace.WORKSPACE_PREVIEW_EXIT_TEST_TAG
import com.riffle.app.launcher.workspace.WORKSPACE_PREVIEW_FINDER_CLOSE_TEST_TAG
import com.riffle.app.launcher.workspace.WORKSPACE_PREVIEW_FINDER_TEST_TAG
import com.riffle.app.launcher.workspace.WORKSPACE_PREVIEW_HOME_PLACEHOLDER_TEST_TAG
import com.riffle.app.launcher.workspace.WorkspacePreviewSurface
import com.riffle.app.screenshots.ScreenshotBackdrop
import com.riffle.app.screenshots.ScreenshotDevices
import com.riffle.app.screenshots.captureScreen
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageRole
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceSourceIds
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Workspaces (preview) surface over fixed lens results: a bound page then a page-set, the honest
 * `home.grid` placeholder, and the loading state, at compact and unfolded widths. Nothing subscribes; the
 * surface is drawn directly (it is only reachable behind the developer setting in the real shell).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class WorkspacePreviewScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val homeGridPage =
        PageContainer(
            ContainerId("home"),
            PageContent.Bound(
                LensBinding(Lens(sources = listOf(WorkspaceSourceIds.HOME_GRID)), ExpressionKind.ICON_GRID),
            ),
        )
    private val dataWorkspace =
        Workspace(WorkspaceId("data"), "Nova", listOf(ContainerFixtures.boundPage, ContainerFixtures.pageSet))
    private val homeWorkspace =
        Workspace(WorkspaceId("home"), "Nova", listOf(homeGridPage, ContainerFixtures.boundPage))
    private val finderPage = ContainerFixtures.boundPage.copy(id = ContainerId("finder"), role = PageRole.FINDER)

    // The Finder is not in the pager: here it is the start page, so it opens over the first pager page.
    private val finderStartWorkspace =
        Workspace(
            WorkspaceId("finder-start"),
            "Nova",
            listOf(ContainerFixtures.pageSet, finderPage),
            startPageId = ContainerId("finder"),
        )
    private var exits = 0

    private fun render(workspace: Workspace?) {
        composeRule.setContent {
            ScreenshotBackdrop {
                WorkspacePreviewSurface(
                    workspace = workspace,
                    servicesFor = { padding ->
                        ContainerFixtures.services.copy(
                            environment = ContainerFixtures.services.environment.copy(contentPadding = padding),
                        )
                    },
                    menu = null,
                    reducedMotion = true,
                    navigation = null,
                    onNavigationConsumed = {},
                    onExit = { exits++ },
                )
            }
        }
        composeRule.captureScreen()
    }

    @Test
    fun boundPageCompact() {
        render(dataWorkspace)
        composeRule.onNodeWithTag(WORKSPACE_PREVIEW_DOCK_TEST_TAG).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun boundPageUnfolded() {
        render(dataWorkspace)
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun boundPageCompactDark() {
        render(dataWorkspace)
    }

    @Test
    fun homeGridPageShowsThePlaceholderCompact() {
        render(homeWorkspace)
        composeRule.onNodeWithTag(WORKSPACE_PREVIEW_HOME_PLACEHOLDER_TEST_TAG).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun homeGridPageShowsThePlaceholderUnfolded() {
        render(homeWorkspace)
    }

    @Test
    fun finderAsStartPageOpensItsOwnSurfaceCompact() {
        render(finderStartWorkspace)
        composeRule.onNodeWithTag(WORKSPACE_PREVIEW_FINDER_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(WORKSPACE_PREVIEW_FINDER_CLOSE_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun closingTheFinderLeavesThePagerWithoutExiting() {
        render(finderStartWorkspace)
        composeRule.onNodeWithTag(WORKSPACE_PREVIEW_FINDER_CLOSE_TEST_TAG).performClick()
        composeRule.onNodeWithTag(WORKSPACE_PREVIEW_FINDER_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(WORKSPACE_PREVIEW_DOCK_TEST_TAG).assertIsDisplayed()
        assertEquals(0, exits)
    }

    @Test
    fun loadingCompact() {
        render(null)
    }

    @Test
    fun exitIsAlwaysAvailableAndCallsBack() {
        render(null)
        composeRule.onNodeWithTag(WORKSPACE_PREVIEW_EXIT_TEST_TAG).assertIsDisplayed().performClick()
        assertEquals(1, exits)
    }
}
