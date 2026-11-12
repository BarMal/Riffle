package com.riffle.app.screenshots

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.HOME_DOCK_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.library.LIBRARY_ALPHA_LIST_TEST_TAG
import com.riffle.app.launcher.library.LIBRARY_CATEGORIES_TEST_TAG
import com.riffle.app.launcher.library.LIBRARY_SEARCH_FIELD_TEST_TAG
import com.riffle.app.launcher.library.LIBRARY_SHEET_TEST_TAG
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.library.LibraryView
import com.riffle.core.domain.launcher.settings.AppDrawerSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Library (ADR 0003) through the real [HomeDestination]: a Thick glass sheet over Home with the Dock still drawn
 * under it, in its three views, with search results, and an opened category; compact and expanded. Plus behaviour
 * checks of "Show as" with Undo, launching, Enter on the top hit, focus from swipe down, and the ways out.
 *
 * The shell is a recording lambda, so a choice shows up as the action it asked for; the state is fixed, so a view
 * change is checked by the action rather than by a redraw.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class LibraryScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<LauncherShellAction>()

    @Test
    fun categoriesCompact() {
        render(libraryState(LibraryView.Categories))
        composeRule.onNodeWithTag(LIBRARY_CATEGORIES_TEST_TAG).assertIsDisplayed()
        composeRule.captureScreen()
    }

    @Test
    fun alphaListCompact() {
        render(libraryState(LibraryView.AlphaList))
        composeRule.onNodeWithTag(LIBRARY_ALPHA_LIST_TEST_TAG).assertIsDisplayed()
        composeRule.captureScreen()
    }

    @Test
    fun gridCompact() {
        render(libraryState(LibraryView.Grid))
        composeRule.captureScreen()
    }

    @Test
    fun searchResultsCompact() {
        render(searching(libraryState(LibraryView.Categories), "ca"))
        composeRule.captureScreen()
    }

    @Test
    fun openCategoryCompact() {
        render(libraryState(LibraryView.Categories))
        composeRule.onNode(hasText("Social") and inSheet()).performClick()
        composeRule.onNode(hasText("Social") and inSheet()).assertIsDisplayed()
        composeRule.captureScreen()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun categoriesCompactDark() {
        render(libraryState(LibraryView.Categories))
        composeRule.captureScreen()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun categoriesUnfolded() {
        render(libraryState(LibraryView.Categories, HomeLayoutDeviceClass.FOLDABLE))
        composeRule.captureScreen()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun alphaListUnfolded() {
        render(libraryState(LibraryView.AlphaList, HomeLayoutDeviceClass.FOLDABLE))
        composeRule.captureScreen()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.TABLET_LANDSCAPE)
    fun gridTabletLandscape() {
        render(libraryState(LibraryView.Grid, HomeLayoutDeviceClass.TABLET))
        composeRule.captureScreen()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.TABLET_LANDSCAPE)
    fun searchResultsTabletLandscape() {
        render(searching(libraryState(LibraryView.Categories, HomeLayoutDeviceClass.TABLET), "ca"))
        composeRule.captureScreen()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun openCategoryUnfolded() {
        render(libraryState(LibraryView.Categories, HomeLayoutDeviceClass.FOLDABLE))
        composeRule.onNode(hasText("Social") and inSheet()).performClick()
        composeRule.captureScreen()
    }

    @Test
    fun theLibraryIsASheetOverHomeAndTheDockStaysDrawn() {
        render(libraryState(LibraryView.Categories))
        composeRule.onNodeWithTag(LIBRARY_SHEET_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(HOME_DOCK_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun categoryTilesShowTheirFirstIconsAndTheNameOpensTheFullCategory() {
        render(libraryState(LibraryView.Categories))

        // Image holds Camera and Photos: both icons are on the tile, the name below opens the category.
        composeRule.onNode(hasContentDescription("Camera icon") and inSheet()).assertIsDisplayed()
        composeRule.onNode(hasContentDescription("Photos icon") and inSheet()).assertIsDisplayed()
        composeRule.onNode(hasText("Image") and inSheet()).assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithContentDescription("Back to all categories").assertIsDisplayed()
        // The other categories are gone while one is open.
        composeRule.onNode(hasText("Social") and inSheet()).assertDoesNotExistInSheet()
    }

    @Test
    fun tappingATileIconLaunchesTheAppAndClosesTheLibrary() {
        render(libraryState(LibraryView.Categories))

        composeRule.onNode(hasContentDescription("Camera icon") and inSheet()).performClick()

        assertEquals(
            listOf<LauncherShellAction>(
                LauncherShellAction.LaunchApp(ScreenshotFixtures.camera.identity),
                LauncherShellAction.OpenHome,
            ),
            actions,
        )
    }

    @Test
    fun launchingFromTheLibraryOverTheStreamClosesBackToTheStream() {
        render(libraryState(LibraryView.AlphaList).copy(libraryOverStream = true))

        composeRule.onNode(hasText("Camera") and inSheet()).performClick()

        assertEquals(LauncherShellAction.OpenStream, actions.last())
    }

    @Test
    fun longPressingTheHeaderOffersShowAsAndChoosingARedrawsAtOnceWithUndo() {
        render(libraryState(LibraryView.Categories))

        composeRule.onNode(hasText("Library") and inSheet()).performTouchInput { longClick() }
        composeRule.onNodeWithText("Show as").assertIsDisplayed()
        composeRule.onNodeWithText("Grid").performClick()

        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.SelectLibraryView(LibraryView.Grid)), actions)
        composeRule.onNodeWithText("Library shown as Grid").assertIsDisplayed()

        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf<LauncherShellAction>(
                LauncherShellAction.SelectLibraryView(LibraryView.Grid),
                LauncherShellAction.SelectLibraryView(LibraryView.Categories),
            ),
            actions,
        )
    }

    @Test
    fun theOptionsButtonOffersTheSameShowAsMenuWithA48DpTarget() {
        render(libraryState(LibraryView.AlphaList))

        composeRule.onNodeWithContentDescription("Library options")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .performClick()

        composeRule.onNodeWithText("Show as").assertIsDisplayed()
        composeRule.onNodeWithText("Categories").performClick()
        assertEquals(
            listOf<LauncherShellAction>(LauncherShellAction.SelectLibraryView(LibraryView.Categories)),
            actions,
        )
    }

    @Test
    fun enterInTheSearchFieldLaunchesTheTopHitAndAppsComeFirst() {
        render(searching(libraryState(LibraryView.Categories), "ca"))

        composeRule.onNodeWithTag(LIBRARY_SEARCH_FIELD_TEST_TAG).performImeAction()

        // Calendar was ranked before Camera by the search source and both are apps: the first one launches.
        assertEquals(
            LauncherShellAction.LaunchApp(ScreenshotFixtures.calendar.identity),
            actions.first(),
        )
        assertEquals(LauncherShellAction.OpenHome, actions.last())
    }

    @Test
    fun swipeDownOnHomeOpensTheLibraryWithTheSearchFieldFocused() {
        render(libraryState(LibraryView.Categories).copy(destination = ShellDestination.SEARCH))

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LIBRARY_SEARCH_FIELD_TEST_TAG).assertIsFocused()
    }

    @Test
    fun theLibraryOffersACloseActionAndGoesHome() {
        render(libraryState(LibraryView.Categories))

        val close =
            composeRule
                .onNode(hasText("Library") and inSheet())
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .single { action -> action.label == "Close Library" }
        composeRule.runOnUiThread { close.action() }

        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), actions)
    }

    @Test
    fun theCloseActionReturnsToTheStreamWhenTheLibraryOpenedOverIt() {
        render(libraryState(LibraryView.Categories).copy(libraryOverStream = true))

        val close =
            composeRule
                .onNode(hasText("Library") and inSheet())
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .single { action -> action.label == "Close Library" }
        composeRule.runOnUiThread { close.action() }

        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), actions)
    }

    @Test
    fun theHeaderOffersEachViewAsACustomActionToo() {
        render(libraryState(LibraryView.Categories))

        val labels =
            composeRule
                .onNode(hasText("Library") and inSheet())
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .map { action -> action.label }

        assertTrue("Show as Grid" in labels && "Show as A–Z list" in labels && "Show as Categories" in labels)
    }

    private fun inSheet() = hasAnyAncestor(hasTestTag(LIBRARY_SHEET_TEST_TAG))

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistInSheet() {
        // The interaction was built from a matcher that no longer matches: it must not resolve to a node.
        val resolved = runCatching { fetchSemanticsNode() }.isSuccess
        assertTrue("expected the node to be gone while a category is open", !resolved)
    }

    private fun render(state: LauncherShellState) {
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = state,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = emptyStreamHost(),
                    streamShown = state.libraryOverStream,
                    onAction = { action -> actions += action },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun searching(
        state: LauncherShellState,
        query: String,
    ): LauncherShellState =
        state.copy(
            searchQuery = query,
            searchResults = listOf(ScreenshotFixtures.calendar, ScreenshotFixtures.camera).map(::categorised),
        )

    private fun libraryState(
        view: LibraryView,
        deviceClass: HomeLayoutDeviceClass = HomeLayoutDeviceClass.PHONE,
    ): LauncherShellState =
        ScreenshotFixtures.homeState(deviceClass).let { base ->
            base.copy(
                destination = ShellDestination.APP_DRAWER,
                installedApps = base.installedApps.map(::categorised),
                launcherSettings =
                    LauncherSettings(
                        appDrawer = AppDrawerSettings(libraryViews = mapOf(deviceClass to view)),
                    ),
            )
        }

    /** The platform category each fixture app would carry, so the tiles have something to group by. */
    private fun categorised(app: InstalledApp): InstalledApp = app.copy(category = categories[app.label])

    private val categories =
        mapOf(
            "Camera" to "Image",
            "Photos" to "Image",
            "Mail" to "Productivity",
            "Calendar" to "Productivity",
            "Notes" to "Productivity",
            "Chat" to "Social",
            "Browser" to "Social",
            "Maps" to "Maps",
            "Music" to "Audio",
        )
}
