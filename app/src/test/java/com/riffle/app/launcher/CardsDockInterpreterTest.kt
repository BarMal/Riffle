package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.AdaptiveStageInteractionContext
import com.riffle.core.domain.launcher.cards.AppStage
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.cards.AppStageLifecycle
import com.riffle.core.domain.launcher.cards.AppStageOrigin
import com.riffle.core.domain.launcher.cards.CardsStageSelection
import com.riffle.core.domain.launcher.cards.CardsStageSelector
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cards' side of the dock boundary (#1212): the dock hands over neutral intents and this decides
 * what they mean -- the dynamic section is the stage selector, "All" first, and a pinned icon's
 * stage is one long-press away while a tap opens the app.
 */
class CardsDockInterpreterTest {
    private val state = LauncherShellState(installedApps = listOf(mailApp, notesApp))
    private val stages =
        listOf(
            AppStage(notesStageId, setOf(AppStageOrigin.PINNED), AppStageLifecycle.EMPTY),
            AppStage(mailStageId, setOf(AppStageOrigin.DYNAMIC), AppStageLifecycle.EMPTY),
        )

    @Test
    fun everySelectorEntryDelegatesBackSoTheDockNeverLearnsWhatAStageIs() {
        val entries =
            cardsStageSelectorDockEntries(CardsStageSelector.entries(stages, CardsStageSelection.None), state)

        assertEquals(listOf(CARDS_ALL_ENTRY_LABEL, "Notes", "Mail"), entries.map(DockDynamicEntry::label))
        assertTrue(entries.all { entry -> entry.intent == DockDynamicEntryIntent.Delegate })
    }

    @Test
    fun aPinnedEmptyStageIsListedAndSaysWhyItIsQuiet() {
        val entries =
            cardsStageSelectorDockEntries(CardsStageSelector.entries(stages, CardsStageSelection.None), state)

        val notes = entries.single { entry -> entry.label == "Notes" }
        assertEquals(notesApp.identity, notes.identity)
        assertEquals("Notes, pinned, nothing new, Open stage", notes.contentDescription)
    }

    @Test
    fun theShowingEntryIsMarkedSelectedAndSaysSo() {
        val entries =
            cardsStageSelectorDockEntries(CardsStageSelector.entries(stages, CardsStageSelection.All), state)

        assertTrue(entries.first().isSelected)
        assertTrue(entries.first().contentDescription.endsWith("Showing. Open stage"))
        assertFalse(entries.drop(1).any(DockDynamicEntry::isSelected))
    }

    @Test
    fun activatingAllSwitchesTheSurfaceToTheMergedViewWithoutAnAction() {
        val contexts = mutableListOf<AdaptiveStageInteractionContext>()
        val actions = mutableListOf<LauncherShellAction>()

        interpretCardsDockEntry(
            key = CardsStageSelector.ALL_ENTRY_KEY,
            stages = stages,
            context = AdaptiveStageInteractionContext(),
            onContextChanged = contexts::add,
            onAction = actions::add,
        )

        assertEquals(listOf(AdaptiveStageInteractionContext(allNotificationsSelected = true)), contexts)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun activatingAStageLeavesAllAndSelectsIt() {
        val contexts = mutableListOf<AdaptiveStageInteractionContext>()
        val actions = mutableListOf<LauncherShellAction>()

        interpretCardsDockEntry(
            key = CardsStageSelector.stageEntryKey(notesStageId),
            stages = stages,
            context = AdaptiveStageInteractionContext(allNotificationsSelected = true),
            onContextChanged = contexts::add,
            onAction = actions::add,
        )

        assertEquals(listOf(AdaptiveStageInteractionContext(allNotificationsSelected = false)), contexts)
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.SelectAppStage(notesStageId)), actions)
    }

    @Test
    fun aStaleKeyDoesNothing() {
        val contexts = mutableListOf<AdaptiveStageInteractionContext>()
        val actions = mutableListOf<LauncherShellAction>()

        interpretCardsDockEntry(
            key = "notifications:gone",
            stages = stages,
            context = AdaptiveStageInteractionContext(),
            onContextChanged = contexts::add,
            onAction = actions::add,
        )

        assertTrue(contexts.isEmpty())
        assertTrue(actions.isEmpty())
    }

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
