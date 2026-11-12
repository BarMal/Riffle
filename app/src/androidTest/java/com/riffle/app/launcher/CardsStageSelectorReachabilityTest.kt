package com.riffle.app.launcher

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
 * P5 (ADR 0003, The Dock is the handle): Cards no longer lists its stages on the dock. The dock's dynamic side is
 * live apps, now playing and the Stream button in every mode, and it opens the Stream, not a Cards stage; a stage
 * is selected from the stage pager and the spine (off by default) instead. These cover what is left of the old
 * stage selector: no stage entry on the dock, pinned or not, no "All" entry, and no spine by default. The dynamic
 * side itself (its entries, the touch and screen-reader reach of the Stream button) is covered by
 * DockDynamicSideEntriesTest and StreamHomeDestinationTest on the JVM.
 */
@RunWith(AndroidJUnit4::class)
class CardsStageSelectorReachabilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun anUnpinnedStageWithSomethingNewHasNoDockEntryEither() {
        val actions = mutableListOf<LauncherShellAction>()
        setContent(actions)

        tile(MAIL_LABEL).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(
                emptyList<LauncherShellAction>(),
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
