package com.riffle.app.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.AdaptiveStageInteractionContext
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.cards.AppStagePreferences
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.settings.CardsSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #1212: in Cards the dock's dynamic section is the stage selector, and every destination on it --
 * "All", a stage with notifications, and a pinned stage with none -- is reachable by touch and by a
 * screen reader's click action. The spine is off by default, so this is the only on-screen selector
 * a compact window has.
 */
@RunWith(AndroidJUnit4::class)
class CardsStageSelectorReachabilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everyStageIncludingAPinnedEmptyOneIsReachableByTouch() {
        val actions = mutableListOf<LauncherShellAction>()
        setContent(actions)

        tile(NOTES_LABEL).performScrollTo().performClick()
        tile(MAIL_LABEL).performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf<LauncherShellAction>(
                    LauncherShellAction.SelectAppStage(notesStageId),
                    LauncherShellAction.SelectAppStage(mailStageId),
                ),
                actions.filterIsInstance<LauncherShellAction.SelectAppStage>(),
            )
        }
    }

    @Test
    fun allIsAlwaysOfferedAndSwitchesToTheMergedView() {
        var context by mutableStateOf(AdaptiveStageInteractionContext())
        setContent(mutableListOf(), onContextChanged = { next -> context = next }, context = { context })

        tile(CARDS_ALL_ENTRY_LABEL).performScrollTo().performClick()

        composeRule.runOnIdle { assertTrue(context.allNotificationsSelected) }
        composeRule.onNodeWithContentDescription("Cards stage: $CARDS_ALL_ENTRY_LABEL").assertIsDisplayed()
    }

    @Test
    fun everyEntryIsReachableByAScreenReader() {
        val actions = mutableListOf<LauncherShellAction>()
        setContent(actions)

        listOf(CARDS_ALL_ENTRY_LABEL, NOTES_LABEL, MAIL_LABEL).forEach { label ->
            tile(label)
                .assert(hasClickAction())
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
        }
        tile(NOTES_LABEL).performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertEquals(
                LauncherShellAction.SelectAppStage(notesStageId),
                actions.filterIsInstance<LauncherShellAction.SelectAppStage>().single(),
            )
        }
    }

    @Test
    fun thePinnedEmptyStageTellsAScreenReaderWhyItIsQuiet() {
        setContent(mutableListOf())

        composeRule.onNodeWithContentDescription("Notes, pinned, nothing new, Open stage").assertExists()
    }

    @Test
    fun theSpineIsOffByDefaultBecauseTheDockIsTheSelector() {
        setContent(mutableListOf())

        composeRule.onNodeWithContentDescription("$NOTES_LABEL. Open stage").assertDoesNotExist()
    }

    private fun tile(label: String) = composeRule.onNodeWithTag(dockDynamicSectionTileTestTag(label))

    private fun setContent(
        actions: MutableList<LauncherShellAction>,
        onContextChanged: (AdaptiveStageInteractionContext) -> Unit = {},
        context: () -> AdaptiveStageInteractionContext = { AdaptiveStageInteractionContext() },
    ) {
        val state = cardsState()
        composeRule.setContent {
            HomeDestination(
                state = state,
                appIconLoader = EmptyAppIconLoader,
                adaptiveStageContext = context(),
                onAdaptiveStageContextChanged = onContextChanged,
                onAction = actions::add,
            )
        }
    }

    private fun cardsState(): LauncherShellState {
        val base = HomeLayoutDefaults.standard().copy(viewMode = LauncherViewMode.CARD_INTERFACE)
        val layout =
            base.copy(
                dock =
                    base.dock.copy(
                        items =
                            listOf(
                                AppShortcutItem(
                                    id = LauncherItemId("camera"),
                                    appIdentity = camera.identity,
                                    label = camera.label,
                                ),
                            ),
                    ),
            )
        val layoutSet = HomeLayoutSet.fromLayout(layout)
        return LauncherShellState(
            firstRunStatus = FirstRunStatus.COMPLETE,
            homeRoleStatus = HomeRoleStatus.DEFAULT_HOME,
            homeLayout = layout,
            homeLayoutSet = layoutSet,
            notificationAccessStatus = NotificationAccessStatus.GRANTED,
            installedApps = listOf(camera, mail, notes),
            profileContentVisibility = mapOf(AppProfile.personal().id to AppProfileContentVisibility.VISIBLE),
            notificationGroupsByApp =
                listOf(
                    AppNotificationGroup(
                        packageName = mail.identity.packageName,
                        profileId = mail.identity.profile.id,
                        latestCategory = NotificationCategory.MESSAGE,
                        latestAgeBucket = NotificationAgeBucket.RECENT,
                        notifications =
                            listOf(
                                LauncherNotification(
                                    key = LauncherNotificationKey("mail"),
                                    packageName = mail.identity.packageName,
                                    profileId = mail.identity.profile.id,
                                    title = "New message",
                                    text = "Hello",
                                    postedAtEpochMillis = 10,
                                ),
                            ),
                    ),
                ),
            launcherSettings =
                LauncherSettings(
                    cards =
                        CardsSettings(
                            stagePreferencesByLayout =
                                mapOf(
                                    layoutSet.activeKey to
                                        AppStagePreferences(
                                            pinnedStageIds = listOf(notesStageId),
                                            selectedStageId = mailStageId,
                                        ),
                                ),
                        ),
                ),
        )
    }

    private companion object {
        private const val MAIL_LABEL = "Mail"
        private const val NOTES_LABEL = "Notes"

        private val camera = app("camera", "Camera")
        private val mail = app("mail", MAIL_LABEL)
        private val notes = app("notes", NOTES_LABEL)
        private val mailStageId = AppStageId(mail.identity.packageName, mail.identity.profile.id)
        private val notesStageId = AppStageId(notes.identity.packageName, notes.identity.profile.id)

        private fun app(
            name: String,
            label: String,
        ) = InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.example.$name"),
                    activityName = AppActivityName(".Main"),
                    profile = AppProfile.personal(),
                ),
            label = label,
        )
    }
}
