package com.riffle.app.screenshots.containers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.riffle.app.launcher.EmptyAppIconLoader
import com.riffle.app.launcher.pool.CachedPoolRepository
import com.riffle.app.launcher.pool.POOL_EDIT_BAR_TEST_TAG
import com.riffle.app.launcher.pool.POOL_EDIT_DONE_TEST_TAG
import com.riffle.app.launcher.pool.POOL_EDIT_ENTER_TEST_TAG
import com.riffle.app.launcher.pool.POOL_EDIT_ITEM_TEST_TAG_PREFIX
import com.riffle.app.launcher.pool.POOL_EDIT_NOTICE_TEST_TAG
import com.riffle.app.launcher.pool.POOL_EDIT_PANEL_TEST_TAG
import com.riffle.app.launcher.pool.POOL_EDIT_UNDO_TEST_TAG
import com.riffle.app.launcher.pool.PlacedHomeContent
import com.riffle.app.launcher.pool.PoolEditController
import com.riffle.app.launcher.pool.PoolEditText
import com.riffle.app.launcher.pool.PoolEditUi
import com.riffle.app.launcher.pool.PoolStorePort
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.app.launcher.workspace.WORKSPACE_PREVIEW_REIMPORT_TEST_TAG
import com.riffle.app.launcher.workspace.WorkspacePreviewSurface
import com.riffle.app.screenshots.ScreenshotBackdrop
import com.riffle.app.screenshots.ScreenshotDevices
import com.riffle.app.screenshots.captureScreen
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLabelSettings
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceSourceIds
import com.riffle.core.domain.launcher.workspace.pool.PoolHomeView
import com.riffle.core.domain.launcher.workspace.pool.PoolStoreState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Edit mode of the preview's home page over a real in-memory pool: entering it, the notice that edits do not change
 * the standard home, selecting and removing an item, Undo, and the two confirmations. No text field is typed into.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class PoolEditScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class MemoryStore : PoolStorePort {
        override suspend fun read(): PoolStoreState? = null

        override suspend fun write(state: PoolStoreState) = Unit
    }

    private val phone = HomeLayoutDeviceClass.PHONE
    private val mode = LauncherViewMode.HOME_SCREEN_LIBRARY
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val repository = CachedPoolRepository(MemoryStore())
    private val controller = PoolEditController(repository, scope, {}, debounceMillis = 60_000L)
    private val homeGridPage =
        PageContainer(
            ContainerId("home"),
            PageContent.Bound(
                LensBinding(Lens(sources = listOf(WorkspaceSourceIds.HOME_GRID)), ExpressionKind.ICON_GRID),
            ),
        )
    private val workspace = Workspace(WorkspaceId("home"), "Nova", listOf(homeGridPage))

    @After
    fun tearDown() = scope.cancel()

    private fun identity(name: String) = AppIdentity(AppPackageName("com.$name"), AppActivityName("com.$name.Main"))

    private fun layoutSet(): HomeLayoutSet {
        fun app(
            label: String,
            column: Int,
        ) = AppShortcutItem(
            LauncherItemId("app:$label"),
            identity(label.lowercase()),
            label,
            placement = GridPlacement(GridCell(column, 0), GridSpan(1, 1)),
        )
        val page =
            LauncherPage(
                LauncherPageId("home"),
                LauncherPageType.Home,
                GridDimensions(4, 6),
                listOf(app("Phone", 0), app("Camera", 1)),
            )
        val layout = HomeLayout(mode, listOf(page), page.id, DockModel(capacity = 5))
        val key = HomeLayoutKey(mode, phone)
        return HomeLayoutSet(activeKey = key, layouts = mapOf(key to layout))
    }

    private fun itemTag(label: String): String {
        val id = repository.pool(phone)!!.items.values.first { it.label == label }.id.value
        return POOL_EDIT_ITEM_TEST_TAG_PREFIX + id
    }

    @Composable
    private fun Surface() {
        val version by repository.version.collectAsState()
        val pool = if (version >= 0) repository.pool(phone) else null
        val candidates = PoolHomeView.candidates(workspace.id, phone, mode)
        val home =
            PlacedHomeContent(
                resolve = { page -> pool?.let { PoolHomeView.resolve(it, page, candidates) } },
                iconLoader = EmptyAppIconLoader,
                widgetViews = EmptyHomeWidgetViewFactory,
                labelSettings = HomeLabelSettings.standard(),
                onOpen = {},
                onReimport = controller::requestReimport,
                edit =
                    PoolEditUi(
                        controller = controller,
                        deviceClass = phone,
                        pool = pool,
                        workspaceId = pool?.let { PoolHomeView.arrangementOf(it, candidates) },
                        installedApps = emptyList(),
                        standardHostIds = { emptySet() },
                        onReimportConfirmed = {},
                    ),
            )
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
            placedHome = home,
        )
    }

    private fun render() {
        runBlocking { repository.initialize(layoutSet()) }
        composeRule.setContent { ScreenshotBackdrop { Surface() } }
    }

    private fun enter() = composeRule.onNodeWithTag(POOL_EDIT_ENTER_TEST_TAG).performClick()

    @Test
    fun enteringEditModeShowsTheBarAndTheNotice() {
        render()
        enter()
        composeRule.onNodeWithTag(POOL_EDIT_BAR_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(POOL_EDIT_NOTICE_TEST_TAG).assertIsDisplayed()
        composeRule.captureScreen()
    }

    @Test
    fun selectingAnItemShowsItsActions() {
        render()
        enter()
        composeRule.onNodeWithTag(itemTag("Phone")).performClick()
        composeRule.onNodeWithTag(POOL_EDIT_PANEL_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(PoolEditText.editingItem("Phone")).assertIsDisplayed()
        composeRule.captureScreen()
    }

    @Test
    fun removingAnItemThenUndoBringsItBack() {
        render()
        enter()
        composeRule.onNodeWithTag(itemTag("Phone")).performClick()
        composeRule.onNodeWithText(PoolEditText.REMOVE).performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Phone").assertDoesNotExist()
        composeRule.onNodeWithTag(POOL_EDIT_UNDO_TEST_TAG).performClick()
        composeRule.onNodeWithContentDescription("Phone").assertExists()
    }

    @Test
    fun deleteEverywhereAsksFirst() {
        render()
        enter()
        composeRule.onNodeWithTag(itemTag("Camera")).performClick()
        composeRule.onNodeWithText(PoolEditText.DELETE_EVERYWHERE).performScrollTo().performClick()
        composeRule.onNode(
            hasText(PoolEditText.DELETE_CONFIRM_TITLE) and hasAnyAncestor(isDialog()),
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Camera").assertExists()
    }

    @Test
    fun refreshWarnsThatItReplacesPreviewEdits() {
        render()
        composeRule.onNodeWithTag(WORKSPACE_PREVIEW_REIMPORT_TEST_TAG).performClick()
        composeRule.onNode(
            hasText(PoolEditText.REIMPORT_CONFIRM_TITLE) and hasAnyAncestor(isDialog()),
        ).assertIsDisplayed()
        composeRule.onNode(
            hasText(PoolEditText.REIMPORT_CONFIRM_BODY) and hasAnyAncestor(isDialog()),
        ).assertIsDisplayed()
    }

    @Test
    fun doneLeavesEditMode() {
        render()
        enter()
        composeRule.onNodeWithTag(POOL_EDIT_DONE_TEST_TAG).performClick()
        composeRule.onNodeWithTag(POOL_EDIT_BAR_TEST_TAG).assertDoesNotExist()
    }
}
