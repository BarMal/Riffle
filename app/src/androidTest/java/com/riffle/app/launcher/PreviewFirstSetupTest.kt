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
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
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
    fun setupCardExplainsOptionalCardsAndFloatingDockWithoutRequestingAccess() {
        val actions = mutableListOf<LauncherShellAction>()

        composeRule.setContent {
            LauncherShellContent(
                state =
                    previewState().copy(
                        notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED,
                        overlayDockPermissionStatus = OverlayDockPermissionStatus.NOT_GRANTED,
                    ),
                onAction = { action -> actions.add(action) },
            )
        }

        composeRule.onNodeWithText("Cards (optional)").assertExists()
        composeRule.onNodeWithText("You'll be asked when you turn on Cards.").assertExists()
        composeRule.onNodeWithText("Floating dock (optional)").assertExists()
        composeRule.onNodeWithText("You'll be asked when you turn on Floating dock.").assertExists()
        composeRule
            .onNodeWithText("Cards and Floating dock are optional. Riffle asks only when you choose one.")
            .assertExists()
        assertTrue(actions.isEmpty())
    }

    @Test
    fun setupCardExposesStatusDescriptionsForAssistiveTechnology() {
        composeRule.setContent {
            LauncherShellContent(
                state =
                    previewState().copy(
                        notificationAccessStatus = NotificationAccessStatus.REVOKED,
                        overlayDockPermissionStatus = OverlayDockPermissionStatus.GRANTED,
                    ),
                onAction = {},
            )
        }

        composeRule
            .onNodeWithContentDescription(
                "Cards (optional): Access was removed. Restore it from Cards when needed.",
            ).assertExists()
        composeRule
            .onNodeWithContentDescription(
                "Floating dock (optional): Ready when you turn on Floating dock.",
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

    private fun previewState(): LauncherShellState {
        return LauncherShellState(homeRoleStatus = HomeRoleStatus.NOT_DEFAULT_HOME)
    }

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
