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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.riffle.app.launcher.LauncherDockEditReducer
import com.riffle.app.launcher.LauncherSettingsStateReducer
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.SettingsPage
import com.riffle.app.launcher.SettingsPageContent
import com.riffle.app.launcher.appsBehindOptionTestTag
import com.riffle.app.launcher.layoutOptionTestTag
import com.riffle.app.launcher.settingsSurfaceState
import com.riffle.app.launcher.streamAppsBehindTestTag
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.home.DockEngine
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.settings.AppsBehindCards
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import com.riffle.core.domain.launcher.settings.LayoutPosture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Two pages that reach one stored value read identically and agree live: the Dock position on Settings > Dock and
 * Settings > Layout, and "Show apps behind the cards" on Settings > Layout and Settings > Stream > Cards. Both pages
 * are drawn at once over the real reducer, a change on one is read off the other.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class SettingsSharedRowsTest {
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
    private val dockReducer =
        LauncherDockEditReducer(
            dockEngine = DockEngine(),
            homeLayoutRepository =
                object : HomeLayoutRepository {
                    override fun loadHomeLayout(): HomeLayout? = null

                    override fun saveHomeLayout(layout: HomeLayout) = Unit
                },
        )
    private var state by mutableStateOf(ScreenshotFixtures.cardsState().copy(destination = ShellDestination.SETTINGS))

    private fun show(vararg pages: SettingsPage) {
        composeRule.setContent {
            ScreenshotBackdrop {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    pages.forEach { page ->
                        SettingsPageContent(
                            modifier = Modifier,
                            state = state.settingsSurfaceState(),
                            page = page,
                            onPageSelected = {},
                            onAction = { action ->
                                state =
                                    if (action is LauncherShellAction.SelectPostureDockEdge ||
                                        action is LauncherShellAction.SelectDockPosition
                                    ) {
                                        dockReducer.reduce(state, action)
                                    } else {
                                        reducer.reduce(state, action)
                                    }
                            },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun theDockPositionReadsTheSameOnTheDockAndLayoutPagesAndAgreesLive() {
        show(SettingsPage.DOCK, SettingsPage.POSTURE_LAYOUT)

        // Same title on both (the Layout page shows it once per posture), same options.
        assertTrue(composeRule.onAllNodesWithText("Dock position").fetchSemanticsNodes().size >= 3)
        listOf("Bottom", "Left", "Right").forEach { option ->
            val offered = composeRule.onAllNodesWithText(option).fetchSemanticsNodes().size
            assertTrue("$option is offered by both", offered >= 2)
        }
        assertTrue(composeRule.onAllNodesWithText("Top").fetchSemanticsNodes().isEmpty())

        // Chosen on the Layout page (folded), read off the Dock page: one value.
        composeRule.onNodeWithTag(layoutOptionTestTag(LayoutPosture.FOLDED, "dock", DockPosition.LEFT.name))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("dock-position-${DockPosition.LEFT.name}").performScrollTo().assertIsSelected()
        assertEquals(DockPosition.LEFT, state.homeLayout.dock.position)

        // And the other way round.
        composeRule.onNodeWithTag("dock-position-${DockPosition.RIGHT.name}").performScrollTo().performClick()
        composeRule.onNodeWithTag(layoutOptionTestTag(LayoutPosture.FOLDED, "dock", DockPosition.RIGHT.name))
            .performScrollTo()
            .assertIsSelected()
    }

    @Test
    fun appsBehindTheCardsReadsTheSameOnTheLayoutAndCardsPagesAndAgreesLive() {
        show(SettingsPage.POSTURE_LAYOUT, SettingsPage.STREAM_CARDS)

        // The one title, on both pages for each posture; the subtitle states the current choice.
        assertEquals(4, composeRule.onAllNodesWithText("Apps behind the cards").fetchSemanticsNodes().size)
        val hidden = "Hidden: Home fades out while the Stream shows."
        val around = "Around the cards: Home stays, and an app under a card is hidden."
        val both = "Behind and around: Home stays and fades out under the cards."
        assertEquals(2, composeRule.onAllNodesWithText(hidden).fetchSemanticsNodes().size)
        assertEquals(2, composeRule.onAllNodesWithText(both).fetchSemanticsNodes().size)
        assertTrue(composeRule.onAllNodesWithText(around).fetchSemanticsNodes().isEmpty())

        // Folded, "Around the cards" picked on the Cards page, read off the Layout page.
        composeRule.onNodeWithTag(
            appsBehindOptionTestTag(streamAppsBehindTestTag(LayoutPosture.FOLDED), AppsBehindCards.AROUND),
        )
            .performScrollTo()
            .performClick()
        assertEquals(AppsBehindCards.AROUND, state.launcherSettings.layout.folded.appsBehind)
        composeRule.onNodeWithTag(
            appsBehindOptionTestTag(
                layoutOptionTestTag(LayoutPosture.FOLDED, "peek", "through"),
                AppsBehindCards.AROUND,
            ),
        ).performScrollTo().assertIsSelected()
        assertEquals(2, composeRule.onAllNodesWithText(around).fetchSemanticsNodes().size)
        assertTrue(composeRule.onAllNodesWithText(hidden).fetchSemanticsNodes().isEmpty())

        // And back from the Layout page, read off the Cards page.
        composeRule.onNodeWithTag(
            appsBehindOptionTestTag(
                layoutOptionTestTag(LayoutPosture.FOLDED, "peek", "through"),
                AppsBehindCards.HIDDEN,
            ),
        ).performScrollTo().performClick()
        assertEquals(AppsBehindCards.HIDDEN, state.launcherSettings.layout.folded.appsBehind)
        composeRule.onNodeWithTag(
            appsBehindOptionTestTag(streamAppsBehindTestTag(LayoutPosture.FOLDED), AppsBehindCards.HIDDEN),
        )
            .performScrollTo()
            .assertIsSelected()
    }
}
