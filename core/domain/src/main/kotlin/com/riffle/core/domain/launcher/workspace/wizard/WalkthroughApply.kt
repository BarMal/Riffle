package com.riffle.core.domain.launcher.workspace.wizard

import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.WorkspaceValidation
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool
import com.riffle.core.domain.launcher.workspace.pool.PoolApp
import com.riffle.core.domain.launcher.workspace.pool.PoolArrangements
import com.riffle.core.domain.launcher.workspace.pool.PoolGc
import com.riffle.core.domain.launcher.workspace.pool.PoolIds
import com.riffle.core.domain.launcher.workspace.pool.PoolItemId
import com.riffle.core.domain.launcher.workspace.preset.WorkspacePresets

/** What the walkthrough is applied to: the layout and pool as stored now. */
data class WalkthroughTarget(
    val layout: LayoutWorkspaces,
    val pool: PlacedItemPool = PlacedItemPool(),
    /** The arrangements the walkthrough workspace would borrow (`PoolHomeView.candidates(WORKSPACE_ID, ...)`). */
    val candidates: List<WorkspaceId> = emptyList(),
    val ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
    val grid: GridDimensions = DEFAULT_HOME_GRID,
)

/** Exactly what was stored before: restoring both values undoes the walkthrough. */
data class WalkthroughUndo(
    val layout: LayoutWorkspaces,
    val pool: PlacedItemPool,
)

sealed interface WalkthroughResult {
    /**
     * Persist [layout] and [pool] together, keep [undo] for the Undo snackbar. [removed] are the seeded defaults a
     * fresh install replaced (never anything the user made or edited).
     */
    data class Applied(
        val layout: LayoutWorkspaces,
        val pool: PlacedItemPool,
        val workspaceId: WorkspaceId,
        val undo: WalkthroughUndo,
        val removed: List<WorkspaceId> = emptyList(),
        val apps: PlacedAppsResult? = null,
        val dockPinsAdded: List<AppPick> = emptyList(),
        /** The layout's dock is not in the pool yet (not imported), so no pin was changed. */
        val dockSkipped: Boolean = false,
    ) : WalkthroughResult

    data class Rejected(val problems: List<WalkthroughProblem>) : WalkthroughResult
}

/**
 * Applies a finished walkthrough. Never destroys data:
 * - the workspace "My home" ([WalkthroughFlow.WORKSPACE_ID]) is added and activated; one from an earlier run is updated
 *   in place (its pages, dock override, start page and name), so applying twice gives the same result;
 * - an existing user's workspaces are all kept; on a fresh install's first run the seeded default (the Nova preset,
 *   still without an
 *   arrangement of its own, so never edited) is replaced and "My home" becomes the default;
 * - picked apps go into the pool through [PlacedApps]; the current home is kept (borrowed, or forked when apps are
 *   added to it) when "Widgets from my home" is chosen;
 * - dock pins: on that first run the dock becomes the picked apps (when any were picked); otherwise the dock only
 *   gains the picked apps it does not have, up to its capacity.
 * Goals whose permission was skipped are built like any other: they show "Needs access" until it is granted.
 */
object WalkthroughApply {
    fun apply(
        state: WalkthroughState,
        context: WalkthroughContext,
        target: WalkthroughTarget,
    ): WalkthroughResult {
        val problems = WalkthroughFlow.problems(state, context, WalkthroughStep.REVIEW)
        val built = WalkthroughBuilder.build(state, context)
        val valid = WorkspaceValidation.validate(built.workspace, context.capabilities, context.descriptors).isEmpty()
        return when {
            problems.isNotEmpty() -> WalkthroughResult.Rejected(problems)
            !valid -> WalkthroughResult.Rejected(listOf(WalkthroughProblem.InvalidWorkspace))
            else -> applyBuilt(state, context, target, built)
        }
    }

