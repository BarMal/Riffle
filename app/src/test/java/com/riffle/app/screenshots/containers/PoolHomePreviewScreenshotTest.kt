package com.riffle.app.screenshots.containers

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.EmptyAppIconLoader
import com.riffle.app.launcher.pool.POOL_HOME_EMPTY_TEST_TAG
import com.riffle.app.launcher.pool.POOL_HOME_PAGE_TEST_TAG
import com.riffle.app.launcher.pool.POOL_HOME_WIDGET_PLACEHOLDER
import com.riffle.app.launcher.pool.PlacedHomeContent
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.app.launcher.workspace.WORKSPACE_PREVIEW_HOME_PLACEHOLDER_TEST_TAG
import com.riffle.app.launcher.workspace.WORKSPACE_PREVIEW_REIMPORT_TEST_TAG
import com.riffle.app.launcher.workspace.WorkspacePreviewSurface
import com.riffle.app.screenshots.ScreenshotBackdrop
import com.riffle.app.screenshots.ScreenshotDevices
import com.riffle.app.screenshots.captureScreen
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLabelSettings
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
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
 * The Workspaces (preview) home page drawing the user's real placed items from the pool: apps, a folder, a
 * widget placeholder, the empty notice, and the interactions that exist (open an app, open a folder, refresh).
 * Nothing subscribes or reads storage; the surface is only reachable behind the developer setting.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class PoolHomePreviewScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val homeGridPage =
        PageContainer(
            ContainerId("home"),
            PageContent.Bound(
                LensBinding(Lens(sources = listOf(WorkspaceSourceIds.HOME_GRID)), ExpressionKind.ICON_GRID),
            ),
        )
    private val workspace = Workspace(WorkspaceId("home"), "Nova", listOf(homeGridPage))
    private val opened = mutableListOf<String>()
    private var reimports = 0

    private fun identity(name: String) = AppIdentity(AppPackageName("com.$name"), AppActivityName("com.$name.Main"))

    private fun cell(
        column: Int,
        row: Int,
        columns: Int = 1,
        rows: Int = 1,
    ) = GridPlacement(GridCell(column, row), GridSpan(columns, rows))

    private fun app(
        label: String,
        column: Int,
        row: Int,
    ) = AppShortcutItem(LauncherItemId("app:$label"), identity(label.lowercase()), label, placement = cell(column, row))

    private val items: List<LauncherItem> =
        listOf(
            app("Phone", 0, 0),
            app("Camera", 1, 0),
            app("Maps", 2, 0),
            FolderItem(
                LauncherItemId("folder:social"),
                "Social",
                listOf(
                    AppShortcutItem(LauncherItemId("entry:mail"), identity("mail"), "Mail"),
                    AppShortcutItem(LauncherItemId("entry:chat"), identity("chat"), "Chat"),
                ),
                cell(3, 0),
            ),
            WidgetItem(LauncherItemId("widget:clock"), HostedWidgetId(42), "Clock", placement = cell(0, 1, 4, 2)),
        )

    private fun page(): LauncherPage =
        LauncherPage(LauncherPageId("home"), LauncherPageType.Home, GridDimensions(4, 6), items)

    private fun home(
        page: LauncherPage?,
        withReimport: Boolean = false,
    ) = PlacedHomeContent(
        resolve = { page },
        iconLoader = EmptyAppIconLoader,
        widgetViews = EmptyHomeWidgetViewFactory,
        labelSettings = HomeLabelSettings.standard(),
        onOpen = { opened += it.label },
        onReimport = if (withReimport) ({ reimports++ }) else null,
    )

    private fun render(
        placedHome: PlacedHomeContent?,
        capture: Boolean = true,
    ) {
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
                    onExit = {},
                    placedHome = placedHome,
                )
            }
        }
        if (capture) composeRule.captureScreen()
    }

    @Test
    fun realItemsCompact() {
        render(home(page()))
        composeRule.onNodeWithTag(POOL_HOME_PAGE_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Phone").assertIsDisplayed()
        composeRule.onNodeWithText("Social").assertIsDisplayed()
        composeRule.onNodeWithText(POOL_HOME_WIDGET_PLACEHOLDER).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun realItemsUnfolded() {
        render(home(page()))
        composeRule.onNodeWithTag(POOL_HOME_PAGE_TEST_TAG).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun realItemsCompactDark() {
        render(home(page()))
    }

    @Test
    fun emptyPoolShowsTheNotice() {
        render(home(null))
        composeRule.onNodeWithTag(POOL_HOME_EMPTY_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun withoutAPoolTheOldPlaceholderStays() {
        render(null)
        composeRule.onNodeWithTag(WORKSPACE_PREVIEW_HOME_PLACEHOLDER_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun tappingAnAppOpensIt() {
        render(home(page()), capture = false)
        composeRule.onNodeWithText("Camera").performClick()
        assertEquals(listOf("Camera"), opened)
    }

    @Test
    fun tappingAFolderListsItsAppsAndTappingOneOpensIt() {
        render(home(page()), capture = false)
        composeRule.onNodeWithText("Social").performClick()
        composeRule.onNode(hasText("Social") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        composeRule.onNode(hasText("Chat") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("Chat"), opened)
    }

    @Test
    fun theRefreshActionIsOfferedOnlyWithAReimportCallback() {
        render(home(page(), withReimport = true), capture = false)
        composeRule.onNodeWithTag(WORKSPACE_PREVIEW_REIMPORT_TEST_TAG).performClick()
        assertEquals(1, reimports)
    }
}
