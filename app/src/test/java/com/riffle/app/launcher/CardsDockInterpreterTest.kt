package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.AdaptiveStageInteractionContext
import com.riffle.core.domain.launcher.cards.AppStage
import com.riffle.core.domain.launcher.cards.AppStageContent
import com.riffle.core.domain.launcher.cards.AppStageContentKind
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.cards.AppStageLifecycle
import com.riffle.core.domain.launcher.cards.AppStageOrigin
import com.riffle.core.domain.launcher.cards.CardsStageSelection
import com.riffle.core.domain.launcher.cards.LauncherCardId
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Cards' side of the dock boundary (#1212): the dock hands over neutral intents and this decides
 * what they mean. A pinned icon that is also a stage navigates to it on a tap (#XXXX);
 * a pinned icon with no stage still opens the app, same as any other mode.
 */
class CardsDockInterpreterTest {
    private val mailContent =
        listOf(AppStageContent(LauncherCardId("mail-1"), mailStageId, AppStageContentKind.NOTIFICATION, 1L))
    private val stages =
        listOf(
            AppStage(notesStageId, setOf(AppStageOrigin.PINNED), AppStageLifecycle.EMPTY),
            AppStage(mailStageId, setOf(AppStageOrigin.DYNAMIC), AppStageLifecycle.ACTIVE, mailContent),
        )

    @Test
    fun selectingAStageFromAnyControlLeavesAllButOtherActionsPassStraightThrough() {
        val contexts = mutableListOf<AdaptiveStageInteractionContext>()
        val actions = mutableListOf<LauncherShellAction>()
        val onAll = AdaptiveStageInteractionContext(allNotificationsSelected = true)

        interpretCardsAction(LauncherShellAction.OpenSettings, onAll, contexts::add, actions::add)
        interpretCardsAction(LauncherShellAction.SelectAppStage(mailStageId), onAll, contexts::add, actions::add)

        assertEquals(listOf(AdaptiveStageInteractionContext(allNotificationsSelected = false)), contexts)
        assertEquals(
            listOf(LauncherShellAction.OpenSettings, LauncherShellAction.SelectAppStage(mailStageId)),
            actions,
        )
    }

    @Test
    fun aTapOnAPinnedIconThatIsAlsoAStageNavigatesToIt() {
        assertEquals(
            LauncherShellAction.SelectAppStage(notesStageId),
            cardsStaticItemTapOverride(notesApp.identity, stages),
        )
    }

    @Test
    fun aTapOnAPinnedIconWithNoStageFallsBackToLaunching() {
        assertEquals(null, cardsStaticItemTapOverride(cameraApp.identity, stages))
    }

    @Test
    fun aPinnedDockAppOffersItsStageOnLongPress() {
        val dock =
            DockModel(
                capacity = 3,
                items = listOf(shortcut(mailApp), shortcut(notesApp), shortcut(cameraApp)),
            )

        val extras = cardsDockItemMenuExtras(dock, stages)

        assertEquals(
            listOf(
                ShortcutContextMenuItem("Show stage", LauncherShellAction.SelectAppStage(mailStageId)),
                ShortcutContextMenuItem("Pin stage", LauncherShellAction.ToggleAppStagePinned(mailStageId)),
            ),
            extras.forApp(mailApp.identity),
        )
        assertEquals(
            listOf(ShortcutContextMenuItem("Show stage", LauncherShellAction.SelectAppStage(notesStageId))),
            extras.forApp(notesApp.identity),
        )
        val cameraStageId = AppStageId(cameraApp.identity.packageName, cameraApp.identity.profile.id)
        assertEquals(
            listOf(ShortcutContextMenuItem("Pin stage", LauncherShellAction.ToggleAppStagePinned(cameraStageId))),
            extras.forApp(cameraApp.identity),
        )
    }

    @Test
    fun theSelectionFollowsTheContextBeforeTheSelectedStage() {
        assertEquals(
            CardsStageSelection.All,
            cardsStageSelection(AdaptiveStageInteractionContext(allNotificationsSelected = true), mailStageId),
        )
        assertEquals(
            CardsStageSelection.Stage(mailStageId),
            cardsStageSelection(AdaptiveStageInteractionContext(), mailStageId),
        )
        assertEquals(CardsStageSelection.None, cardsStageSelection(AdaptiveStageInteractionContext(), null))
    }

    private fun shortcut(app: InstalledApp) =
        AppShortcutItem(
            id = LauncherItemId(app.identity.packageName.value),
            appIdentity = app.identity,
            label = app.label,
        )

    private companion object {
        private val mailApp = app("mail", "Mail")
        private val notesApp = app("notes", "Notes")
        private val cameraApp = app("camera", "Camera")
        private val mailStageId = AppStageId(mailApp.identity.packageName, mailApp.identity.profile.id)
        private val notesStageId = AppStageId(notesApp.identity.packageName, notesApp.identity.profile.id)

        private fun app(
            name: String,
            label: String,
        ) = InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.$name"),
                    activityName = AppActivityName(".MainActivity"),
                    profile = AppProfile.personal(),
                ),
            label = label,
        )
    }
}
