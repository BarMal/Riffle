package com.riffle.app.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
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
