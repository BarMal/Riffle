package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.riffle.app.launcher.notifications.AppStageShellState
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.cards.AdaptiveStageInteractionContext
import com.riffle.core.domain.launcher.cards.AppStage
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.cards.CardsDockItemStageAction
import com.riffle.core.domain.launcher.cards.CardsStageSelection
import com.riffle.core.domain.launcher.cards.CardsStageSelector
import com.riffle.core.domain.launcher.cards.toAppStageId
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition

/*
 * Cards' side of the dock boundary (Decision 2 in docs/product/modes-dock-handle-and-cards-plan.md;
 * Decision 5's "static dock icons launch their app" is overridden here for a pinned stage -- #XXXX).
 *
 * The dock is mode-agnostic: it renders neutral entries and reports neutral intents -- a pinned app
 * tapped, a long-press menu item chosen (the dynamic side is the Stream's, not any mode's). Every piece of Cards
 * meaning -- that a pinned app which is also a stage navigates to it instead of launching, that the
 * pinned stage is reached from the dock -- is decided here, in Cards code, and
 * never in the dock.
 *
 * It all reaches the dock through one value, the [HomeDockInterpreter] [rememberCardsDockInterpreter]
 * builds: the same host contract grid modes fill with its defaults.
 */

/**
 * Cards' [HomeDockInterpreter]: a pinned icon that is also a stage
 * navigates to it on a tap instead of launching the app (a pinned icon with no stage still launches,
 * same as any other mode), with "Show stage" / "Pin stage" still on its long-press menu; and the
 * expanded shelf drops its notification row, because the stages already are the notifications.
 */
@Composable
internal fun rememberCardsDockInterpreter(
    state: LauncherShellState,
    shellState: AppStageShellState,
    adaptiveStageContext: AdaptiveStageInteractionContext,
    onAdaptiveStageContextChanged: (AdaptiveStageInteractionContext) -> Unit,
    appIconLoader: AppIconLoader,
    dockPosition: DockPosition,
    onAction: (LauncherShellAction) -> Unit,
): HomeDockInterpreter {
    val stages = shellState.snapshot.stages
    val dockItemMenuExtras =
        remember(state.homeLayout.dock, stages) { cardsDockItemMenuExtras(state.homeLayout.dock, stages) }
    val interpretedOnAction: (LauncherShellAction) -> Unit = { action ->
        interpretCardsAction(action, adaptiveStageContext, onAdaptiveStageContextChanged, onAction)
    }
    return HomeDockInterpreter(
        staticItemMenuExtras = dockItemMenuExtras,
        staticItemTapOverride = { identity -> cardsStaticItemTapOverride(identity, stages) },
        // "Show stage" from a pinned icon's menu selects a stage, so it leaves "All" like the selector.
        onAction = interpretedOnAction,
        showExpandedNotificationShelf = false,
        // The dock's own edge, wherever the user put it -- the stage-identity, pin and overflow
        // controls the floating header used to draw (#XXXX), now anchored beside the dock itself as
        // one card-shaped panel instead of floating near the top of the screen (or as two separate
        // pills -- see CardsDockEdgeCardPanel.kt) regardless of the dock's actual position.
        dockEdgeCompanion = {
            CardsDockEdgeCardPanel(
                selectedStage = shellState.snapshot.selectedStage,
                allNotificationsSelected = adaptiveStageContext.allNotificationsSelected,
                stages = stages,
                state = state,
                appIconLoader = appIconLoader,
                position = dockPosition,
                onAction = interpretedOnAction,
            )
        },
    )
}

/**
 * What a tap on a pinned dock app does in Cards: navigates to its stage when it has one, instead of
 * the every-mode default of launching the app -- a pinned Cards stage is reached the same way its
 * dynamic-section counterpart was before it got pinned. `null` (no matching stage, e.g. notification
 * access not yet granted) falls back to the default launch, same as any other pinned dock icon.
 */
internal fun cardsStaticItemTapOverride(
    identity: AppIdentity,
    stages: List<AppStage>,
): LauncherShellAction? =
    stages
        .firstOrNull { stage -> stage.id == identity.toAppStageId() }
        ?.let { stage -> LauncherShellAction.SelectAppStage(stage.id) }

/** What the Cards surface is showing, in the selector's terms. */
internal fun cardsStageSelection(
    context: AdaptiveStageInteractionContext,
    selectedStageId: AppStageId?,
): CardsStageSelection =
    when {
        context.allNotificationsSelected -> CardsStageSelection.All
        selectedStageId != null -> CardsStageSelection.Stage(selectedStageId)
        else -> CardsStageSelection.None
    }

/**
 * Forwards [action], first leaving the merged "All" view when the action selects a real stage --
 * whichever control sent it (a dock entry, a dock icon's "Show stage", a spine chip), selecting a
 * stage means showing that stage.
 */
internal fun interpretCardsAction(
    action: LauncherShellAction,
    context: AdaptiveStageInteractionContext,
    onContextChanged: (AdaptiveStageInteractionContext) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    if (action is LauncherShellAction.SelectAppStage && context.allNotificationsSelected) {
        onContextChanged(context.copy(allNotificationsSelected = false))
    }
    onAction(action)
}

/** Cards' additions to each pinned dock app's long-press menu: "Show stage" and "Pin stage". */
internal fun cardsDockItemMenuExtras(
    dock: DockModel,
    stages: List<AppStage>,
): DockItemMenuExtras =
    DockItemMenuExtras(
        byApp =
            dock.items
                .filterIsInstance<AppShortcutItem>()
                .associate { shortcut ->
                    val stageId = AppStageId(shortcut.appIdentity.packageName, shortcut.appIdentity.profile.id)
                    shortcut.appIdentity to
                        CardsStageSelector.dockItemStageActions(stageId, stages).map { stageAction ->
                            when (stageAction) {
                                CardsDockItemStageAction.SHOW_STAGE ->
                                    ShortcutContextMenuItem(
                                        label = "Show stage",
                                        action = LauncherShellAction.SelectAppStage(stageId),
                                    )

                                CardsDockItemStageAction.PIN_STAGE ->
                                    ShortcutContextMenuItem(
                                        label = "Pin stage",
                                        action = LauncherShellAction.ToggleAppStagePinned(stageId),
                                    )
                            }
                        }
                },
    )

/** The merged view's entry label; its accessible name reads the same. */
internal const val CARDS_ALL_ENTRY_LABEL = "All notifications"
