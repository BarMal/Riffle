package com.riffle.app.screenshots.containers

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.EmptyAppIconLoader
import com.riffle.app.launcher.WORKSPACE_MENU_HANDLE_TEST_TAG
import com.riffle.app.launcher.WorkspaceMenuHost
import com.riffle.app.launcher.WorkspaceMenuUiState
import com.riffle.app.launcher.pool.PlacedHomeContent
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.app.launcher.workspace.PREVIEW_DOCK_DYNAMIC_TEST_TAG
import com.riffle.app.launcher.workspace.PREVIEW_DOCK_EMPTY_TEST_TAG
import com.riffle.app.launcher.workspace.PREVIEW_DOCK_FOLDER_TEST_TAG
import com.riffle.app.launcher.workspace.WORKSPACE_PREVIEW_DOCK_TEST_TAG
import com.riffle.app.launcher.workspace.WorkspacePreviewSurface
import com.riffle.app.launcher.workspace.WorkspacePreviewText
import com.riffle.app.launcher.workspace.previewDockPinTestTag
import com.riffle.app.screenshots.ScreenshotBackdrop
import com.riffle.app.screenshots.ScreenshotDevices
import com.riffle.app.screenshots.captureScreen
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLabelSettings
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceDock
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.dock.PreviewDock
import com.riffle.core.domain.launcher.workspace.dock.PreviewDockModel
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
 * The read-only dock of the Workspaces (preview): pinned apps and a folder, the workspace's dynamic section
 * beside them, the empty and hidden notes, the placeholder while there is no pool, tapping, the 48dp targets,
 * and the dock's "Workspace menu" accessibility action next to the existing handle. Nothing subscribes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class PreviewDockScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val opened = mutableListOf<String>()
    private val menuActions = mutableListOf<WorkspaceMenuAction>()

    private fun identity(name: String) = AppIdentity(AppPackageName("com.$name"), AppActivityName("com.$name.Main"))

    private fun app(label: String) = AppShortcutItem(LauncherItemId("dock:$label"), identity(label.lowercase()), label)

    private val social =
        FolderItem(LauncherItemId("dock:social"), "Social", listOf(app("Mail"), app("Chat")))

    private val dockModel =
        PreviewDock.from(DockModel(capacity = 5, items = listOf(app("Phone"), app("Camera"), app("Maps"), social)))

    private val plainWorkspace = Workspace(WorkspaceId("plain"), "Nova", listOf(ContainerFixtures.boundPage))
    private val dynamicWorkspace =
        plainWorkspace.copy(
            dock =
                WorkspaceDock(
                    dynamicSection = LensBinding(Lens(sources = listOf(SourceId("apps"))), ExpressionKind.ICON_ROW),
                ),
        )

    private val home =
        PlacedHomeContent(
            resolve = { null },
            iconLoader = EmptyAppIconLoader,
            widgetViews = EmptyHomeWidgetViewFactory,
            labelSettings = HomeLabelSettings.standard(),
            onOpen = { opened += it.label },
        )

    private fun render(
        workspace: Workspace = plainWorkspace,
        dockPins: PreviewDockModel? = dockModel,
        placedHome: PlacedHomeContent? = home,
        menu: WorkspaceMenuHost? = null,
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
                    menu = menu,
                    reducedMotion = true,
                    navigation = null,
                    onNavigationConsumed = {},
                    onExit = {},
                    placedHome = placedHome,
                    dockPins = dockPins,
                )
            }
        }
        if (capture) composeRule.captureScreen()
    }

    @Test
    fun pinnedItemsCompact() {
        render()
        composeRule.onNodeWithTag(WORKSPACE_PREVIEW_DOCK_TEST_TAG).assertIsDisplayed()
        dockModel.pins.forEach { composeRule.onNodeWithTag(previewDockPinTestTag(it)).assertExists() }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun pinnedItemsUnfolded() {
        render()
        composeRule.onNodeWithTag(WORKSPACE_PREVIEW_DOCK_TEST_TAG).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun pinnedItemsCompactDark() {
        render()
    }

    @Test
    fun pinnedItemsWithTheDynamicSectionCompact() {
        render(workspace = dynamicWorkspace)
        composeRule.onNodeWithTag(PREVIEW_DOCK_DYNAMIC_TEST_TAG).assertExists()
        dockModel.pins.forEach { composeRule.onNodeWithTag(previewDockPinTestTag(it)).assertExists() }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun pinnedItemsWithTheDynamicSectionUnfolded() {
        render(workspace = dynamicWorkspace)
        composeRule.onNodeWithTag(PREVIEW_DOCK_DYNAMIC_TEST_TAG).assertExists()
    }

    @Test
    fun onlyTheDynamicSectionWhenNothingIsPinned() {
        render(workspace = dynamicWorkspace, dockPins = PreviewDock.from(null))
        composeRule.onNodeWithTag(PREVIEW_DOCK_DYNAMIC_TEST_TAG).assertExists()
        composeRule.onNodeWithTag(PREVIEW_DOCK_EMPTY_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun nothingPinnedShowsAnHonestNote() {
        render(dockPins = PreviewDock.from(null))
        composeRule.onNodeWithTag(PREVIEW_DOCK_EMPTY_TEST_TAG).assertExists()
        composeRule.onNodeWithText(WorkspacePreviewText.DOCK_EMPTY).assertExists()
    }

    @Test
    fun aHiddenDockSaysSoAndDrawsNoPins() {
        render(dockPins = PreviewDock.from(DockModel(capacity = 4, items = listOf(app("Phone")), isEnabled = false)))
        composeRule.onNodeWithText(WorkspacePreviewText.DOCK_HIDDEN).assertExists()
        composeRule.onNodeWithTag(previewDockPinTestTag(dockModel.pins.first())).assertDoesNotExist()
    }

    @Test
    fun withoutAPoolThePlaceholderStays() {
        render(placedHome = null)
        composeRule.onNodeWithText(WorkspacePreviewText.DOCK_PLACEHOLDER).assertExists()
    }

    @Test
    fun tappingAPinnedAppOpensIt() {
        render(capture = false)
        composeRule.onNodeWithTag(previewDockPinTestTag(dockModel.pins[1])).performClick()
        assertEquals(listOf("Camera"), opened)
    }

    @Test
    fun tappingAPinnedFolderListsItsAppsAndTappingOneOpensIt() {
        render(capture = false)
        composeRule.onNodeWithTag(previewDockPinTestTag(dockModel.pins[3])).performClick()
        composeRule.onNodeWithTag(PREVIEW_DOCK_FOLDER_TEST_TAG).assertExists()
        composeRule.onNode(hasText("Chat") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("Chat"), opened)
    }

    @Test
    fun pinsAreSpokenAndMeetThe48dpTarget() {
        render(capture = false)
        val phone = composeRule.onNodeWithTag(previewDockPinTestTag(dockModel.pins[0]))
        phone.assertHasClickAction()
        phone.assertWidthIsAtLeast(48.dp)
        phone.assertHeightIsAtLeast(48.dp)
        phone.assert(hasContentDescription("Phone"))
        composeRule.onNodeWithTag(previewDockPinTestTag(dockModel.pins[3])).assert(
            hasContentDescription("Social folder, 2 apps"),
        )
    }

    @Test
    fun theDockOffersTheWorkspaceMenuActionNextToTheHandle() {
        val menu = WorkspaceMenuHost(WorkspaceMenuUiState()) { menuActions += it }
        render(menu = menu, capture = false)
        composeRule.onNodeWithTag(WORKSPACE_MENU_HANDLE_TEST_TAG).assertExists()
        val actions =
            composeRule.onNodeWithTag(WORKSPACE_PREVIEW_DOCK_TEST_TAG).fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
        val open = actions.first { it.label == "Workspace menu" }
        assertTrue(open.action())
        assertEquals(listOf<WorkspaceMenuAction>(WorkspaceMenuAction.Open), menuActions)
    }
}
