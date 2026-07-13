package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardModeGuardedSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun guardedCardsSurfaceExplainsEmptyGrantedAndRevokedStatesThenRecoversToStandard() {
        var state by mutableStateOf(cardsState(NotificationAccessStatus.GRANTED))

        composeRule.setContent {
            MaterialTheme {
                HomeDestination(
                    state = state,
                    appIconLoader = EmptyAppIconLoader,
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("No active notifications").assertExists()

        composeRule.runOnIdle {
            state = cardsState(NotificationAccessStatus.REVOKED)
        }

        composeRule.onNodeWithText("Notification access was revoked").assertExists()
        composeRule.onNodeWithText("Open notification access").assertExists()

        composeRule.runOnIdle {
            state = standardState()
        }

        composeRule.onNodeWithText("Notification access was revoked").assertDoesNotExist()
        composeRule.onNodeWithText("Open notification access").assertDoesNotExist()
    }

    private fun cardsState(notificationAccessStatus: NotificationAccessStatus): LauncherShellState {
        val layout = HomeLayoutDefaults.standard().copy(viewMode = LauncherViewMode.CARD_INTERFACE)
        return LauncherShellState(
            homeLayout = layout,
            homeLayoutSet = HomeLayoutSet.fromLayout(layout),
            notificationAccessStatus = notificationAccessStatus,
        )
    }

    private fun standardState(): LauncherShellState {
        val layout = HomeLayoutDefaults.standard()
        return LauncherShellState(
            homeLayout = layout,
            homeLayoutSet = HomeLayoutSet.fromLayout(layout),
        )
    }
}
