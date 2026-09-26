package com.riffle.core.domain.launcher.cards

import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppCatalog

/**
 * What the Cards surface is showing, as the stage selector sees it (#1212).
 *
 * [All] is the merged view of every stage's notifications. It is not an [AppStage] and is never
 * persisted as one, so it is modelled beside the real stage selection rather than as a fake id.
 */
sealed interface CardsStageSelection {
    data object All : CardsStageSelection

    data class Stage(val stageId: AppStageId) : CardsStageSelection

    /** Nothing is selected yet: no stage exists, or the planner has not chosen one. */
    data object None : CardsStageSelection
}

/**
 * One destination in the Cards stage selector, which the dock's dynamic section draws in Cards.
 *
 * Plain data, no labels or icons: the app layer resolves those from the installed apps, and the dock
 * itself never sees this type -- it only sees neutral entries with a [key] that Cards interprets.
 */
sealed interface CardsStageSelectorEntry {
    val key: String
    val isSelected: Boolean

    /** The merged view. Always present, so it is never a dead end to reach. */
    data class All(
        val cardCount: Int,
        override val isSelected: Boolean,
    ) : CardsStageSelectorEntry {
        override val key: String
            get() = CardsStageSelector.ALL_ENTRY_KEY
    }

    data class Stage(
        val stageId: AppStageId,
        val cardCount: Int,
        val isPinned: Boolean,
        override val isSelected: Boolean,
    ) : CardsStageSelectorEntry {
        override val key: String
            get() = CardsStageSelector.stageEntryKey(stageId)
    }
}

/** What a long-press on a pinned dock app offers in Cards, beside the dock's own menu items. */
enum class CardsDockItemStageAction {
    /** Bring the app's existing stage forward. */
    SHOW_STAGE,

    /** Keep the app as a stage even with nothing waiting. */
    PIN_STAGE,
}

/**
 * The Cards stage selector: which destinations exist, in which order, and what an entry key means.
 *
 * Order is fixed so muscle memory holds: the leading entries first (today only "All"; the "Now"
 * glance stage from #1216 belongs in [leadingEntries], ahead of "All"), then every stage in the
 * planner's own order -- pinned stages in the user's pin order, then dynamic stages newest first.
 * A pinned stage with no content is still listed, so every stage is reachable by touch.
 */
object CardsStageSelector {
    const val ALL_ENTRY_KEY: String = "cards-selector:all"
    private const val STAGE_ENTRY_KEY_PREFIX = "cards-selector:stage:"

    fun stageEntryKey(stageId: AppStageId): String = STAGE_ENTRY_KEY_PREFIX + stageId.stableKey

    fun entries(
        stages: List<AppStage>,
        selection: CardsStageSelection,
    ): List<CardsStageSelectorEntry> =
        leadingEntries(stages, selection) +
            stages.map { stage ->
                CardsStageSelectorEntry.Stage(
                    stageId = stage.id,
                    cardCount = stage.content.size,
                    isPinned = stage.isPinned,
                    isSelected = selection == CardsStageSelection.Stage(stage.id),
                )
            }

    /**
     * The permanent entries ahead of the stages. The extension point for "Now" (#1216): it goes
     * first in this list, and [resolve] learns its key.
     */
    private fun leadingEntries(
        stages: List<AppStage>,
        selection: CardsStageSelection,
    ): List<CardsStageSelectorEntry> =
        listOf(
            CardsStageSelectorEntry.All(
                cardCount = stages.sumOf { stage -> stage.content.size },
                isSelected = selection == CardsStageSelection.All,
            ),
        )

    /** What activating the entry with [key] selects, or `null` when no current entry has that key. */
    fun resolve(
        key: String,
        stages: List<AppStage>,
    ): CardsStageSelection? =
        when {
            key == ALL_ENTRY_KEY -> CardsStageSelection.All
            key.startsWith(STAGE_ENTRY_KEY_PREFIX) ->
                stages
                    .firstOrNull { stage -> stageEntryKey(stage.id) == key }
                    ?.let { stage -> CardsStageSelection.Stage(stage.id) }
            else -> null
        }

    /** The selector entry index that should be scrolled into view, or -1 when nothing is selected. */
    fun selectedIndex(entries: List<CardsStageSelectorEntry>): Int = entries.indexOfFirst { entry -> entry.isSelected }

    /**
     * The stage actions a long-press on a pinned dock app offers. In Cards a tap on that icon opens
     * the app like everywhere else, so its stage is reached from here or from the selector.
     */
    fun dockItemStageActions(
        stageId: AppStageId,
        stages: List<AppStage>,
    ): List<CardsDockItemStageAction> {
        val stage = stages.firstOrNull { candidate -> candidate.id == stageId }
        return listOfNotNull(
            CardsDockItemStageAction.SHOW_STAGE.takeIf { stage != null },
            CardsDockItemStageAction.PIN_STAGE.takeIf { stage?.isPinned != true },
        )
    }

    /**
     * Whether the stage spine (the chip strip under the stack) is drawn. It is an opt-in setting,
     * but it is also the fallback whenever the dock cannot host the selector -- the dock is off or
     * hidden -- so switching the spine off never strands a stage.
     */
    fun showsSpine(
        spineEnabled: Boolean,
        dockHostsSelector: Boolean,
    ): Boolean = spineEnabled || !dockHostsSelector

    /**
     * The apps the "Add stage" picker offers for [query]: visible apps matched by the drawer's own
     * search index, minus apps that are already pinned stages, one entry per stage identity.
     */
    fun addableApps(
        apps: List<InstalledApp>,
        pinnedStageIds: Set<AppStageId>,
        query: String,
        shortcutsByApp: AppShortcutsByApp = emptyMap(),
    ): List<InstalledApp> =
        InstalledAppCatalog()
            .searchApps(apps = apps, query = query, shortcutsByApp = shortcutsByApp)
            .filterNot { app -> app.identity.toAppStageId() in pinnedStageIds }
            .distinctBy { app -> app.identity.toAppStageId() }
}
