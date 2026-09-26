package com.riffle.app.launcher

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #1212 (revised #XXXX): in Cards the dock's dynamic section is the stage selector, but only for a
 * stage with something new that is not already pinned -- a pinned stage (Notes here) renders as an
 * ordinary static dock icon instead (see CardsDockPinning.kt), reachable and behaving exactly like
 * any other pinned app until #XXXX (tap-to-navigate for a pinned stage) changes what its tap does.
 * "All" has no entry point on the dock at all pending a dedicated gesture. The spine is off by
 * default, so the dynamic section is the only on-screen selector a compact window has for stages
 * that are not pinned.
 */
@RunWith(AndroidJUnit4::class)
class CardsStageSelectorReachabilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun anUnpinnedStageWithSomethingNewIsReachableByTouch() {
        val actions = mutableListOf<LauncherShellAction>()
        setContent(actions)

        tile(MAIL_LABEL).performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(LauncherShellAction.SelectAppStage(mailStageId)),
                actions.filterIsInstance<LauncherShellAction.SelectAppStage>(),
            )
        }
    }

    @Test
    fun aPinnedStageIsNotOnTheDynamicSectionAtAll() {
        setContent(mutableListOf())

        tile(NOTES_LABEL).assertDoesNotExist()
    }

    @Test
    fun allHasNoDockEntryPendingItsOwnGesture() {
        setContent(mutableListOf())

        composeRule.onNodeWithContentDescription("Cards stage: $CARDS_ALL_ENTRY_LABEL").assertDoesNotExist()
    }

    @Test
    fun theDynamicEntryIsReachableByAScreenReader() {
        val actions = mutableListOf<LauncherShellAction>()
        setContent(actions)

        tile(MAIL_LABEL)
            .assert(hasClickAction())
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertEquals(
                LauncherShellAction.SelectAppStage(mailStageId),
                actions.filterIsInstance<LauncherShellAction.SelectAppStage>().single(),
            )
        }
    }

    @Test
    fun theSpineIsOffByDefaultBecauseTheDockIsTheSelector() {
        setContent(mutableListOf())

        composeRule.onNodeWithContentDescription("$MAIL_LABEL. Open stage").assertDoesNotExist()
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
