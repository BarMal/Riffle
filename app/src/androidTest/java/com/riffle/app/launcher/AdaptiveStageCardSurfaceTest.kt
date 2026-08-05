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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
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
import com.riffle.core.domain.launcher.cards.AdaptiveStagePaneArrangement
import com.riffle.core.domain.launcher.cards.AdaptiveStagePosture
import com.riffle.core.domain.launcher.cards.AdaptiveStageWindowLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.settings.CardsSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.AdaptiveStageAccentSource
import com.riffle.core.domain.launcher.settings.AdaptiveStageAppearanceSettings
import com.riffle.core.domain.launcher.settings.AdaptiveStageBackgroundSource
import com.riffle.core.domain.launcher.settings.AdaptiveStageContentDensity
import com.riffle.core.domain.launcher.settings.AdaptiveStageGeometry
import com.riffle.core.domain.launcher.settings.AdaptiveStageHapticStrength
import com.riffle.core.domain.launcher.settings.AdaptiveStageMotion
import com.riffle.core.domain.launcher.settings.AdaptiveStageSurface
import com.riffle.core.domain.launcher.settings.AdaptiveStageTypography
import com.riffle.core.domain.launcher.settings.AdaptiveStageViewportDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AdaptiveStageCardSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun detailTransitionsUseThePersistedExpandAndExitDurations() {
        val motion = AdaptiveStageMotion(expandDurationMillis = 440, exitDurationMillis = 190)
        val detailState =
            AdaptiveStageCardDetailState(
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
    fun mapsAdaptiveStageHapticStrengthToDistinctSettleFeedback() {
        assertNull(AdaptiveStageHapticStrength.OFF.adaptiveStageSettleHapticFeedbackConstant())
        assertNotEquals(
            AdaptiveStageHapticStrength.LIGHT.adaptiveStageSettleHapticFeedbackConstant(),
            AdaptiveStageHapticStrength.STRONG.adaptiveStageSettleHapticFeedbackConstant(),
        )
    }

    @Test
    fun appStageSurfaceExplainsMissingNotificationAccess() {
        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageAppStageSurface(
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
    fun stageHeaderDoesNotRepeatAdaptiveStageWhenNoStageIsSelected() {
        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageAppStageSurface(
                    state = LauncherShellState(notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED),
                    onAction = {},
                )
            }
        }

        // The header title falls back to "Cards" when no stage is selected, and used to be
        // followed by an unconditional "Cards" eyebrow subtitle underneath it -- stacking the
        // same text twice. Only one instance should ever be on screen at a time.
        composeRule.onAllNodesWithText("Cards").assertCountEquals(1)
    }

    @Test
    fun appStageSurfaceOffersInstalledAppsBeforeNotificationsExist() {
        val app = adaptiveStageTestApp()
        val actions = mutableListOf<LauncherShellAction>()

        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageAppStageSurface(
                    state =
                        LauncherShellState(
                            notificationAccessStatus = NotificationAccessStatus.GRANTED,
                            installedApps = listOf(app),
                        ),
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithText("Choose an app to keep as a stage.").assertIsDisplayed()
        composeRule.onNodeWithText("Pin ${app.label}").performClick()
        assertEquals(
            LauncherShellAction.ToggleAppStagePinned(
                AppStageId(app.identity.packageName, app.identity.profile.id),
            ),
            actions.single(),
        )
    }

    @Test
    fun pinnedStageOffersAnotherInstalledAppForPinning() {
        val first = adaptiveStageTestApp()
        val second =
            first.copy(
                identity =
                    first.identity.copy(
                        packageName = AppPackageName("com.example.calendar"),
                    ),
                label = "Calendar",
            )
        val firstStageId = AppStageId(first.identity.packageName, first.identity.profile.id)
        val cardLayout = HomeLayoutDefaults.standard().copy(viewMode = LauncherViewMode.CARD_INTERFACE)
        val cardLayoutSet = HomeLayoutSet.fromLayout(cardLayout)
        val actions = mutableListOf<LauncherShellAction>()

        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageAppStageSurface(
                    state =
                        LauncherShellState(
                            homeLayout = cardLayout,
                            homeLayoutSet = cardLayoutSet,
                            notificationAccessStatus = NotificationAccessStatus.GRANTED,
                            installedApps = listOf(first, second),
                            launcherSettings =
                                LauncherSettings(
                                    cards =
                                        CardsSettings(
                                            stagePreferencesByLayout =
                                                mapOf(
                                                    HomeLayoutKey(LauncherViewMode.CARD_INTERFACE) to
                                                        AppStagePreferences(
                                                            pinnedStageIds = listOf(firstStageId),
                                                            selectedStageId = firstStageId,
                                                        ),
                                                ),
                                        ),
                                ),
                        ),
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithText("Add stage").performClick()
        composeRule.onNodeWithText("Pin ${second.label}").performClick()

        assertEquals(
            LauncherShellAction.ToggleAppStagePinned(
                AppStageId(second.identity.packageName, second.identity.profile.id),
            ),
            actions.single(),
        )
    }

    @Test
    fun emptyPinnedStageKeepsLaunchAffordanceWithoutNotificationAccess() {
        val app = adaptiveStageTestApp()
        val stageId = AppStageId(app.identity.packageName, app.identity.profile.id)
        val actions = mutableListOf<LauncherShellAction>()

        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageAppStageSurface(
                    state =
                        LauncherShellState(
                            notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED,
                            installedApps = listOf(app),
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
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithText("Stage ready").assertIsDisplayed()
        // The empty-stage placeholder now renders inside a fixed-size AdaptiveStageCardSurface
        // (matching populated-card sizing) with its content in a scrollable Column, so an
        // affordance below the fold needs a scroll before it can be clicked, same as on a real
        // device.
        composeRule.onNodeWithText("Open ${app.label}").performScrollTo().performClick()
        assertEquals(LauncherShellAction.LaunchApp(app.identity), actions.single())
    }

    @Test
    fun emptyPinnedStageRendersInsideCardSurfaceContainer() {
        val app = adaptiveStageTestApp()
        val stageId = AppStageId(app.identity.packageName, app.identity.profile.id)

        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageAppStageSurface(
                    state =
                        LauncherShellState(
                            notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED,
                            installedApps = listOf(app),
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

        composeRule.onNodeWithTag(ADAPTIVE_STAGE_EMPTY_STAGE_CARD_TEST_TAG).assertIsDisplayed()
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
                text = "Hello from Cards",
                postedAtEpochMillis = 10,
            )
        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageAppStageSurface(
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
        composeRule.onNodeWithText("Hello from Cards").assertIsDisplayed()
    }

    @Test
    fun cardNavigationUsesOnePoliteLiveRegionForTheSettledFocusedCard() {
        val app = adaptiveStageTestApp()
        val newest =
            adaptiveStageTestNotification(app).copy(
                key = LauncherNotificationKey("newest"),
                title = "Newest message",
                postedAtEpochMillis = 20,
            )
        val older =
            adaptiveStageTestNotification(app).copy(
                key = LauncherNotificationKey("older"),
                title = "Older message",
                postedAtEpochMillis = 10,
            )
        val state =
            adaptiveStageTestState(app, newest).copy(
                notificationGroupsByApp =
                    adaptiveStageTestState(app, newest).notificationGroupsByApp.map { group ->
                        group.copy(notifications = listOf(newest, older))
                    },
            )
        val focusedCardLiveRegion =
            SemanticsMatcher
                .expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
                .and(hasContentDescription("Focused", substring = true))

        composeRule.setContent {
            MaterialTheme { AdaptiveStageAppStageSurface(state = state, onAction = {}) }
        }

        composeRule.onAllNodes(focusedCardLiveRegion).assertCountEquals(1)
        composeRule.onNodeWithText("Older message").performClick()

        composeRule.onAllNodes(focusedCardLiveRegion).assertCountEquals(1)
    }

    @Test
    fun explicitDetailsOpensAndBackReturnsToTheFocusedCard() {
        val app = adaptiveStageTestApp()
        val notification = adaptiveStageTestNotification(app)
        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageAppStageSurface(state = adaptiveStageTestState(app, notification), onAction = {})
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
    fun cardStackStaysComposedButDimmedWhileDetailIsExpanded() {
        val app = adaptiveStageTestApp()
        val notification = adaptiveStageTestNotification(app)
        // A bare "Focused" content-description substring is ambiguous in this tree:
        // AdaptiveStageCardNavigationControls' position indicator also carries a "Focused card
        // position" content description (see cardNavigationUsesOnePoliteLiveRegionForTheSettled-
        // FocusedCard, which disambiguates the same way). Only the focused CardStack entry's own
        // semantics also tag a polite live region, so AND-ing on that uniquely targets it instead
        // of matching either node depending on composition order.
        val focusedCardDescription =
            SemanticsMatcher
                .expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
                .and(hasContentDescription("Focused", substring = true))
        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageAppStageSurface(state = adaptiveStageTestState(app, notification), onAction = {})
            }
        }

        composeRule.onNode(focusedCardDescription).assertExists()

        composeRule.onNodeWithText("Details").performClick()
        composeRule.mainClock.advanceTimeBy(500)

        composeRule.onNodeWithText("Notification details").assertIsDisplayed()
        // The underlying card stack (including the focused card behind the detail overlay) stays
        // in the semantics tree -- dimmed via CardStack's dimFactor, not torn down -- rather than
        // being branched away entirely.
        composeRule.onNode(focusedCardDescription).assertExists()
    }

    @Test
    fun splitArrangementShowsExpandedDetailOnlyOnceNotDuplicatedInTheLowerStack() {
        val app = adaptiveStageTestApp()
        val notification = adaptiveStageTestNotification(app)
        val splitState =
            adaptiveStageTestState(app, notification).copy(
                launcherSettings =
                    LauncherSettings(
                        cards = CardsSettings(adaptiveStagePaneArrangement = AdaptiveStagePaneArrangement.SPLIT),
                    ),
            )
        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageAppStageSurface(
                    state = splitState,
                    windowLayout = AdaptiveStageWindowLayout(widthDp = 360, heightDp = 800, posture = AdaptiveStagePosture.UNFOLDED),
                    onAction = {},
                )
            }
        }

        // SPLIT mode legitimately shows more than one "Details" affordance for the same focused
        // card at once (the upper AdaptiveStageSupportingPane's own context shelf, and the lower
        // stack's card content) -- both drive the same detailState.expand(...) for the same card,
        // so clicking either is equivalent; disambiguate by picking the first clickable one.
        composeRule.onAllNodes(hasText("Details").and(hasClickAction()))[0].performClick()
        composeRule.mainClock.advanceTimeBy(500)

        // Only the upper pane should show the expanded card's detail -- the lower stack must
        // suppress its own inline detail (showDetailInline = false) rather than duplicating it.
        composeRule.onAllNodesWithText("Notification details").assertCountEquals(1)
    }

    @Test
    fun focusedCardAndOpenDetailSurviveCompactAndSupportingPaneChanges() {
        val app = adaptiveStageTestApp()
        val newest =
            adaptiveStageTestNotification(app).copy(
                key = LauncherNotificationKey("newest"),
                title = "Newest message",
                text = "Selected card context",
                postedAtEpochMillis = 20,
            )
        val older =
            adaptiveStageTestNotification(app).copy(
                key = LauncherNotificationKey("older"),
                title = "Older message",
                postedAtEpochMillis = 10,
            )
        val testState =
            adaptiveStageTestState(app, newest).copy(
                notificationGroupsByApp =
                    adaptiveStageTestState(app, newest).notificationGroupsByApp.map { group ->
                        group.copy(notifications = listOf(newest, older))
                    },
            )
        var widthDp by mutableIntStateOf(500)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(0.3f)) {
                MaterialTheme {
                    Box(modifier = Modifier.width(widthDp.dp).height(800.dp).clipToBounds()) {
                        AdaptiveStageAppStageSurface(
                            state = testState,
                            windowLayout = AdaptiveStageWindowLayout(widthDp, 800, posture = AdaptiveStagePosture.UNFOLDED),
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
        composeRule.onNodeWithTag(ADAPTIVE_STAGE_SUPPORTING_PANE_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Notification details").assertExists()
        composeRule.runOnIdle { widthDp = 500 }
        composeRule.onNodeWithText("Notification details").assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.onNodeWithText("Older message").assertIsDisplayed()
    }

    @Test
    fun notificationRefreshKeepsFocusedCardByStableIdentity() {
        val app = adaptiveStageTestApp()
        val initial =
            adaptiveStageTestState(
                app,
                adaptiveStageTestNotification(app).copy(text = "Before refresh"),
            )
        var state by mutableStateOf(initial)

        composeRule.setContent {
            MaterialTheme { AdaptiveStageAppStageSurface(state = state, onAction = {}) }
        }

        composeRule.onNodeWithText("Before refresh").performClick()
        composeRule.runOnIdle {
            state =
                initial.copy(
                    notificationGroupsByApp =
                        initial.notificationGroupsByApp.map { group ->
                            group.copy(
                                notifications =
                                    group.notifications.map { notification ->
                                        notification.copy(text = "After refresh")
                                    },
                            )
                        },
                )
        }

        composeRule.onNodeWithText("After refresh").assertIsDisplayed()
    }

    @Test
    fun stageHeaderExposesPreviousAndNextStageAsCustomAccessibilityActions() {
        val first = adaptiveStageTestApp()
        val second =
            first.copy(
                identity =
                    first.identity.copy(
                        packageName = AppPackageName("com.example.calendar"),
                    ),
                label = "Calendar",
            )
        val firstNotification = adaptiveStageTestNotification(first)
        val secondNotification =
            firstNotification.copy(
                key = LauncherNotificationKey("calendar"),
                packageName = second.identity.packageName,
                title = "Calendar event",
            )
        val actions = mutableListOf<LauncherShellAction>()
        val state =
            LauncherShellState(
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
                installedApps = listOf(first, second),
                profileContentVisibility =
                    mapOf(
                        first.identity.profile.id to AppProfileContentVisibility.VISIBLE,
                    ),
                notificationGroupsByApp =
                    listOf(
                        notificationGroup(first, firstNotification),
                        notificationGroup(second, secondNotification),
                    ),
            )

        composeRule.setContent {
            MaterialTheme { AdaptiveStageAppStageSurface(state = state, onAction = actions::add) }
        }

        // Previous/Next are no longer visible buttons (removed as redundant with tapping a stage
        // directly, or swiping) -- they're reachable via AdaptiveStageStageHeader's customActions,
        // the same CustomAccessibilityAction pattern already used for intra-stack card navigation
        // (see WidgetPickerSurfaceTest for the identical precedent).
        val headerActions =
            composeRule
                .onNodeWithTag(ADAPTIVE_STAGE_STAGE_HEADER_TEST_TAG)
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
        headerActions.first { action -> action.label == "Next stage" }.action()
        headerActions.first { action -> action.label == "Previous stage" }.action()

        assertEquals(
            listOf(
                LauncherShellAction.SelectNextAppStage,
                LauncherShellAction.SelectPreviousAppStage,
            ),
            actions,
        )
    }

    @Test
    fun tappingAStageRailTileSelectsThatStage() {
        val first = adaptiveStageTestApp()
        val second =
            first.copy(
                identity = first.identity.copy(packageName = AppPackageName("com.example.calendar")),
                label = "Calendar",
            )
        val firstNotification = adaptiveStageTestNotification(first)
        // Older than firstNotification so AppStagePlanner's default-selection tie-break (most
        // recent content wins) deterministically leaves "Calendar" unselected regardless of how
        // package names happen to sort -- this test taps it expecting the plain, unselected
        // "Calendar. Open stage" content description.
        val secondNotification =
            firstNotification.copy(
                key = LauncherNotificationKey("calendar"),
                packageName = second.identity.packageName,
                title = "Calendar event",
                postedAtEpochMillis = firstNotification.postedAtEpochMillis - 1,
            )
        val actions = mutableListOf<LauncherShellAction>()
        val state =
            LauncherShellState(
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
                installedApps = listOf(first, second),
                profileContentVisibility =
                    mapOf(
                        first.identity.profile.id to AppProfileContentVisibility.VISIBLE,
                    ),
                notificationGroupsByApp =
                    listOf(
                        notificationGroup(first, firstNotification),
                        notificationGroup(second, secondNotification),
                    ),
            )

        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageAppStageSurface(
                    state = state,
                    windowLayout =
                        AdaptiveStageWindowLayout(widthDp = 800, heightDp = 800, posture = AdaptiveStagePosture.UNFOLDED),
                    onAction = actions::add,
                )
            }
        }

        // Rail tiles are no longer their own clickable Surface -- the CardStack-supplied modifier
        // (raw pointer input, not a semantics onClick action) drives tap-to-select now, so this
        // must synthesize a real touch rather than use performClick().
        composeRule.onNodeWithContentDescription("Calendar. Open stage").performTouchInput { click() }

        assertEquals(
            listOf(
                LauncherShellAction.SelectAppStage(
                    AppStageId(second.identity.packageName, second.identity.profile.id),
                ),
            ),
            actions,
        )
    }

    @Test
    fun detailActionsRouteEverySupportedActionToTheFocusedNotificationKey() {
        val app = adaptiveStageTestApp()
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
                    AdaptiveStageCardDetailState(
                        currentExpansion = { expansion },
                        updateExpansion = { expansion = it },
                        currentRecoveryMessage = { null },
                        updateRecoveryMessage = { _ -> },
                        motion = AdaptiveStageMotion(reducedMotion = true),
                        globalReducedMotion = false,
                    )
                }
            MaterialTheme {
                AdaptiveStageCardDetailSurface(card = card, detailState = detailState, onAction = actions::add)
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
        val app = adaptiveStageTestApp()
        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageAppStageSurface(
                    state = adaptiveStageTestState(app, adaptiveStageTestNotification(app)),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("Details").assertIsNotFocused()
    }

    @Test
    fun emptyAppDetailsBackRestoresFocusToItsDetailsControl() {
        val app = adaptiveStageTestApp()
        composeRule.setContent {
            MaterialTheme { AdaptiveStageAppStageSurface(state = emptyPinnedStageState(app), onAction = {}) }
        }

        composeRule.onNodeWithText("Details").performClick()

        composeRule.onNodeWithText("App details").assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.mainClock.advanceTimeBy(200)

        composeRule.onNodeWithText("Details").assertIsFocused()
    }

    @Test
    fun emptyAppDetailMovesToSupportingPaneAndBackAcrossResize() {
        val app = adaptiveStageTestApp()
        var widthDp by mutableIntStateOf(500)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(0.3f)) {
                MaterialTheme {
                    Box(modifier = Modifier.width(widthDp.dp).height(800.dp).clipToBounds()) {
                        AdaptiveStageAppStageSurface(
                            state = emptyPinnedStageState(app),
                            windowLayout = AdaptiveStageWindowLayout(widthDp, 800, posture = AdaptiveStagePosture.UNFOLDED),
                            onAction = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Details").performClick()
        composeRule.onNodeWithText("App details").assertIsDisplayed()

        composeRule.runOnIdle { widthDp = 1_200 }
        composeRule.onNodeWithTag(ADAPTIVE_STAGE_SUPPORTING_PANE_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("App details").assertExists()

        composeRule.runOnIdle { widthDp = 500 }
        composeRule.onNodeWithText("App details").assertIsDisplayed()
    }

    @Test
    fun removingSourceDuringBackCloseDoesNotFocusRemainingCardDetails() {
        val app = adaptiveStageTestApp()
        val source = adaptiveStageTestNotification(app).copy(key = LauncherNotificationKey("source"), postedAtEpochMillis = 20)
        val remaining =
            adaptiveStageTestNotification(app).copy(
                key = LauncherNotificationKey("remaining"),
                title = "Remaining notification",
                postedAtEpochMillis = 10,
            )
        var state by
            mutableStateOf(
                adaptiveStageTestState(app, source).copy(
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
            MaterialTheme { AdaptiveStageAppStageSurface(state = state, onAction = {}) }
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
                AdaptiveStageAppStageSurface(
                    state = emptyPinnedStageState(adaptiveStageTestApp()),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("Details").assertIsNotFocused()
    }

    @Test
    fun removedExpandedEmptyAppRecoversWithoutFocusingDetachedDetailsControl() {
        val app = adaptiveStageTestApp()
        var state by mutableStateOf(emptyPinnedStageState(app))
        composeRule.setContent {
            MaterialTheme { AdaptiveStageAppStageSurface(state = state, onAction = {}) }
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
        val app = adaptiveStageTestApp()
        val notification = adaptiveStageTestNotification(app)
        var state by mutableStateOf(adaptiveStageTestState(app, notification))
        composeRule.setContent {
            MaterialTheme { AdaptiveStageAppStageSurface(state = state, onAction = {}) }
        }

        composeRule.onNodeWithText("Details").performClick()
        composeRule.onNodeWithText("Notification details").assertIsDisplayed()
        composeRule.runOnIdle { state = state.copy(notificationGroupsByApp = emptyList()) }

        composeRule.onNodeWithText("The selected card is no longer available.").assertIsDisplayed()
        composeRule.onAllNodesWithText("Notification details").assertCountEquals(0)
    }

    @Test
    fun revokingNotificationAccessWhileDetailIsOpenClosesItWithAnExplanation() {
        val app = adaptiveStageTestApp()
        val notification = adaptiveStageTestNotification(app)
        var state by mutableStateOf(adaptiveStageTestState(app, notification))
        composeRule.setContent {
            MaterialTheme { AdaptiveStageAppStageSurface(state = state, onAction = {}) }
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
                text = "Hello from Cards",
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
                AdaptiveStageAppStageSurface(state = state, onAction = {})
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
                AdaptiveStageAppStageSurface(
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
                    SemanticsProperties.StateDescription in node.config &&
                        node.config[SemanticsProperties.StateDescription].contains("Pinned")
                }.and(hasContentDescription("Cards stage: Mail")),
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
                text = "Hello from Cards",
                postedAtEpochMillis = 10,
            )
        val actions = mutableListOf<LauncherShellAction>()
        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageAppStageSurface(
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
        composeRule.onNodeWithTag(RIFFLE_CONTEXT_MENU_TEST_TAG).assertIsDisplayed()
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
                AdaptiveStageAppStageSurface(
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
    fun tappingASpineItemDispatchesSelectAppStageForThatStage() {
        val first = adaptiveStageTestApp()
        val second =
            first.copy(
                identity = first.identity.copy(packageName = AppPackageName("com.example.calendar")),
                label = "Calendar",
            )
        val firstStageId = AppStageId(first.identity.packageName, first.identity.profile.id)
        val secondStageId = AppStageId(second.identity.packageName, second.identity.profile.id)
        val actions = mutableListOf<LauncherShellAction>()

        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageAppStageSurface(
                    state =
                        LauncherShellState(
                            notificationAccessStatus = NotificationAccessStatus.GRANTED,
                            installedApps = listOf(first, second),
                            launcherSettings =
                                LauncherSettings(
                                    cards =
                                        CardsSettings(
                                            stagePreferencesByLayout =
                                                mapOf(
                                                    HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER) to
                                                        AppStagePreferences(
                                                            pinnedStageIds = listOf(firstStageId, secondStageId),
                                                            selectedStageId = firstStageId,
                                                        ),
                                                ),
                                        ),
                                ),
                        ),
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithContentDescription("Calendar. Open stage").performClick()

        assertEquals(LauncherShellAction.SelectAppStage(secondStageId), actions.single())
    }

    @Test
    fun stageSelectorUsesStableSaveableKeysForProfileScopedStages() {
        val personal = AppStageId(AppPackageName("com.example.mail"), AppProfile.personal().id)
        val work = AppStageId(AppPackageName("com.example.mail"), AppProfile.work().id)

        assertEquals(
            "personal:com.example.mail",
            adaptiveStageStageSelectorItemKey(
                AppStage(personal, setOf(AppStageOrigin.DYNAMIC), AppStageLifecycle.EMPTY),
            ),
        )
        assertEquals(
            "work:com.example.mail",
            adaptiveStageStageSelectorItemKey(
                AppStage(work, setOf(AppStageOrigin.DYNAMIC), AppStageLifecycle.EMPTY),
            ),
        )
    }

    @Test
    fun everyBackgroundSourceRendersCardContentWithAFallback() {
        val appearances =
            AdaptiveStageBackgroundSource.entries.map { source ->
                AdaptiveStageAppearanceSettings(surface = AdaptiveStageSurface(backgroundSource = source))
            }

        composeRule.setContent {
            MaterialTheme {
                appearances.forEachIndexed { index, appearance ->
                    AdaptiveStageCardSurface(
                        appearance = appearance,
                        background = AdaptiveStageCardBackground(appSeed = "card-$index"),
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
            resolveAdaptiveStageCardColors(
                appearance =
                    AdaptiveStageAppearanceSettings(
                        surface =
                            AdaptiveStageSurface(
                                customBackgroundArgb = 0xFF101010L,
                                backgroundSource = AdaptiveStageBackgroundSource.CUSTOM_SOLID,
                            ),
                        motion = AdaptiveStageMotion(reducedTransparency = true),
                    ),
                background = AdaptiveStageCardBackground(),
                materialBackground = Color.White,
                materialAccent = Color.Blue,
            )

        assertEquals(1f, colors.glass.alpha)
        assertEquals(1f, colors.glassTint.alpha)
        assertTrue(contrastRatio(colors.foreground, colors.glass) >= 4.5f)
    }

    @Test
    fun appColorWinsOverTheSeedHashFallback() {
        val appColor = Color(0.2f, 0.4f, 0.9f)
        val backgroundSources =
            listOf(
                AdaptiveStageBackgroundSource.APP_DERIVED_SOLID,
                AdaptiveStageBackgroundSource.APP_DERIVED_GRADIENT,
                AdaptiveStageBackgroundSource.NOTIFICATION_ARTWORK,
                AdaptiveStageBackgroundSource.APP_ICON_TREATMENT,
            )

        backgroundSources.forEach { source ->
            val colorsWithAppColor =
                resolveAdaptiveStageCardColors(
                    appearance = AdaptiveStageAppearanceSettings(surface = AdaptiveStageSurface(backgroundSource = source)),
                    background = AdaptiveStageCardBackground(appSeed = "com.example.app", appColor = appColor),
                    materialBackground = Color.White,
                    materialAccent = Color.Blue,
                )
            val colorsWithoutAppColor =
                resolveAdaptiveStageCardColors(
                    appearance = AdaptiveStageAppearanceSettings(surface = AdaptiveStageSurface(backgroundSource = source)),
                    background = AdaptiveStageCardBackground(appSeed = "com.example.app"),
                    materialBackground = Color.White,
                    materialAccent = Color.Blue,
                )

            assertNotEquals(colorsWithAppColor.background, colorsWithoutAppColor.background)
        }
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
                resolveAdaptiveStageCardColors(
                    appearance =
                        AdaptiveStageAppearanceSettings(
                            surface =
                                AdaptiveStageSurface(
                                    backgroundSource = AdaptiveStageBackgroundSource.CUSTOM_SOLID,
                                    customBackgroundArgb = backgroundArgb,
                                    glassTintArgb = backgroundArgb,
                                    glassTransparencyPercent = 0,
                                ),
                            typography =
                                AdaptiveStageTypography(
                                    automaticForegroundContrast = automaticForegroundContrast,
                                ),
                        ),
                    background = AdaptiveStageCardBackground(),
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
            AdaptiveStageAppearanceSettings(
                surface =
                    AdaptiveStageSurface(
                        backgroundSource = AdaptiveStageBackgroundSource.NOTIFICATION_ARTWORK,
                        glassTintArgb = 0xFFFFFFFFL,
                        glassTransparencyPercent = 50,
                        blurStrengthPercent = 0,
                    ),
            )

        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageCardSurface(
                    appearance = appearance,
                    background = AdaptiveStageCardBackground(artwork = artwork),
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
            AdaptiveStageAppearanceSettings(
                surface =
                    AdaptiveStageSurface(
                        backgroundSource = AdaptiveStageBackgroundSource.NOTIFICATION_ARTWORK,
                        glassTintArgb = 0xFFFFFFFFL,
                        glassTransparencyPercent = 95,
                        blurStrengthPercent = 0,
                    ),
            )

        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageCardSurface(
                    appearance = appearance,
                    background = AdaptiveStageCardBackground(artwork = artwork),
                    modifier = Modifier.requiredSize(160.dp).testTag("mixed-artwork-card"),
                ) {}
            }
        }

        val rendered = composeRule.onNodeWithTag("mixed-artwork-card").captureToImage()
        val pixels = rendered.toPixelMap()
        val left = pixels[rendered.width / 4, rendered.height / 2]
        val right = pixels[rendered.width * 3 / 4, rendered.height / 2]
        val colors =
            resolveAdaptiveStageCardColors(
                appearance = appearance,
                background = AdaptiveStageCardBackground(artwork = artwork),
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

        val desaturated = adaptiveStageAdjustedColor(original, saturationPercent = 0, contrastPercent = 100)
        val contrasted = adaptiveStageAdjustedColor(original, saturationPercent = 100, contrastPercent = 150)

        assertEquals(desaturated.red, desaturated.green, 0.001f)
        assertEquals(desaturated.green, desaturated.blue, 0.001f)
        assertNotEquals(original, contrasted)
    }

    @Test
    fun typographyProjectsAccentTextScaleAndContentDensity() {
        var observedAction = Color.Unspecified
        var observedFontScale = 0f
        val appearance =
            AdaptiveStageAppearanceSettings(
                typography =
                    AdaptiveStageTypography(
                        accentSource = AdaptiveStageAccentSource.CUSTOM,
                        customAccentArgb = 0xFF336699L,
                        contentDensity = AdaptiveStageContentDensity.EXPANDED,
                        textScalePercent = 130,
                    ),
            )

        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageCardSurface(appearance, AdaptiveStageCardBackground()) {
                    observedAction = MaterialTheme.colorScheme.primary
                    observedFontScale = LocalDensity.current.fontScale
                    Text("Styled card")
                }
            }
        }

        composeRule.onNodeWithText("Styled card").assertIsDisplayed()
        composeRule.runOnIdle {
            val colors =
                resolveAdaptiveStageCardColors(
                    appearance = appearance,
                    background = AdaptiveStageCardBackground(),
                    materialBackground = Color.Black,
                    materialAccent = Color.Blue,
                )
            assertEquals(Color(0xFF336699), colors.accent)
            assertTrue(contrastRatio(observedAction, colors.glass) >= 4.5f)
            assertEquals(1.3f, observedFontScale, 0.001f)
            assertEquals(1.2f, adaptiveStageContentDensityScale(AdaptiveStageContentDensity.EXPANDED), 0.001f)
            assertEquals(0.8f, adaptiveStageContentDensityScale(AdaptiveStageContentDensity.COMPACT), 0.001f)
        }
    }

    @Test
    fun cardActionsRemainLegibleForLowContrastCustomAccents() {
        val actionColors = mutableMapOf<Int, Pair<Color, Color>>()
        val appearances =
            listOf(
                AdaptiveStageAppearanceSettings(
                    surface =
                        AdaptiveStageSurface(
                            backgroundSource = AdaptiveStageBackgroundSource.CUSTOM_SOLID,
                            customBackgroundArgb = 0xFFFFFFFFL,
                            glassTintArgb = 0xFFFFFFFFL,
                            glassTransparencyPercent = 0,
                        ),
                    typography =
                        AdaptiveStageTypography(
                            accentSource = AdaptiveStageAccentSource.CUSTOM,
                            customAccentArgb = 0xFFFFFFFFL,
                        ),
                ),
                AdaptiveStageAppearanceSettings(
                    surface =
                        AdaptiveStageSurface(
                            backgroundSource = AdaptiveStageBackgroundSource.CUSTOM_SOLID,
                            customBackgroundArgb = 0xFF000000L,
                            glassTintArgb = 0xFF000000L,
                            glassTransparencyPercent = 0,
                        ),
                    typography =
                        AdaptiveStageTypography(
                            accentSource = AdaptiveStageAccentSource.CUSTOM,
                            customAccentArgb = 0xFF000000L,
                        ),
                ),
            )

        composeRule.setContent {
            MaterialTheme {
                appearances.forEachIndexed { index, appearance ->
                    AdaptiveStageCardSurface(appearance, AdaptiveStageCardBackground()) {
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
                    resolveAdaptiveStageCardColors(
                        appearance = appearances[index],
                        background = AdaptiveStageCardBackground(),
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
        val appearance = AdaptiveStageAppearanceSettings(surface = AdaptiveStageSurface(blurStrengthPercent = 72))

        assertFalse(adaptiveStageRendererCapabilities(sdkInt = 30).supportsBlur)
        assertEquals(72, appearance.surface.blurStrengthPercent)
        assertEquals(0, appearance.effectiveFor(adaptiveStageRendererCapabilities(sdkInt = 30)).surface.blurStrengthPercent)
    }

    @Test
    fun corruptOrOversizedArtworkFallsBackAndDecodingIsBounded() {
        assertNull(decodeAdaptiveStageArtwork("not-base64"))
        assertNull(decodeAdaptiveStageArtwork("a".repeat(2_800_001)))
        assertEquals(2, adaptiveStageArtworkSampleSize(width = 1_024, height = 600))
        assertEquals(4, adaptiveStageArtworkSampleSize(width = 3_000, height = 900))
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
            AdaptiveStageAppearanceSettings(
                geometry = AdaptiveStageGeometry(contentPaddingDp = 64),
            ).resolveCardStack(
                viewport = AdaptiveStageViewportDp(widthDp = 500, heightDp = 475),
            )

        assertTrue(resolution.isUsable)
        assertEquals(
            resolution.contentPaddingDp.dp,
            adaptiveStageResolvedContentPadding(resolution),
        )
        assertTrue(resolution.contentPaddingDp < 64)
    }

    @Test
    fun resolvedStackRetainsFocusedCardAsHighestOrderEntryWithoutMotion() {
        val appearance = AdaptiveStageAppearanceSettings(motion = AdaptiveStageMotion(reducedMotion = true))
        val entries =
            appearance
                .resolveCardStack(
                    viewport = com.riffle.core.domain.launcher.settings.AdaptiveStageViewportDp(800, 1_200),
                    globalReducedMotion = true,
                ).layoutPolicy
                .entries(cardCount = 3, activeIndex = 1, reducedMotion = true)

        assertEquals(1, entries.maxBy { entry -> entry.order }.cardIndex)
        assertTrue(entries.all { entry -> entry.rotationDegrees == 0f })
    }

    @Test
    fun notificationStackKeepsEveryAvailableCardReachableBeyondConfiguredVisualDepth() {
        val resolution =
            AdaptiveStageAppearanceSettings().resolveCardStack(
                viewport = AdaptiveStageViewportDp(widthDp = 800, heightDp = 1_200),
            )

        val entries =
            adaptiveStageNotificationStackEntries(
                resolution = resolution,
                cardCount = 11,
                activeCardIndex = 5,
            )

        assertEquals(11, entries.size)
        assertEquals((0..10).toSet(), entries.map { entry -> entry.cardIndex }.toSet())
    }

    private fun adaptiveStageTestApp(): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.example.mail"),
                    activityName = AppActivityName(".Main"),
                    profile = AppProfile.personal(),
                ),
            label = "Mail",
        )

    private fun adaptiveStageTestNotification(app: InstalledApp): LauncherNotification =
        LauncherNotification(
            key = LauncherNotificationKey("mail"),
            packageName = app.identity.packageName,
            profileId = app.identity.profile.id,
            title = "New message",
            text = "Hello from Cards",
            postedAtEpochMillis = 10,
        )

    private fun notificationGroup(
        app: InstalledApp,
        notification: LauncherNotification,
    ): AppNotificationGroup =
        AppNotificationGroup(
            packageName = app.identity.packageName,
            profileId = app.identity.profile.id,
            latestCategory = NotificationCategory.MESSAGE,
            latestAgeBucket = NotificationAgeBucket.RECENT,
            notifications = listOf(notification),
        )

    private fun adaptiveStageTestState(
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
