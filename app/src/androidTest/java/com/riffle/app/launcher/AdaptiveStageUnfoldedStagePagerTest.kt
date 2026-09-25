package com.riffle.app.launcher

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.AdaptiveStagePosture
import com.riffle.core.domain.launcher.cards.AdaptiveStageWindowLayout
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.cards.AppStagePreferences
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.settings.CardsSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Follow-up to user feedback on alpha 1252: TWO_PANE/THREE_PANE Cards used to render a single
 * static [AdaptiveStagePageBody] for the selected stage with no way to drag between stages --
 * only [AdaptiveStageCompactContent] and [AdaptiveStageSplitContent] used
 * [AdaptiveStageCompactStagePager]. Exercises the pager now wired into that wide-window branch
 * through the real [AdaptiveStageAppStageSurface] tree, the same drag style
 * [AdaptiveStageStagePagerGestureTest] uses for the harness pager, plus the "rail tap" side --
 * the dock itself is out of scope for this change (see #1212), so a rail tap is simulated the
 * same way it really reaches this surface: as an externally driven change to the durable selected
 * stage, mirroring how [AdaptiveStageAllNotificationsSurfaceTest] drives an externally-held
 * selection into this same tree.
 */
class AdaptiveStageUnfoldedStagePagerTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun horizontalDragInTwoPaneModeSettlesToTheNextStage() {
        val first = app("first", "First")
        val second = app("second", "Second")
        var state by mutableStateOf(twoPinnedStageState(first, second, selected = first))
        val dispatched = mutableListOf<LauncherShellAction>()

        setTwoPaneContent(
            state = { state },
            onAction = { action ->
                dispatched.add(action)
                if (action is LauncherShellAction.SelectAppStage) {
                    state = state.withSelectedStage(action.stageId)
                }
            },
        )

        composeRule.onNodeWithContentDescription("Cards stage: First").assertIsDisplayed()

        composeRule.onAllNodesWithTag(ADAPTIVE_STAGE_EMPTY_STAGE_CARD_TEST_TAG)[0].performTouchInput {
            swipe(start = Offset(width - 1f, height / 2f), end = Offset(1f, height / 2f))
        }

        composeRule.runOnIdle {
            assertEquals(
                listOf(LauncherShellAction.SelectAppStage(secondStageId(second))),
                dispatched.filterIsInstance<LauncherShellAction.SelectAppStage>(),
            )
        }
        composeRule.onNodeWithContentDescription("Cards stage: Second").assertIsDisplayed()
    }

    @Test
    fun railTapInThreePaneModeMovesThePagerAndStaysInSyncWithAFollowingDrag() {
        val first = app("first", "First")
        val second = app("second", "Second")
        val third = app("third", "Third")
        var state by mutableStateOf(threePinnedStageState(first, second, third, selected = first))
        val dispatched = mutableListOf<LauncherShellAction>()

        setThreePaneContent(
            state = { state },
            onAction = { action ->
                dispatched.add(action)
                if (action is LauncherShellAction.SelectAppStage) {
                    state = state.withSelectedStage(action.stageId)
                }
            },
        )

        composeRule.onNodeWithContentDescription("Cards stage: First").assertIsDisplayed()

        // Simulate a rail tap: something outside this surface (the dock's dynamic section, #1212)
        // dispatches SelectAppStage directly, without ever touching the pager.
        composeRule.runOnIdle { state = state.withSelectedStage(secondStageId(second)) }

        composeRule.onNodeWithContentDescription("Cards stage: Second").assertIsDisplayed()

        // The pager must have followed the rail-driven selection (not just the header): a drag
        // now moves on from the rail-selected page to its real neighbor, third.
        composeRule.onAllNodesWithTag(ADAPTIVE_STAGE_EMPTY_STAGE_CARD_TEST_TAG)[0].performTouchInput {
            swipe(start = Offset(width - 1f, height / 2f), end = Offset(1f, height / 2f))
        }

        composeRule.runOnIdle {
            assertTrue(
                dispatched.filterIsInstance<LauncherShellAction.SelectAppStage>().last().stageId ==
                    thirdStageId(third),
            )
        }
        composeRule.onNodeWithContentDescription("Cards stage: Third").assertIsDisplayed()
    }

    private fun setTwoPaneContent(
        state: () -> LauncherShellState,
        onAction: (LauncherShellAction) -> Unit,
    ) = setPaneContent(widthDp = TWO_PANE_WIDTH_DP, state = state, onAction = onAction)

    private fun setThreePaneContent(
        state: () -> LauncherShellState,
        onAction: (LauncherShellAction) -> Unit,
    ) = setPaneContent(widthDp = THREE_PANE_WIDTH_DP, state = state, onAction = onAction)

    private fun setPaneContent(
        widthDp: Int,
        state: () -> LauncherShellState,
        onAction: (LauncherShellAction) -> Unit,
    ) {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(widthDp.dp).height(PANE_HEIGHT_DP.dp).clipToBounds()) {
                    AdaptiveStageAppStageSurface(
                        state = state(),
                        windowLayout =
                            AdaptiveStageWindowLayout(
                                widthDp = widthDp,
                                heightDp = PANE_HEIGHT_DP,
                                posture = AdaptiveStagePosture.UNFOLDED,
                            ),
                        onAction = onAction,
                    )
                }
            }
        }
    }

    private fun LauncherShellState.withSelectedStage(stageId: AppStageId): LauncherShellState {
        val preferences =
            launcherSettings.cards.stagePreferencesByLayout[homeLayoutSet.activeKey] ?: AppStagePreferences()
        return copy(
            launcherSettings =
                launcherSettings.copy(
                    cards =
                        launcherSettings.cards.copy(
                            stagePreferencesByLayout =
                                launcherSettings.cards.stagePreferencesByLayout +
                                    (homeLayoutSet.activeKey to preferences.copy(selectedStageId = stageId)),
                        ),
                ),
        )
    }

    private fun twoPinnedStageState(
        first: InstalledApp,
        second: InstalledApp,
        selected: InstalledApp,
    ): LauncherShellState {
        val base =
            LauncherShellState(
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
                installedApps = listOf(first, second),
            )
        return base.withPinnedStages(listOf(first, second), selected = selected)
    }

    private fun threePinnedStageState(
        first: InstalledApp,
        second: InstalledApp,
        third: InstalledApp,
        selected: InstalledApp,
    ): LauncherShellState {
        val base =
            LauncherShellState(
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
                installedApps = listOf(first, second, third),
            )
        return base.withPinnedStages(listOf(first, second, third), selected = selected)
    }

    private fun LauncherShellState.withPinnedStages(
        apps: List<InstalledApp>,
        selected: InstalledApp,
    ): LauncherShellState {
        val pinnedIds = apps.map { app -> AppStageId(app.identity.packageName, app.identity.profile.id) }
        val selectedId = AppStageId(selected.identity.packageName, selected.identity.profile.id)
        return copy(
            launcherSettings =
                LauncherSettings(
                    cards =
                        CardsSettings(
                            stagePreferencesByLayout =
                                mapOf(
                                    homeLayoutSet.activeKey to
                                        AppStagePreferences(pinnedStageIds = pinnedIds, selectedStageId = selectedId),
                                ),
                        ),
                ),
        )
    }

    private fun secondStageId(second: InstalledApp): AppStageId =
        AppStageId(second.identity.packageName, second.identity.profile.id)

    private fun thirdStageId(third: InstalledApp): AppStageId =
        AppStageId(third.identity.packageName, third.identity.profile.id)

    private fun app(
        packageSuffix: String,
        label: String,
    ): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.example.$packageSuffix"),
                    activityName = AppActivityName(".Main"),
                    profile = AppProfile.personal(),
                ),
            label = label,
        )

    private companion object {
        // 600dp <= usableWidth < 1000dp resolves to TWO_PANE; >= 1000dp resolves to THREE_PANE
        // (AdaptiveStagePaneLayoutPolicy's MIN_TWO_PANE_WIDTH_DP/MIN_THREE_PANE_WIDTH_DP).
        const val TWO_PANE_WIDTH_DP = 800
        const val THREE_PANE_WIDTH_DP = 1_200
        const val PANE_HEIGHT_DP = 800
    }
}
