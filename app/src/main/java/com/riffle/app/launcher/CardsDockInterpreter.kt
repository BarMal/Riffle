package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.cards.AdaptiveStageInteractionContext
import com.riffle.core.domain.launcher.cards.AppStage
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.cards.CardsDockItemStageAction
import com.riffle.core.domain.launcher.cards.CardsStageSelection
import com.riffle.core.domain.launcher.cards.CardsStageSelector
import com.riffle.core.domain.launcher.cards.CardsStageSelectorEntry
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel

/*
 * Cards' side of the dock boundary (Decision 2 in docs/product/modes-dock-handle-and-cards-plan.md).
 *
 * The dock is mode-agnostic: it renders neutral entries and reports neutral intents -- a pinned app
 * tapped (always opens it), a dynamic entry activated (by key), a long-press menu item chosen. Every
 * piece of Cards meaning -- that the dynamic section is the stage selector, that "All" comes first,
 * what "Show stage" does -- is decided here, in Cards code, and never in the dock.
 */

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
 * The stage selector as dock entries. Every entry delegates back to [interpretCardsDockEntry] by
 * key, so the dock never learns what a stage is.
 */
internal fun cardsStageSelectorDockEntries(
    entries: List<CardsStageSelectorEntry>,
    state: LauncherShellState,
): List<DockDynamicEntry> =
    entries.map { entry ->
        val stage = entry as? CardsStageSelectorEntry.Stage
        val label = stage?.let { stageLabel(it.stageId, state) } ?: CARDS_ALL_ENTRY_LABEL
        val cardCount =
            when (entry) {
                is CardsStageSelectorEntry.All -> entry.cardCount
                is CardsStageSelectorEntry.Stage -> entry.cardCount
            }
        DockDynamicEntry(
            key = entry.key,
            label = label,
            identity = stage?.let { stageAppIdentity(it.stageId, state) },
            badgeCount = cardCount,
            isSelected = entry.isSelected,
            contentDescription =
                cardsStageSelectorEntryContentDescription(
                    label = label,
                    cardCount = cardCount,
                    isPinned = stage?.isPinned == true,
                    isSelected = entry.isSelected,
                ),
            intent = DockDynamicEntryIntent.Delegate,
        )
    }

/**
 * What activating a selector entry does: "All" switches the surface to the merged view (a choice
 * the interaction context holds, not a stage selection), a stage leaves "All" and selects itself.
 * A key that no longer resolves -- the stage went away between frames -- does nothing.
 */
internal fun interpretCardsDockEntry(
    key: String,
    stages: List<AppStage>,
    context: AdaptiveStageInteractionContext,
    onContextChanged: (AdaptiveStageInteractionContext) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    when (val selection = CardsStageSelector.resolve(key, stages)) {
        CardsStageSelection.All ->
            if (!context.allNotificationsSelected) onContextChanged(context.copy(allNotificationsSelected = true))

        is CardsStageSelection.Stage ->
            interpretCardsAction(
                action = LauncherShellAction.SelectAppStage(selection.stageId),
                context = context,
                onContextChanged = onContextChanged,
                onAction = onAction,
            )

        CardsStageSelection.None, null -> Unit
    }
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

internal fun cardsStageSelectorEntryContentDescription(
    label: String,
    cardCount: Int,
    isPinned: Boolean,
    isSelected: Boolean,
): String =
    buildList {
        add(label)
        if (cardCount > 0) {
            add("$cardCount ${if (cardCount == 1) "card" else "cards"}")
        } else if (isPinned) {
            add("pinned, nothing new")
        }
        add(if (isSelected) "Showing. Open stage" else "Open stage")
    }.joinToString(separator = ", ")

/** The merged view's entry label; its accessible name reads the same. */
internal const val CARDS_ALL_ENTRY_LABEL = "All notifications"
