package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.riffle.app.launcher.LauncherSettingsStateReducer
import com.riffle.app.launcher.SEARCH_RESULTS_TEST_TAG_PREFIX
import com.riffle.app.launcher.SettingsPage
import com.riffle.app.launcher.SettingsPageContent
import com.riffle.app.launcher.settingsSurfaceState
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import com.riffle.core.domain.launcher.settings.SearchResultPresentation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The "Search results" setting (Settings > Library, its one place) offers the card stack beside icons and list, a new
 * install starts on the stack, and a choice made through the real reducer is read back off the same row.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class SearchResultsSettingTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val reducer =
        LauncherSettingsStateReducer(
            homeLayoutRepository =
                object : HomeLayoutRepository {
                    override fun loadHomeLayout(): HomeLayout? = null

                    override fun saveHomeLayout(layout: HomeLayout) = Unit
                },
            launcherSettingsRepository =
                object : LauncherSettingsRepository {
                    override fun loadLauncherSettings(): LauncherSettings? = null

                    override fun saveLauncherSettings(settings: LauncherSettings) = Unit
                },
            appVisibilityRepository =
                object : AppVisibilityRepository {
                    override fun hiddenAppIdentities(): Set<AppIdentity> = emptySet()

                    override fun hideApp(identity: AppIdentity) = Unit

                    override fun showApp(identity: AppIdentity) = Unit
                },
        )
    private var state: LauncherShellState by mutableStateOf(
        ScreenshotFixtures.cardsState().copy(destination = ShellDestination.SETTINGS),
    )

    private fun show(initial: SearchResultPresentation? = null) {
        initial?.let { presentation ->
            val settings = state.launcherSettings
            state = state.copy(launcherSettings = settings.copy(search = settings.search.copy(presentation)))
        }
        composeRule.setContent {
            ScreenshotBackdrop {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SettingsPageContent(
                        modifier = Modifier,
                        state = state.settingsSurfaceState(),
                        page = SettingsPage.LIBRARY,
                        onPageSelected = {},
                        onAction = { action -> state = reducer.reduce(state, action) },
                    )
                }
            }
        }
    }

    private fun option(presentation: SearchResultPresentation) =
        composeRule.onNodeWithTag("$SEARCH_RESULTS_TEST_TAG_PREFIX${presentation.name}").performScrollTo()

    @Test
    fun aNewInstallStartsOnTheCardStackAndAllThreeOptionsAreOffered() {
        show()

        option(SearchResultPresentation.STACK).assertIsSelected()
        composeRule.onNodeWithTag("$SEARCH_RESULTS_TEST_TAG_PREFIX${SearchResultPresentation.STACK.name}")
            .assertExists()
        composeRule.onNodeWithTag(
            "$SEARCH_RESULTS_TEST_TAG_PREFIX${SearchResultPresentation.ICONS.name}",
        ).assertExists()
        composeRule.onNodeWithTag("$SEARCH_RESULTS_TEST_TAG_PREFIX${SearchResultPresentation.LIST.name}").assertExists()
        composeRule.onNodeWithText("Card stack").assertExists()
    }

    @Test
    fun aStoredChoiceKeepsItsValueAndIsTheOneSelected() {
        show(initial = SearchResultPresentation.LIST)

        option(SearchResultPresentation.LIST).assertIsSelected()
    }

    @Test
    fun choosingAnOptionIsReadBackOffTheRow() {
        show()

        option(SearchResultPresentation.ICONS).performClick()
        assertEquals(SearchResultPresentation.ICONS, state.launcherSettings.search.resultPresentation)
        option(SearchResultPresentation.ICONS).assertIsSelected()

        option(SearchResultPresentation.STACK).performClick()
        assertEquals(SearchResultPresentation.STACK, state.launcherSettings.search.resultPresentation)
        option(SearchResultPresentation.STACK).assertIsSelected()
    }
}
