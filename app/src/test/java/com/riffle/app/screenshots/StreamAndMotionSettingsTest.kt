package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.LauncherSettingsStateReducer
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.STREAM_CARD_PREVIEW_TEST_TAG
import com.riffle.app.launcher.SettingsDockMotionSection
import com.riffle.app.launcher.SettingsStreamCards
import com.riffle.app.launcher.cardActionsPlacementTestTag
import com.riffle.app.launcher.openCardPlacementTestTag
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.settings.CardActionsPlacement
import com.riffle.core.domain.launcher.settings.DockMotionSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import com.riffle.core.domain.launcher.settings.OpenCardPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings > Stream > Cards and Settings > Motion > Dock and Stream, drawn over a real reducer and a fake store: a
 * change made on the page reaches what is saved, and the page draws what was saved (dark, light and large type are
 * recorded as screenshots).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamAndMotionSettingsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class Store : LauncherSettingsRepository {
        var saved: LauncherSettings? = null

        override fun loadLauncherSettings(): LauncherSettings? = saved

        override fun saveLauncherSettings(settings: LauncherSettings) {
            saved = settings
        }
    }

    private val store = Store()
    private val reducer =
        LauncherSettingsStateReducer(
            homeLayoutRepository =
                object : HomeLayoutRepository {
                    override fun loadHomeLayout(): HomeLayout? = null

                    override fun saveHomeLayout(layout: HomeLayout) = Unit
                },
            launcherSettingsRepository = store,
            appVisibilityRepository =
                object : AppVisibilityRepository {
                    override fun hiddenAppIdentities(): Set<AppIdentity> = emptySet()

                    override fun hideApp(identity: AppIdentity) = Unit

                    override fun showApp(identity: AppIdentity) = Unit
                },
        )
    private var state by mutableStateOf(LauncherShellState())

    private fun onAction(action: LauncherShellAction) {
        state = reducer.reduce(state, action)
    }

    private fun showCards() {
        composeRule.setContent {
            ScreenshotBackdrop {
                Column(
                    modifier = pageModifier(),
                ) {
                    SettingsStreamCards(settings = state.launcherSettings.stream, onAction = ::onAction)
                }
            }
        }
    }

    private fun showMotion() {
        composeRule.setContent {
            ScreenshotBackdrop {
                Column(
                    modifier = pageModifier(),
                ) {
                    SettingsDockMotionSection(settings = state.launcherSettings.motion.dock, onAction = ::onAction)
                }
            }
        }
    }

    @Composable
    private fun pageModifier(): Modifier =
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)

    // Stream > Cards

    @Test
    fun theCardsPageShowsTheDefaultsAndAPreviewCard() {
        showCards()

        composeRule.onNodeWithTag(STREAM_CARD_PREVIEW_TEST_TAG).assertExists()
        composeRule.onNodeWithTag(openCardPlacementTestTag(OpenCardPlacement.CENTRE)).assertIsSelected()
        composeRule.onNodeWithTag(cardActionsPlacementTestTag(CardActionsPlacement.IN_DOCK)).assertIsSelected()
        composeRule.onNodeWithContentDescription("Preview of a card at 22 percent colour").assertExists()
        composeRule.captureScreen()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun theCardsPageInTheDarkTheme() {
        showCards()
        composeRule.captureScreen()
    }

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun theCardsPageWithLargeType() {
        showCards()
        composeRule.captureScreen()
    }

    @Test
    fun choosingWhereAnOpenCardAndItsActionsSitIsSavedAndKeptSelected() {
        showCards()

        composeRule.onNodeWithTag(openCardPlacementTestTag(OpenCardPlacement.ABOVE_DOCK)).performClick()
        composeRule.onNodeWithTag(cardActionsPlacementTestTag(CardActionsPlacement.ON_CARD)).performClick()

        assertEquals(OpenCardPlacement.ABOVE_DOCK, store.saved?.stream?.openCardPlacement)
        assertEquals(CardActionsPlacement.ON_CARD, store.saved?.stream?.cardActionsPlacement)
        composeRule.onNodeWithTag(openCardPlacementTestTag(OpenCardPlacement.ABOVE_DOCK)).assertIsSelected()
        composeRule.onNodeWithTag(cardActionsPlacementTestTag(CardActionsPlacement.ON_CARD)).assertIsSelected()
    }

    @Test
    fun theDeferredGrowsOutOfTheDockChoiceIsOfferedAndSaysItLooksLikeAboveTheDockForNow() {
        showCards()

        composeRule.onNodeWithTag(openCardPlacementTestTag(OpenCardPlacement.GROWS_OUT_OF_DOCK)).performClick()

        assertEquals(OpenCardPlacement.GROWS_OUT_OF_DOCK, store.saved?.stream?.openCardPlacement)
        composeRule.onNodeWithTag(openCardPlacementTestTag(OpenCardPlacement.GROWS_OUT_OF_DOCK)).assertIsSelected()
        composeRule.onNodeWithText("Grows out of the Dock is kept for later", substring = true).assertExists()
    }

    @Test
    fun theTintSliderSavesItsValueAndThePreviewFollowsIt() {
        showCards()
        val slider = composeRule.onNodeWithContentDescription("Card colour strength")
        slider.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "22%"))

        slider.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(35f)) }

        assertEquals(35, store.saved?.stream?.cardTintPercent)
        composeRule.onNodeWithContentDescription("Preview of a card at 35 percent colour").assertExists()
    }

    @Test
    fun theTopGlowSwitchIsSaved() {
        showCards()

        composeRule.onNodeWithText("Glow on the top edge").performScrollTo().performClick()

        assertTrue(store.saved?.stream?.cardTopGlow == true)
        composeRule.onNodeWithText("The card's colour also shows along its top edge").assertExists()
        composeRule.captureScreen()
    }

    // Motion > Dock and Stream

    @Test
    fun theMotionSectionShowsTodaysValuesAsTheDefaults() {
        showMotion()

        composeRule.onNodeWithContentDescription("Opening speed")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "200 ms"))
        composeRule.onNodeWithContentDescription("Dock bounce")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "50%"))
        composeRule.onNodeWithContentDescription("Card stack coasting")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "100%"))
        composeRule.onNodeWithContentDescription("Card snapping")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "40%"))
        composeRule.onNodeWithContentDescription("Hold time to choose")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "220 ms"))
        composeRule.captureScreen()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun theMotionSectionInTheDarkTheme() {
        showMotion()
        composeRule.captureScreen()
    }

    @Test
    fun eachMotionSliderSavesItsValueAndKeepsTheOthers() {
        showMotion()

        // The slider's progress is its stop: from the minimum in whole steps.
        composeRule.onNodeWithContentDescription("Opening speed")
            .performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(20f)) }
        composeRule.onNodeWithContentDescription("Dock bounce")
            .performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(0f)) }
        composeRule.onNodeWithContentDescription("Card stack coasting")
            .performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(15f)) }
        composeRule.onNodeWithContentDescription("Card snapping")
            .performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(10f)) }
        composeRule.onNodeWithContentDescription("Hold time to choose")
            .performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(15f)) }

        assertEquals(
            DockMotionSettings(
                speedMillis = 300,
                trampolinePercent = 0,
                inertiaPercent = 150,
                magnetPercent = 100,
                holdMillis = 400,
            ),
            store.saved?.motion?.dock,
        )
        composeRule.onNodeWithContentDescription("Dock bounce")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Off"))
    }

    @Test
    fun theTicksSwitchIsSavedAndSaysWhatItDoes() {
        showMotion()

        composeRule.onNodeWithText("Light ticks").performScrollTo().performClick()

        assertFalse(store.saved?.motion?.dock?.ticks ?: true)
        composeRule.onNodeWithText("No ticks").assertExists()
    }
}
