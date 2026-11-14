package com.riffle.app.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.riffle.app.launcher.HomeDestination
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.settings.SearchResultPresentation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Home handle and search from the Dock (ADR 0003, Amendment 1), through the real [HomeDestination]: Home with the
 * handle above the Dock (several pages, one page, a side Dock, beside the first-run hint) and search open from the
 * Dock with the field on its edge: empty, with results as a list and as icons, and with nothing found. Dark and light.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class HomeHandleScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val pages = ScreenshotFixtures.homeState().withHomePages(4)

    @Test
    fun homeWithTheHandleLight() = render(pages)

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun homeWithTheHandleDark() = render(pages)

    @Test
    fun homeWithOnePageTheHandleIsJustTheSearchGlyph() = render(ScreenshotFixtures.homeState())

    @Test
    fun homeWithASideDockTheHandleStaysAboveTheBottomEdge() =
        render(ScreenshotFixtures.homeState(dockPosition = DockPosition.LEFT).withHomePages(3))

    @Test
    fun theFirstRunHintStandsAboveTheHandleNotOnIt() {
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = pages,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = emptyStreamHost(),
                    onDockPressHintSeen = {},
                    onAction = {},
                )
            }
        }
        composeRule.captureScreen()
    }

    @Test
    fun searchOpenFromTheDockEmptyLight() = render(pages.searching(query = ""))

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun searchOpenFromTheDockEmptyDark() = render(pages.searching(query = ""))

    @Test
    fun searchResultsAsAListGrowUpFromTheFieldLight() = render(pages.searching())

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun searchResultsAsAListGrowUpFromTheFieldDark() = render(pages.searching())

    @Test
    fun searchResultsAsIconsLight() = render(pages.searching(presentation = SearchResultPresentation.ICONS))

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun searchResultsAsIconsDark() = render(pages.searching(presentation = SearchResultPresentation.ICONS))

    @Test
    fun searchWithNothingFoundLight() = render(pages.searching(query = "zzz"))

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun searchResultsLargeFont() = render(pages.searching())

    private fun render(state: LauncherShellState) {
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = state,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = emptyStreamHost(),
                    onAction = {},
                )
            }
        }
        composeRule.captureScreen()
    }
}
