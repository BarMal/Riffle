package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [CardsDockEdgeCardPanel]: the redesign of the old two-pill dock-edge header (an identity pill
 * plus a separate pin/overflow capsule -- see #1301/#1306) into one card-shaped [GlassSurface]
 * panel. [AdaptiveStageCardSurfaceTest] already covers the panel's semantics/behaviour (pin
 * toggle, overflow menu contents, stage-navigation custom actions) exhaustively via its own
 * direct [CardsDockEdgeCardPanel] calls; this file covers only what is new about the redesign
 * itself -- that it renders as one card, and that the card bounds its own size.
 */
class CardsDockEdgeCardPanelTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersAsOneCardContainingBothIdentityAndControls() {
        val app = cardPanelTestApp()
        val state = cardPanelTestState(app)

        composeRule.setContent {
            MaterialTheme {
                val shellState = rememberAppStageShellState(state)
                CardsDockEdgeCardPanel(
                    selectedStage = shellState.snapshot.selectedStage,
                    allNotificationsSelected = false,
                    stages = shellState.snapshot.stages,
                    state = state,
                    appIconLoader = EmptyAppIconLoader,
                    position = DockPosition.BOTTOM,
                    onAction = {},
                )
            }
        }

        // One glass surface for the whole panel, not two separately-shaped pills.
        composeRule.onAllNodesWithTag(CARDS_DOCK_EDGE_CARD_PANEL_TEST_TAG).assertCountEquals(1)
        // Everything the old two pills exposed is still reachable -- just from inside that one card.
        composeRule.onNodeWithContentDescription("Cards stage: Mail").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(ADAPTIVE_STAGE_PIN_TOGGLE_LABEL).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(ADAPTIVE_STAGE_OVERFLOW_LABEL).assertIsDisplayed()
    }

    @Test
    fun widthNeverExceedsItsOwnMaxBoundEvenWithALongAppName() {
        val app = cardPanelTestApp().copy(label = "A Very Long Application Name That Keeps On Going And Going")
        val state = cardPanelTestState(app)

        composeRule.setContent {
            MaterialTheme {
                val shellState = rememberAppStageShellState(state)
                CardsDockEdgeCardPanel(
                    selectedStage = shellState.snapshot.selectedStage,
                    allNotificationsSelected = false,
                    stages = shellState.snapshot.stages,
                    state = state,
                    appIconLoader = EmptyAppIconLoader,
                    position = DockPosition.BOTTOM,
                    onAction = {},
                )
            }
        }

        val bounds = composeRule.onAllNodesWithTag(CARDS_DOCK_EDGE_CARD_PANEL_TEST_TAG)[0].getBoundsInRoot()
        val width = bounds.right - bounds.left
        assertTrue(
            "Panel width $width exceeded its own $CARDS_DOCK_EDGE_CARD_PANEL_MAX_WIDTH_DP dp cap",
            width <= CARDS_DOCK_EDGE_CARD_PANEL_MAX_WIDTH_DP.dp,
        )
    }

    private fun cardPanelTestApp(): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.example.mail"),
                    activityName = AppActivityName(".Main"),
                    profile = AppProfile.personal(),
                ),
            label = "Mail",
        )

    private fun cardPanelTestState(app: InstalledApp): LauncherShellState =
        LauncherShellState(
            notificationAccessStatus = NotificationAccessStatus.GRANTED,
            installedApps = listOf(app),
            profileContentVisibility =
                mapOf(app.identity.profile.id to AppProfileContentVisibility.VISIBLE),
            notificationGroupsByApp =
                listOf(
                    AppNotificationGroup(
                        packageName = app.identity.packageName,
                        profileId = app.identity.profile.id,
                        latestCategory = NotificationCategory.MESSAGE,
                        latestAgeBucket = NotificationAgeBucket.RECENT,
                        notifications =
                            listOf(
                                LauncherNotification(
                                    key = LauncherNotificationKey("mail"),
                                    packageName = app.identity.packageName,
                                    profileId = app.identity.profile.id,
                                    title = "New message",
                                    text = "Hello from Cards",
                                    postedAtEpochMillis = 10,
                                ),
                            ),
                    ),
                ),
        )
}
