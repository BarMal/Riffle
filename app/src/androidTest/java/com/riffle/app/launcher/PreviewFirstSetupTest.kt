package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviewFirstSetupTest {
    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun clearFirstRunPreferences() {
        firstRunPreferences().edit().clear().commit()
    }

    @Test
    fun setupCardTitleIsAnAccessibleHeading() {
        composeRule.setContent {
            LauncherShellContent(state = previewState(), onAction = {})
        }

        composeRule
            .onNodeWithText("Set up Riffle")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
    }

    @Test
    fun setupCardIsVisibleOnlyOnHome() {
        var state by mutableStateOf(previewState())

        composeRule.setContent {
            LauncherShellContent(state = state, onAction = {})
        }

        composeRule.onNodeWithText("Set as Home app").assertExists()
        composeRule.onNodeWithText("Not now").assertExists()

        composeRule.runOnIdle {
            state = state.copy(destination = ShellDestination.APP_DRAWER)
        }

        composeRule.onNodeWithText("Set as Home app").assertDoesNotExist()
        composeRule.onNodeWithText("Not now").assertDoesNotExist()
    }

    @Test
    fun setupCardDispatchesHomeRequestOnlyFromNamedHomeAction() {
        val actions = mutableListOf<LauncherShellAction>()

        composeRule.setContent {
            LauncherShellContent(
                state = previewState(),
                onAction = { action -> actions.add(action) },
            )
        }

        composeRule.onNodeWithText("Set as Home app").assertHasClickAction()
        composeRule.onNodeWithText("Not now").assertHasClickAction()
        assertTrue(actions.isEmpty())

        composeRule.onNodeWithText("Set as Home app").performClick()

        assertEquals(listOf(LauncherShellAction.RequestDefaultHome), actions)
    }

    @Test
    fun setupCardExplainsTheHomeRoleBenefitBeforeItsPrimaryAction() {
        composeRule.setContent {
            LauncherShellContent(
                state = previewState(),
                onAction = {},
            )
        }

        composeRule
            .onNodeWithText(
                "Set Riffle as your Home app to open it when you press the device Home button.",
            ).assertExists()
        composeRule.onNodeWithText("Set as Home app").assertHasClickAction()
    }

    @Test
    fun setupCardKeepsOptionalCapabilityAccessInFeatureContexts() {
        val actions = mutableListOf<LauncherShellAction>()

        composeRule.setContent {
            LauncherShellContent(
                state = previewState(),
                onAction = { action -> actions.add(action) },
            )
        }

        composeRule.onNodeWithText("Cards (optional)").assertDoesNotExist()
        composeRule.onNodeWithText("You'll be asked when you turn on Cards.").assertDoesNotExist()
        composeRule.onNodeWithText("Floating dock (optional)").assertDoesNotExist()
        composeRule.onNodeWithText("You'll be asked when you turn on Floating dock.").assertDoesNotExist()
        composeRule
            .onNodeWithText("Optional features ask for access only when you turn them on.")
            .assertExists()
        assertTrue(actions.isEmpty())
    }

    @Test
    fun setupCardExposesHomeStatusDescriptionForAssistiveTechnology() {
        composeRule.setContent {
            LauncherShellContent(
                state = previewState(),
                onAction = {},
            )
        }

        composeRule
            .onNodeWithContentDescription(
                "Home app: Riffle is not your Home app yet.",
            ).assertExists()
    }

    @Test
    fun setupCardKeepsActionsReachableInCompactLargeFontLayout() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.8f)) {
                Box(modifier = Modifier.size(width = 320.dp, height = 280.dp)) {
                    LauncherShellContent(
                        state = previewState(),
                        onAction = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Set as Home app").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Not now").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun unavailableHomeStatusKeepsARecoverableSettingsAction() {
        val actions = mutableListOf<LauncherShellAction>()

        composeRule.setContent {
            LauncherShellContent(
                state = LauncherShellState(homeRoleStatus = HomeRoleStatus.UNKNOWN),
                onAction = { action -> actions.add(action) },
            )
        }

        composeRule.onNodeWithText("Home app status is unavailable right now.").assertExists()
        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(listOf(LauncherShellAction.RequestDefaultHome), actions)
    }

    @Test
    fun pendingHomeRoleRequestShowsCheckingStateWithoutAnotherRoleRequestAction() {
        val actions = mutableListOf<LauncherShellAction>()

        composeRule.setContent {
            LauncherShellContent(
                state =
                    LauncherShellState(
                        firstRunStatus = FirstRunStatus.REQUESTING_HOME_ROLE,
                        homeRoleStatus = HomeRoleStatus.UNKNOWN,
                    ),
                onAction = { action -> actions.add(action) },
            )
        }

        composeRule.onNodeWithText("Checking whether Riffle is your Home app.").assertExists()
        composeRule
            .onNodeWithText(
                "Riffle is checking the result of your Home app request. " +
                    "You can keep exploring while this updates.",
            ).assertExists()
        composeRule.onNodeWithText("Try again").assertDoesNotExist()
        composeRule.onNodeWithText("Set as Home app").assertDoesNotExist()
        assertTrue(actions.isEmpty())
    }

    @Test
    fun dismissedSetupCardKeepsHomeSetupDiscoverableFromSettings() {
        val actions = mutableListOf<LauncherShellAction>()

        composeRule.setContent {
            LauncherShellContent(
                state =
                    previewState().copy(
                        destination = ShellDestination.SETTINGS,
                        setupCardDismissed = true,
                    ),
                onAction = { action -> actions.add(action) },
            )
        }

        composeRule.onNodeWithText("Default home app").assertHasClickAction().performClick()

        assertEquals(listOf(LauncherShellAction.RequestDefaultHome), actions)
    }

    @Test
    fun dismissingSetupCardPersistsForTheNextShellState() {
        val repository = FakeFirstRunRepository()
        val viewModel = LauncherShellViewModel(firstRunRepository = repository)

        composeRule.setContent {
            LauncherShell(
                viewModel = viewModel,
                appVersionLabel = "",
                appBuildIdentityLabel = "",
                onAction = {},
            )
        }

        composeRule.onNodeWithText("Not now").performClick()

        composeRule.onNodeWithText("Set as Home app").assertDoesNotExist()
        composeRule.runOnIdle {
            assertTrue(repository.isSetupCardDismissed())
        }
        assertFalse(
            LauncherShellViewModel(firstRunRepository = repository)
                .state.value
                .shouldShowSetupCard,
        )
    }

    @Test
    fun legacyCompletedFirstRunMigratesToTheSetupCardDismissalMarker() {
        val preferences = firstRunPreferences()
        preferences
            .edit()
            .clear()
            .putBoolean("first_run_complete", true)
            .commit()

        val repository =
            SharedPreferencesFirstRunRepository(
                InstrumentationRegistry.getInstrumentation().targetContext,
            )

        assertTrue(repository.isSetupCardDismissed())
        assertTrue(preferences.getBoolean("setup_card_dismissed", false))
        assertTrue(
            SharedPreferencesFirstRunRepository(
                InstrumentationRegistry.getInstrumentation().targetContext,
            ).isSetupCardDismissed(),
        )
    }

    @Test
    fun pendingHomeRoleRequestDestinationSurvivesRepositoryRecreation() {
        val repository =
            SharedPreferencesFirstRunRepository(
                InstrumentationRegistry.getInstrumentation().targetContext,
            )

        repository.setHomeRoleRequestPending(pending = true)
        repository.setHomeRoleRequestDestination(ShellDestination.SETTINGS)

        val recreatedRepository =
            SharedPreferencesFirstRunRepository(
                InstrumentationRegistry.getInstrumentation().targetContext,
            )

        assertTrue(recreatedRepository.isHomeRoleRequestPending())
        assertEquals(ShellDestination.SETTINGS, recreatedRepository.homeRoleRequestDestination())

        recreatedRepository.setHomeRoleRequestDestination(destination = null)

        assertEquals(null, recreatedRepository.homeRoleRequestDestination())
    }

    private fun previewState(): LauncherShellState {
        return LauncherShellState(homeRoleStatus = HomeRoleStatus.NOT_DEFAULT_HOME)
    }

    private fun firstRunPreferences() =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .getSharedPreferences("riffle_first_run", android.content.Context.MODE_PRIVATE)

    private class FakeFirstRunRepository : FirstRunRepository {
        private var setupCardDismissed = false

        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit

        override fun isSetupCardDismissed(): Boolean = setupCardDismissed

        override fun setSetupCardDismissed() {
            setupCardDismissed = true
        }
    }
}
