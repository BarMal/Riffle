package com.riffle.app.launcher

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.notifications.AppStageNotificationCard
import com.riffle.app.launcher.notifications.MediaCommand
import com.riffle.app.launcher.notifications.NotificationStageAction
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.AppStage
import com.riffle.core.domain.launcher.cards.AppStageContent
import com.riffle.core.domain.launcher.cards.AppStageContentKind
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.cards.AppStageLifecycle
import com.riffle.core.domain.launcher.cards.AppStageOrigin
import com.riffle.core.domain.launcher.cards.AppStagePreferences
import com.riffle.core.domain.launcher.cards.CardExpansionPhase
import com.riffle.core.domain.launcher.cards.CardExpansionState
import com.riffle.core.domain.launcher.cards.LauncherCardId
import com.riffle.core.domain.launcher.cards.TimeScapeWindowLayout
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.settings.CardsSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.TimeScapeAccentSource
import com.riffle.core.domain.launcher.settings.TimeScapeAppearanceSettings
import com.riffle.core.domain.launcher.settings.TimeScapeBackgroundSource
import com.riffle.core.domain.launcher.settings.TimeScapeContentDensity
import com.riffle.core.domain.launcher.settings.TimeScapeGeometry
import com.riffle.core.domain.launcher.settings.TimeScapeHapticStrength
import com.riffle.core.domain.launcher.settings.TimeScapeMotion
import com.riffle.core.domain.launcher.settings.TimeScapeSurface
import com.riffle.core.domain.launcher.settings.TimeScapeTypography
import com.riffle.core.domain.launcher.settings.TimeScapeViewportDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimeScapeCardSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun detailTransitionsUseThePersistedExpandAndExitDurations() {
        val motion = TimeScapeMotion(expandDurationMillis = 440, exitDurationMillis = 190)
        val detailState =
            TimeScapeCardDetailState(
                currentExpansion = { CardExpansionState() },
                updateExpansion = {},
                currentRecoveryMessage = { null },
                updateRecoveryMessage = {},
                motion = motion,
                globalReducedMotion = false,
            )

        assertEquals(440, detailState.transitionDurationMillis(CardExpansionPhase.EXPANDING))
        assertEquals(190, detailState.transitionDurationMillis(CardExpansionPhase.COLLAPSING))
    }

    @Test
    fun mapsTimeScapeHapticStrengthToDistinctSettleFeedback() {
        assertNull(TimeScapeHapticStrength.OFF.timeScapeSettleHapticFeedbackConstant())
        assertNotEquals(
            TimeScapeHapticStrength.LIGHT.timeScapeSettleHapticFeedbackConstant(),
            TimeScapeHapticStrength.STRONG.timeScapeSettleHapticFeedbackConstant(),
        )
    }

    @Test
    fun appStageSurfaceExplainsMissingNotificationAccess() {
        composeRule.setContent {
            MaterialTheme {
                TimeScapeAppStageSurface(
                    state = LauncherShellState(notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("Allow notification access to show your app stages.").assertIsDisplayed()
        composeRule
            .onNode(
                SemanticsMatcher
                    .expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
                    .and(hasText("Allow notification access to show your app stages.")),
            ).assertIsDisplayed()
        composeRule.onNodeWithText("Allow access").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("More stage options").assertDoesNotExist()
    }

    @Test
    fun appStageSurfaceRendersTheFocusedAppStage() {
        val app =
            InstalledApp(
                identity =
                    AppIdentity(
                        packageName = AppPackageName("com.example.mail"),
                        activityName = AppActivityName(".Main"),
                        profile = AppProfile.personal(),
                    ),
                label = "Mail",
            )
        val notification =
            LauncherNotification(
                key = LauncherNotificationKey("mail"),
                packageName = app.identity.packageName,
                profileId = app.identity.profile.id,
                title = "New message",
                text = "Hello from TimeScape",
                postedAtEpochMillis = 10,
            )
        composeRule.setContent {
            MaterialTheme {
                TimeScapeAppStageSurface(
                    state =
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
                                        notifications = listOf(notification),
                                    ),
                                ),
                        ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Mail, selected. Open stage").assertIsDisplayed()
        composeRule.onNodeWithText("New message").assertIsDisplayed()
        composeRule.onNodeWithText("Hello from TimeScape").assertIsDisplayed()
    }

    @Test
    fun cardNavigationUsesOnePoliteLiveRegionForTheSettledFocusedCard() {
        val app = timeScapeTestApp()
        val newest =
            timeScapeTestNotification(app).copy(
                key = LauncherNotificationKey("newest"),
                title = "Newest message",
                postedAtEpochMillis = 20,
            )
        val older =
            timeScapeTestNotification(app).copy(
                key = LauncherNotificationKey("older"),
                title = "Older message",
                postedAtEpochMillis = 10,
            )
        val state =
            timeScapeTestState(app, newest).copy(
                notificationGroupsByApp =
                    timeScapeTestState(app, newest).notificationGroupsByApp.map { group ->
                        group.copy(notifications = listOf(newest, older))
                    },
            )
        val focusedCardLiveRegion =
            SemanticsMatcher
                .expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
                .and(hasContentDescription("Focused", substring = true))

        composeRule.setContent {
            MaterialTheme { TimeScapeAppStageSurface(state = state, onAction = {}) }
        }

        composeRule.onAllNodes(focusedCardLiveRegion).assertCountEquals(1)
        composeRule.onNodeWithText("Older message").performClick()

        composeRule.onAllNodes(focusedCardLiveRegion).assertCountEquals(1)
    }

    @Test
    fun explicitDetailsOpensAndBackReturnsToTheFocusedCard() {
        val app = timeScapeTestApp()
        val notification = timeScapeTestNotification(app)
        composeRule.setContent {
            MaterialTheme {
                TimeScapeAppStageSurface(state = timeScapeTestState(app, notification), onAction = {})
            }
        }

        composeRule.onNodeWithText("Details").performClick()

        composeRule.onNodeWithText("Notification details").assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.mainClock.advanceTimeBy(200)

        composeRule.onAllNodesWithText("Notification details").assertCountEquals(0)
        composeRule.onNodeWithText("Details").assertIsDisplayed()
    }

    @Test
    fun focusedCardAndOpenDetailSurviveCompactAndSupportingPaneChanges() {
        val app = timeScapeTestApp()
        val newest =
            timeScapeTestNotification(app).copy(
                key = LauncherNotificationKey("newest"),
                title = "Newest message",
                text = "Selected card context",
                postedAtEpochMillis = 20,
            )
        val older =
            timeScapeTestNotification(app).copy(
                key = LauncherNotificationKey("older"),
                title = "Older message",
                postedAtEpochMillis = 10,
            )
        val testState =
            timeScapeTestState(app, newest).copy(
                notificationGroupsByApp =
                    timeScapeTestState(app, newest).notificationGroupsByApp.map { group ->
                        group.copy(notifications = listOf(newest, older))
                    },
            )
        var widthDp by mutableIntStateOf(500)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(0.3f)) {
                MaterialTheme {
                    Box(modifier = Modifier.width(widthDp.dp).height(800.dp).clipToBounds()) {
                        TimeScapeAppStageSurface(
                            state = testState,
                            windowLayout = TimeScapeWindowLayout(widthDp, 800),
                            onAction = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Older message").performClick()
        composeRule.onNodeWithText("Details").performClick()
        composeRule.onNodeWithText("Notification details").assertIsDisplayed()

        composeRule.runOnIdle { widthDp = 1_200 }
        composeRule.onNodeWithTag(TIME_SCAPE_SUPPORTING_PANE_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Notification details").assertExists()
        composeRule.runOnIdle { widthDp = 500 }
        composeRule.onNodeWithText("Notification details").assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.onNodeWithText("Older message").assertIsDisplayed()
    }

    @Test
    fun detailActionsRouteEverySupportedActionToTheFocusedNotificationKey() {
        val app = timeScapeTestApp()
        val key = LauncherNotificationKey("focused-notification")
        val card =
            AppStageNotificationCard(
                content =
                    AppStageContent(
                        id = LauncherCardId("focused-card"),
                        stageId = AppStageId(app.identity.packageName, app.identity.profile.id),
                        kind = AppStageContentKind.NOTIFICATION,
                        meaningfulActivityAtEpochMillis = 10,
                    ),
                notificationKey = key,
                title = "Focused notification",
                text = "Actions route to this notification",
                isRedacted = false,
                supportedActions =
                    setOf(
                        NotificationStageAction.Open,
                        NotificationStageAction.ProviderAction("reply"),
                        NotificationStageAction.MediaControl(MediaCommand.PLAY),
                        NotificationStageAction.Dismiss,
                    ),
            )
        val actions = mutableListOf<LauncherShellAction>()

        composeRule.setContent {
            var expansion by remember { mutableStateOf(CardExpansionState().expand(card.content.id, true)) }
            val detailState =
                remember {
                    TimeScapeCardDetailState(
                        currentExpansion = { expansion },
                        updateExpansion = { expansion = it },
                        currentRecoveryMessage = { null },
                        updateRecoveryMessage = { _ -> },
                        motion = TimeScapeMotion(reducedMotion = true),
                        globalReducedMotion = false,
                    )
                }
            MaterialTheme {
                TimeScapeCardDetailSurface(card = card, detailState = detailState, onAction = actions::add)
            }
        }

        composeRule.onNodeWithText("Action").performClick()
        composeRule.onNodeWithText("Dismiss").performClick()
        composeRule.onNodeWithText("Open").performClick()
        composeRule.onNodeWithText("Play").performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    LauncherShellAction.PerformNotificationStageAction(
                        key,
                        NotificationStageAction.ProviderAction("reply"),
                    ),
                    LauncherShellAction.PerformNotificationStageAction(key, NotificationStageAction.Dismiss),
                    LauncherShellAction.PerformNotificationStageAction(key, NotificationStageAction.Open),
                    LauncherShellAction.PerformNotificationStageAction(
                        key,
                        NotificationStageAction.MediaControl(MediaCommand.PLAY),
                    ),
                ),
                actions,
            )
        }
    }

    @Test
    fun initialNotificationStageDoesNotMoveFocusToDetails() {
        val app = timeScapeTestApp()
        composeRule.setContent {
            MaterialTheme {
                TimeScapeAppStageSurface(
                    state = timeScapeTestState(app, timeScapeTestNotification(app)),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("Details").assertIsNotFocused()
    }

    @Test
    fun emptyAppDetailsBackRestoresFocusToItsDetailsControl() {
        val app = timeScapeTestApp()
        composeRule.setContent {
            MaterialTheme { TimeScapeAppStageSurface(state = emptyPinnedStageState(app), onAction = {}) }
        }

        composeRule.onNodeWithText("Details").performClick()

        composeRule.onNodeWithText("App details").assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.mainClock.advanceTimeBy(200)

        composeRule.onNodeWithText("Details").assertIsFocused()
    }

    @Test
    fun emptyAppDetailMovesToSupportingPaneAndBackAcrossResize() {
        val app = timeScapeTestApp()
        var widthDp by mutableIntStateOf(500)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(0.3f)) {
                MaterialTheme {
                    Box(modifier = Modifier.width(widthDp.dp).height(800.dp).clipToBounds()) {
                        TimeScapeAppStageSurface(
                            state = emptyPinnedStageState(app),
                            windowLayout = TimeScapeWindowLayout(widthDp, 800),
                            onAction = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Details").performClick()
        composeRule.onNodeWithText("App details").assertIsDisplayed()

        composeRule.runOnIdle { widthDp = 1_200 }
        composeRule.onNodeWithTag(TIME_SCAPE_SUPPORTING_PANE_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("App details").assertExists()

        composeRule.runOnIdle { widthDp = 500 }
        composeRule.onNodeWithText("App details").assertIsDisplayed()
    }

    @Test
    fun removingSourceDuringBackCloseDoesNotFocusRemainingCardDetails() {
        val app = timeScapeTestApp()
        val source = timeScapeTestNotification(app).copy(key = LauncherNotificationKey("source"), postedAtEpochMillis = 20)
        val remaining =
            timeScapeTestNotification(app).copy(
                key = LauncherNotificationKey("remaining"),
                title = "Remaining notification",
                postedAtEpochMillis = 10,
            )
        var state by
            mutableStateOf(
                timeScapeTestState(app, source).copy(
                    notificationGroupsByApp =
                        listOf(
                            AppNotificationGroup(
                                packageName = app.identity.packageName,
                                profileId = app.identity.profile.id,
                                latestCategory = NotificationCategory.MESSAGE,
                                latestAgeBucket = NotificationAgeBucket.RECENT,
                                notifications = listOf(source, remaining),
                            ),
                        ),
                ),
            )
        composeRule.setContent {
            MaterialTheme { TimeScapeAppStageSurface(state = state, onAction = {}) }
        }

        composeRule.onNodeWithText("Details").performClick()
        composeRule.onNodeWithText("Notification details").assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Back").performClick()
        composeRule.runOnIdle {
            state =
                state.copy(
                    notificationGroupsByApp =
                        state.notificationGroupsByApp.map { group ->
                            group.copy(notifications = listOf(remaining))
                        },
                )
        }
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        composeRule.onNodeWithText("The selected card is no longer available.").assertIsDisplayed()
        composeRule.onNodeWithText("Details").assertIsNotFocused()
    }

    @Test
    fun initialEmptyAppStageDoesNotMoveFocusToDetails() {
        composeRule.setContent {
            MaterialTheme {
                TimeScapeAppStageSurface(
                    state = emptyPinnedStageState(timeScapeTestApp()),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("Details").assertIsNotFocused()
    }

    @Test
    fun removedExpandedEmptyAppRecoversWithoutFocusingDetachedDetailsControl() {
        val app = timeScapeTestApp()
        var state by mutableStateOf(emptyPinnedStageState(app))
        composeRule.setContent {
            MaterialTheme { TimeScapeAppStageSurface(state = state, onAction = {}) }
        }

        composeRule.onNodeWithText("Details").performClick()
        composeRule.onNodeWithText("App details").assertIsDisplayed()
        composeRule.runOnIdle { state = state.copy(installedApps = emptyList()) }

        composeRule.onNodeWithText("The selected card is no longer available.").assertIsDisplayed()
        composeRule.onAllNodesWithText("App details").assertCountEquals(0)
        composeRule.onAllNodesWithText("Details").assertCountEquals(0)
    }

    @Test
    fun removedExpandedContentReturnsToTheStageWithAnExplanation() {
        val app = timeScapeTestApp()
        val notification = timeScapeTestNotification(app)
        var state by mutableStateOf(timeScapeTestState(app, notification))
        composeRule.setContent {
            MaterialTheme { TimeScapeAppStageSurface(state = state, onAction = {}) }
        }

        composeRule.onNodeWithText("Details").performClick()
        composeRule.onNodeWithText("Notification details").assertIsDisplayed()
        composeRule.runOnIdle { state = state.copy(notificationGroupsByApp = emptyList()) }

        composeRule.onNodeWithText("The selected card is no longer available.").assertIsDisplayed()
        composeRule.onAllNodesWithText("Notification details").assertCountEquals(0)
    }

    @Test
    fun revokingNotificationAccessWhileDetailIsOpenClosesItWithAnExplanation() {
        val app = timeScapeTestApp()
        val notification = timeScapeTestNotification(app)
        var state by mutableStateOf(timeScapeTestState(app, notification))
        composeRule.setContent {
            MaterialTheme { TimeScapeAppStageSurface(state = state, onAction = {}) }
        }

        composeRule.onNodeWithText("Details").performClick()
        composeRule.onNodeWithText("Notification details").assertIsDisplayed()
        composeRule.runOnIdle { state = state.copy(notificationAccessStatus = NotificationAccessStatus.REVOKED) }

        composeRule
            .onNodeWithText("Notification access was revoked. Restore access to update stages.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("The selected card is no longer available.").assertIsDisplayed()
        composeRule.onAllNodesWithText("Notification details").assertCountEquals(0)
    }

    @Test
    fun appStageSurfaceShowsRevokedAccessAfterRetainedDynamicStage() {
        val app =
            InstalledApp(
                identity =
                    AppIdentity(
                        packageName = AppPackageName("com.example.mail"),
                        activityName = AppActivityName(".Main"),
                        profile = AppProfile.personal(),
                    ),
                label = "Mail",
            )
        val notification =
            LauncherNotification(
                key = LauncherNotificationKey("mail"),
                packageName = app.identity.packageName,
                profileId = app.identity.profile.id,
                title = "New message",
                text = "Hello from TimeScape",
                postedAtEpochMillis = 10,
            )
        var state by
            mutableStateOf(
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
                                notifications = listOf(notification),
                            ),
                        ),
                ),
            )

        composeRule.setContent {
            MaterialTheme {
                TimeScapeAppStageSurface(state = state, onAction = {})
            }
        }
        composeRule.onNodeWithText("New message").assertIsDisplayed()

        composeRule.runOnIdle {
            state = state.copy(notificationAccessStatus = NotificationAccessStatus.REVOKED)
        }

        composeRule
            .onNodeWithText("Notification access was revoked. Restore access to update stages.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Allow access").assertIsDisplayed()
        composeRule.onNodeWithText("Nothing new").assertDoesNotExist()
    }

    @Test
    fun appStageSurfaceShowsRevokedAccessForSelectedPinnedStage() {
        val app =
            InstalledApp(
                identity =
                    AppIdentity(
                        packageName = AppPackageName("com.example.mail"),
                        activityName = AppActivityName(".Main"),
                        profile = AppProfile.personal(),
                    ),
                label = "Mail",
            )
        val stageId = AppStageId(app.identity.packageName, app.identity.profile.id)

        composeRule.setContent {
            MaterialTheme {
                TimeScapeAppStageSurface(
                    state =
                        LauncherShellState(
                            notificationAccessStatus = NotificationAccessStatus.REVOKED,
                            installedApps = listOf(app),
                            profileContentVisibility =
                                mapOf(app.identity.profile.id to AppProfileContentVisibility.VISIBLE),
                            launcherSettings =
                                LauncherSettings(
                                    cards =
                                        CardsSettings(
                                            stagePreferencesByLayout =
                                                mapOf(
                                                    HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER) to
                                                        AppStagePreferences(
                                                            pinnedStageIds = listOf(stageId),
                                                            selectedStageId = stageId,
                                                        ),
                                                ),
                                        ),
                                ),
                        ),
                    onAction = {},
                )
            }
        }

        composeRule
            .onNodeWithText("Notification access was revoked. Restore access to update stages.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Allow access").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Mail, selected. Open stage").assertIsDisplayed()
        composeRule
            .onNode(
                SemanticsMatcher("selected pinned stage exposes pin state") { node ->
                    node.config
                        .getOrNull(SemanticsProperties.StateDescription)
                        ?.contains("Pinned") == true
                }.and(hasContentDescription("TimeScape stage: Mail")),
            ).assertIsDisplayed()
        composeRule.onNodeWithText("Nothing new").assertDoesNotExist()
    }

    @Test
    fun appStageHeaderOverflowExposesFunctionalStageActions() {
        val app =
            InstalledApp(
                identity =
                    AppIdentity(
                        packageName = AppPackageName("com.example.mail"),
                        activityName = AppActivityName(".Main"),
                        profile = AppProfile.personal(),
                    ),
                label = "Mail",
            )
        val notification =
            LauncherNotification(
                key = LauncherNotificationKey("mail"),
                packageName = app.identity.packageName,
                profileId = app.identity.profile.id,
                title = "New message",
                text = "Hello from TimeScape",
                postedAtEpochMillis = 10,
            )
        val actions = mutableListOf<LauncherShellAction>()
        composeRule.setContent {
            MaterialTheme {
                TimeScapeAppStageSurface(
                    state =
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
                                        notifications = listOf(notification),
                                    ),
                                ),
                        ),
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithContentDescription("More stage options").performClick()
        composeRule.onNodeWithText("Pin stage").assertIsDisplayed()
        composeRule.onNodeWithText("Open Mail").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(LauncherShellAction.LaunchApp(app.identity)), actions)
        }
    }

    @Test
    fun appStageSurfaceLabelsSamePackageProfilesIndependently() {
        val personal =
            InstalledApp(
                identity =
                    AppIdentity(
                        packageName = AppPackageName("com.example.mail"),
                        activityName = AppActivityName(".Main"),
                        profile = AppProfile.personal(),
                    ),
                label = "Mail",
            )
        val work =
            personal.copy(
                identity = personal.identity.copy(profile = AppProfile.work()),
            )
        val personalNotification =
            LauncherNotification(
                key = LauncherNotificationKey("personal"),
                packageName = personal.identity.packageName,
                profileId = personal.identity.profile.id,
                title = "Personal message",
                text = "Personal content",
                postedAtEpochMillis = 10,
            )
        val workNotification =
            LauncherNotification(
                key = LauncherNotificationKey("work"),
                packageName = work.identity.packageName,
                profileId = work.identity.profile.id,
                title = "Work message",
                text = "Work content",
                postedAtEpochMillis = 20,
            )

        composeRule.setContent {
            MaterialTheme {
                TimeScapeAppStageSurface(
                    state =
                        LauncherShellState(
                            notificationAccessStatus = NotificationAccessStatus.GRANTED,
                            installedApps = listOf(personal, work),
                            profileContentVisibility =
                                mapOf(
                                    personal.identity.profile.id to AppProfileContentVisibility.VISIBLE,
                                    work.identity.profile.id to AppProfileContentVisibility.VISIBLE,
                                ),
                            notificationGroupsByApp =
                                listOf(
                                    AppNotificationGroup(
                                        packageName = personal.identity.packageName,
                                        profileId = personal.identity.profile.id,
                                        latestCategory = NotificationCategory.MESSAGE,
                                        latestAgeBucket = NotificationAgeBucket.RECENT,
                                        notifications = listOf(personalNotification),
                                    ),
                                    AppNotificationGroup(
                                        packageName = work.identity.packageName,
                                        profileId = work.identity.profile.id,
                                        latestCategory = NotificationCategory.MESSAGE,
                                        latestAgeBucket = NotificationAgeBucket.RECENT,
                                        notifications = listOf(workNotification),
                                    ),
                                ),
                        ),
                    onAction = {},
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Work - Mail, selected. Open stage")
            .assertIsDisplayed()
    }

    @Test
    fun stageSelectorUsesStableSaveableKeysForProfileScopedStages() {
        val personal = AppStageId(AppPackageName("com.example.mail"), AppProfile.personal().id)
        val work = AppStageId(AppPackageName("com.example.mail"), AppProfile.work().id)

        assertEquals(
            "personal:com.example.mail",
            timeScapeStageSelectorItemKey(
                AppStage(personal, setOf(AppStageOrigin.DYNAMIC), AppStageLifecycle.EMPTY),
            ),
        )
        assertEquals(
            "work:com.example.mail",
            timeScapeStageSelectorItemKey(
                AppStage(work, setOf(AppStageOrigin.DYNAMIC), AppStageLifecycle.EMPTY),
            ),
        )
    }

    @Test
    fun everyBackgroundSourceRendersCardContentWithAFallback() {
        val appearances =
            TimeScapeBackgroundSource.entries.map { source ->
                TimeScapeAppearanceSettings(surface = TimeScapeSurface(backgroundSource = source))
            }

        composeRule.setContent {
            MaterialTheme {
                appearances.forEachIndexed { index, appearance ->
                    TimeScapeCardSurface(
                        appearance = appearance,
                        background = TimeScapeCardBackground(appSeed = "card-$index"),
                    ) {
                        Text("Card $index")
                    }
                }
            }
        }

        appearances.indices.forEach { index -> composeRule.onNodeWithText("Card $index").assertIsDisplayed() }
    }

    @Test
    fun reducedTransparencyKeepsAnOpaqueLegibleSurface() {
        val colors =
            resolveTimeScapeCardColors(
                appearance =
                    TimeScapeAppearanceSettings(
                        surface =
                            TimeScapeSurface(
                                customBackgroundArgb = 0xFF101010L,
                                backgroundSource = TimeScapeBackgroundSource.CUSTOM_SOLID,
                            ),
                        motion = TimeScapeMotion(reducedTransparency = true),
                    ),
                background = TimeScapeCardBackground(),
                materialBackground = Color.White,
                materialAccent = Color.Blue,
            )

        assertEquals(1f, colors.glass.alpha)
        assertEquals(1f, colors.glassTint.alpha)
        assertTrue(contrastRatio(colors.foreground, colors.glass) >= 4.5f)
    }

    @Test
    fun foregroundMaintainsContrastForAutomaticAndMaterialTypographyModes() {
        val modes =
            listOf(
                false to 0xFFFFFFFFL,
                true to 0xFF000000L,
            )

        modes.forEach { (automaticForegroundContrast, backgroundArgb) ->
            val colors =
                resolveTimeScapeCardColors(
                    appearance =
                        TimeScapeAppearanceSettings(
                            surface =
                                TimeScapeSurface(
                                    backgroundSource = TimeScapeBackgroundSource.CUSTOM_SOLID,
                                    customBackgroundArgb = backgroundArgb,
                                    glassTintArgb = backgroundArgb,
                                    glassTransparencyPercent = 0,
                                ),
                            typography =
                                TimeScapeTypography(
                                    automaticForegroundContrast = automaticForegroundContrast,
                                ),
                        ),
                    background = TimeScapeCardBackground(),
                    materialBackground = Color.White,
                    materialAccent = Color.Blue,
                )

            assertTrue(contrastRatio(colors.foreground, colors.glass) >= 4.5f)
        }
    }

    @Test
    fun artworkRemainsVisuallyRepresentedBelowTranslucentGlassTint() {
        val artwork =
            Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply {
                for (x in 0 until width) {
                    val color = if (x < width / 2) android.graphics.Color.BLUE else android.graphics.Color.RED
                    for (y in 0 until height) setPixel(x, y, color)
                }
            }.asImageBitmap()
        val appearance =
            TimeScapeAppearanceSettings(
                surface =
                    TimeScapeSurface(
                        backgroundSource = TimeScapeBackgroundSource.NOTIFICATION_ARTWORK,
                        glassTintArgb = 0xFFFFFFFFL,
                        glassTransparencyPercent = 50,
                        blurStrengthPercent = 0,
                    ),
            )

        composeRule.setContent {
            MaterialTheme {
                TimeScapeCardSurface(
                    appearance = appearance,
                    background = TimeScapeCardBackground(artwork = artwork),
                    modifier = Modifier.requiredSize(120.dp).testTag("artwork-card"),
                ) {}
            }
        }

        val rendered = composeRule.onNodeWithTag("artwork-card").captureToImage()
        val pixels = rendered.toPixelMap()
        // The 20dp content scrim protects the centre; sample the exposed artwork band instead.
        val left = pixels[rendered.width / 10, rendered.height / 2]
        val right = pixels[rendered.width * 9 / 10, rendered.height / 2]

        assertTrue(left.blue > left.red)
        assertTrue(right.red > right.blue)
    }

    @Test
    fun mixedArtworkUsesAnOpaqueContentScrimForContrast() {
        val artwork =
            Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply {
                for (x in 0 until width) {
                    val color = if (x < width / 2) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                    for (y in 0 until height) setPixel(x, y, color)
                }
            }.asImageBitmap()
        val appearance =
            TimeScapeAppearanceSettings(
                surface =
                    TimeScapeSurface(
                        backgroundSource = TimeScapeBackgroundSource.NOTIFICATION_ARTWORK,
                        glassTintArgb = 0xFFFFFFFFL,
                        glassTransparencyPercent = 95,
                        blurStrengthPercent = 0,
                    ),
            )

        composeRule.setContent {
            MaterialTheme {
                TimeScapeCardSurface(
                    appearance = appearance,
                    background = TimeScapeCardBackground(artwork = artwork),
                    modifier = Modifier.requiredSize(160.dp).testTag("mixed-artwork-card"),
                ) {}
            }
        }

        val rendered = composeRule.onNodeWithTag("mixed-artwork-card").captureToImage()
        val pixels = rendered.toPixelMap()
        val left = pixels[rendered.width / 4, rendered.height / 2]
        val right = pixels[rendered.width * 3 / 4, rendered.height / 2]
        val colors =
            resolveTimeScapeCardColors(
                appearance = appearance,
                background = TimeScapeCardBackground(artwork = artwork),
                materialBackground = Color.Black,
                materialAccent = Color.Blue,
            )

        assertEquals(left.red, right.red, 0.03f)
        assertEquals(left.green, right.green, 0.03f)
        assertEquals(left.blue, right.blue, 0.03f)
        assertTrue(contrastRatio(colors.foreground, colors.glass) >= 4.5f)
    }

    @Test
    fun saturationAndContrastAdjustFallbackBackgrounds() {
        val original = Color(0.8f, 0.2f, 0.1f)

        val desaturated = timeScapeAdjustedColor(original, saturationPercent = 0, contrastPercent = 100)
        val contrasted = timeScapeAdjustedColor(original, saturationPercent = 100, contrastPercent = 150)

        assertEquals(desaturated.red, desaturated.green, 0.001f)
        assertEquals(desaturated.green, desaturated.blue, 0.001f)
        assertNotEquals(original, contrasted)
    }

    @Test
    fun typographyProjectsAccentTextScaleAndContentDensity() {
        var observedAction = Color.Unspecified
        var observedFontScale = 0f
        val appearance =
            TimeScapeAppearanceSettings(
                typography =
                    TimeScapeTypography(
                        accentSource = TimeScapeAccentSource.CUSTOM,
                        customAccentArgb = 0xFF336699L,
                        contentDensity = TimeScapeContentDensity.EXPANDED,
                        textScalePercent = 130,
                    ),
            )

        composeRule.setContent {
            MaterialTheme {
                TimeScapeCardSurface(appearance, TimeScapeCardBackground()) {
                    observedAction = MaterialTheme.colorScheme.primary
                    observedFontScale = LocalDensity.current.fontScale
                    Text("Styled card")
                }
            }
        }

        composeRule.onNodeWithText("Styled card").assertIsDisplayed()
        composeRule.runOnIdle {
            val colors =
                resolveTimeScapeCardColors(
                    appearance = appearance,
                    background = TimeScapeCardBackground(),
                    materialBackground = Color.Black,
                    materialAccent = Color.Blue,
                )
            assertEquals(Color(0xFF336699), colors.accent)
            assertTrue(contrastRatio(observedAction, colors.glass) >= 4.5f)
            assertEquals(1.3f, observedFontScale, 0.001f)
            assertEquals(1.2f, timeScapeContentDensityScale(TimeScapeContentDensity.EXPANDED), 0.001f)
            assertEquals(0.8f, timeScapeContentDensityScale(TimeScapeContentDensity.COMPACT), 0.001f)
        }
    }

    @Test
    fun cardActionsRemainLegibleForLowContrastCustomAccents() {
        val actionColors = mutableMapOf<Int, Pair<Color, Color>>()
        val appearances =
            listOf(
                TimeScapeAppearanceSettings(
                    surface =
                        TimeScapeSurface(
                            backgroundSource = TimeScapeBackgroundSource.CUSTOM_SOLID,
                            customBackgroundArgb = 0xFFFFFFFFL,
                            glassTintArgb = 0xFFFFFFFFL,
                            glassTransparencyPercent = 0,
                        ),
                    typography =
                        TimeScapeTypography(
                            accentSource = TimeScapeAccentSource.CUSTOM,
                            customAccentArgb = 0xFFFFFFFFL,
                        ),
                ),
                TimeScapeAppearanceSettings(
                    surface =
                        TimeScapeSurface(
                            backgroundSource = TimeScapeBackgroundSource.CUSTOM_SOLID,
                            customBackgroundArgb = 0xFF000000L,
                            glassTintArgb = 0xFF000000L,
                            glassTransparencyPercent = 0,
                        ),
                    typography =
                        TimeScapeTypography(
                            accentSource = TimeScapeAccentSource.CUSTOM,
                            customAccentArgb = 0xFF000000L,
                        ),
                ),
            )

        composeRule.setContent {
            MaterialTheme {
                appearances.forEachIndexed { index, appearance ->
                    TimeScapeCardSurface(appearance, TimeScapeCardBackground()) {
                        actionColors[index] = MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
                        TextButton(onClick = {}) { Text("Action $index") }
                    }
                }
            }
        }

        composeRule.onNodeWithText("Action 0").assertIsDisplayed()
        composeRule.onNodeWithText("Action 1").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(2, actionColors.size)
            appearances.indices.forEach { index ->
                val colors =
                    resolveTimeScapeCardColors(
                        appearance = appearances[index],
                        background = TimeScapeCardBackground(),
                        materialBackground = Color.Black,
                        materialAccent = Color.Blue,
                    )
                val (action, onAction) = requireNotNull(actionColors[index])
                assertTrue(contrastRatio(action, colors.glass) >= 4.5f)
                assertTrue(contrastRatio(onAction, action) >= 4.5f)
            }
        }
    }

    @Test
    fun oldPlatformDisablesBlurWithoutChangingStoredAppearance() {
        val appearance = TimeScapeAppearanceSettings(surface = TimeScapeSurface(blurStrengthPercent = 72))

        assertFalse(timeScapeRendererCapabilities(sdkInt = 30).supportsBlur)
        assertEquals(72, appearance.surface.blurStrengthPercent)
        assertEquals(0, appearance.effectiveFor(timeScapeRendererCapabilities(sdkInt = 30)).surface.blurStrengthPercent)
    }

    @Test
    fun corruptOrOversizedArtworkFallsBackAndDecodingIsBounded() {
        assertNull(decodeTimeScapeArtwork("not-base64"))
        assertNull(decodeTimeScapeArtwork("a".repeat(2_800_001)))
        assertEquals(2, timeScapeArtworkSampleSize(width = 1_024, height = 600))
        assertEquals(4, timeScapeArtworkSampleSize(width = 3_000, height = 900))
    }

    @Test
    fun constrainedViewportUsesTheReachableNotificationListFallback() {
        val profile = AppProfile.personal()
        val group =
            AppNotificationGroup(
                packageName = AppPackageName("com.riffle.mail"),
                profileId = profile.id,
                latestCategory = NotificationCategory.EMAIL,
                latestAgeBucket = NotificationAgeBucket.RECENT,
                notifications =
                    listOf(
                        LauncherNotification(
                            key = LauncherNotificationKey("mail-1"),
                            packageName = AppPackageName("com.riffle.mail"),
                            profileId = profile.id,
                            title = "Mail",
                            postedAtEpochMillis = 1L,
                        ),
                    ),
            )

        composeRule.setContent {
            MaterialTheme {
                GeneratedNotificationCardsPage(
                    groups = listOf(group),
                    notificationAccessStatus = NotificationAccessStatus.GRANTED,
                    apps = emptyList(),
                    onAction = {},
                    reducedMotion = false,
                    modifier = Modifier.requiredSize(80.dp),
                )
            }
        }

        composeRule.onNodeWithTag(GENERATED_NOTIFICATION_CARD_LIST_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun previewAndProductionCardsUseTheSameCappedContentPaddingOnConstrainedViewports() {
        val resolution =
            TimeScapeAppearanceSettings(
                geometry = TimeScapeGeometry(contentPaddingDp = 64),
            ).resolveCardStack(
                viewport = TimeScapeViewportDp(widthDp = 500, heightDp = 475),
            )

        assertTrue(resolution.isUsable)
        assertEquals(
            resolution.contentPaddingDp.dp,
            timeScapeResolvedContentPadding(resolution),
        )
        assertTrue(resolution.contentPaddingDp < 64)
    }

    @Test
    fun resolvedStackRetainsFocusedCardAsHighestOrderEntryWithoutMotion() {
        val appearance = TimeScapeAppearanceSettings(motion = TimeScapeMotion(reducedMotion = true))
        val entries =
            appearance
                .resolveCardStack(
                    viewport = com.riffle.core.domain.launcher.settings.TimeScapeViewportDp(800, 1_200),
                    globalReducedMotion = true,
                ).layoutPolicy
                .entries(cardCount = 3, activeIndex = 1, reducedMotion = true)

        assertEquals(1, entries.maxBy { entry -> entry.order }.cardIndex)
        assertTrue(entries.all { entry -> entry.rotationDegrees == 0f })
    }

    private fun timeScapeTestApp(): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.example.mail"),
                    activityName = AppActivityName(".Main"),
                    profile = AppProfile.personal(),
                ),
            label = "Mail",
        )

    private fun timeScapeTestNotification(app: InstalledApp): LauncherNotification =
        LauncherNotification(
            key = LauncherNotificationKey("mail"),
            packageName = app.identity.packageName,
            profileId = app.identity.profile.id,
            title = "New message",
            text = "Hello from TimeScape",
            postedAtEpochMillis = 10,
        )

    private fun timeScapeTestState(
        app: InstalledApp,
        notification: LauncherNotification,
    ): LauncherShellState =
        LauncherShellState(
            notificationAccessStatus = NotificationAccessStatus.GRANTED,
            installedApps = listOf(app),
            profileContentVisibility = mapOf(app.identity.profile.id to AppProfileContentVisibility.VISIBLE),
            notificationGroupsByApp =
                listOf(
                    AppNotificationGroup(
                        packageName = app.identity.packageName,
                        profileId = app.identity.profile.id,
                        latestCategory = NotificationCategory.MESSAGE,
                        latestAgeBucket = NotificationAgeBucket.RECENT,
                        notifications = listOf(notification),
                    ),
                ),
        )

    private fun emptyPinnedStageState(app: InstalledApp): LauncherShellState {
        val stageId = AppStageId(app.identity.packageName, app.identity.profile.id)
        return LauncherShellState(
            notificationAccessStatus = NotificationAccessStatus.GRANTED,
            installedApps = listOf(app),
            profileContentVisibility = mapOf(app.identity.profile.id to AppProfileContentVisibility.VISIBLE),
            launcherSettings =
                LauncherSettings(
                    cards =
                        CardsSettings(
                            stagePreferencesByLayout =
                                mapOf(
                                    HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER) to
                                        AppStagePreferences(
                                            pinnedStageIds = listOf(stageId),
                                            selectedStageId = stageId,
                                        ),
                                ),
                        ),
                ),
        )
    }
}