    private fun applyBuilt(
        state: WalkthroughState,
        context: WalkthroughContext,
        target: WalkthroughTarget,
        built: BuiltHome,
    ): WalkthroughResult.Applied {
        val id = built.workspace.id
        val fresh = context.replacesDefault
        val seeds = if (fresh) seedsOf(target, id) else emptyList()
        val layout = seeds.fold(withHome(target.layout, built)) { acc, seed -> acc.remove(seed) }
        val apps =
            PlacedApps.place(
                target.pool,
                id,
                built.apps,
                WalkthroughBuilder.APPS_PAGE,
                built.keepHome,
                target.candidates,
                target.ids,
                target.grid,
            )
        val dock = DockPins.apply(apps.pool, state.dockPins, fresh, context.dockCapacity, target.ids)
        return WalkthroughResult.Applied(
            layout = if (fresh) layout.withDefault(id) else layout,
            pool = dock.pool,
            workspaceId = id,
            undo = WalkthroughUndo(target.layout, target.pool),
            removed = seeds,
            apps = apps.takeIf { built.apps.isNotEmpty() },
            dockPinsAdded = dock.added,
            dockSkipped = dock.skipped,
        )
    }

    /** [layout] with the built workspace added, or an earlier run's updated in place, and active. */
    private fun withHome(
        layout: LayoutWorkspaces,
        built: BuiltHome,
    ): LayoutWorkspaces {
        val home = built.workspace
        return if (layout.find(home.id) == null) {
            layout.add(home, activate = true)
        } else {
            layout.replace(home.id) {
                it.copy(name = home.name, pages = home.pages, dock = home.dock, startPageId = home.startPageId)
            }.activate(home.id)
        }
    }

    /** The seeded defaults a first run replaces: the Nova preset without an arrangement of its own (never edited). */
    private fun seedsOf(
        target: WalkthroughTarget,
        home: WorkspaceId,
    ): List<WorkspaceId> =
        target.layout.workspaces.filter {
            it.id != home && it.presetId == WorkspacePresets.DEFAULT_ID &&
                !PoolArrangements.ownsArrangement(target.pool, it.id)
        }.map { it.id }
}

internal data class DockPinsResult(
    val pool: PlacedItemPool,
    val added: List<AppPick>,
    val skipped: Boolean,
)

/**
 * Pins apps in the layout's shared dock. Dock items are exclusive to the dock (`PoolDock`), so a pin is its own
 * `PoolApp`, reused when the dock already pins that app. With [replace] the pins become exactly [picks] (in order, up
 * to [capacity]); otherwise the picks the dock lacks are appended while there is room. No picks change nothing.
 */
internal object DockPins {
    fun apply(
        pool: PlacedItemPool,
        picks: List<AppPick>,
        replace: Boolean,
        capacity: Int,
        ids: WorkspaceIdFactory,
    ): DockPinsResult {
        val dock = pool.dock
        if (dock == null || picks.isEmpty()) {
            return DockPinsResult(
                pool,
                emptyList(),
                skipped = dock == null && picks.isNotEmpty(),
            )
        }
        val pinned =
            dock.pins.mapNotNull {
                    id ->
                (pool.items[id] as? PoolApp)?.takeIf { it.appShortcutId == null }?.let { it.appIdentity to id }
            }.toMap()
        val wanted = picks.distinctBy { it.identity }
        val taken = pool.items.keys.toMutableSet()
        val created = mutableListOf<PoolApp>()
        val added = mutableListOf<AppPick>()

        fun pinFor(pick: AppPick): PoolItemId =
            pinned[pick.identity] ?: PoolApp(PoolIds.fresh(taken, ids), pick.identity, pick.label).also {
                taken += it.id
                created += it
                added += pick
            }.id

        val pins =
            if (replace) {
                wanted.take(capacity).map(::pinFor)
            } else {
                val room = (capacity - dock.pins.size).coerceAtLeast(0)
                dock.pins + wanted.filter { it.identity !in pinned }.take(room).map(::pinFor)
            }
        val next = pool.copy(items = pool.items + created.associateBy { it.id }, dock = dock.copy(pins = pins))
        return DockPinsResult(PoolGc.finish(pool, next).pool, added, skipped = false)
    }
}
