package com.riffle.core.domain.launcher.hints

/**
 * The one-time hints that teach a Dock gesture (ADR 0003, Amendment 12), in the order they are offered when more than
 * one is due. [storedId] is a stored contract: never rename one, only add new ids.
 */
enum class Hint(
    val storedId: String,
) {
    /** Press the Dock down to see what is live (the Stream). Shown on Home. */
    DOCK_PRESS("dockPress"),

    /** Long-press a Dock item for its menu. Shown on Home. */
    DOCK_MENU("dockMenu"),

    /** Pull the Dock up for the Library. Shown on Home. */
    DOCK_PULL("dockPull"),

    /** Hold, then slide along the Dock to change stack. Shown while the Stream is out. */
    DOCK_SCRUB("dockScrub"),
    ;

    /** Whether the hint stands on Home (the others stand over the Stream). */
    val onHome: Boolean get() = this != DOCK_SCRUB

    companion object {
        fun fromStoredId(id: String?): Hint? = entries.firstOrNull { it.storedId == id }
    }
}

/**
 * What the person has done and seen, as far as the hints care (stored with the onboarding state: device local, never in
 * a backup). A hint is retired for good once it is dismissed or its gesture is performed.
 */
data class HintProgress(
    val retired: Set<Hint> = emptySet(),
    /** How many times Home has come to the front. */
    val homeVisits: Int = 0,
    /** The Library was opened by a route other than the Dock's pull (a tap on search, a gesture, the Home menu). */
    val libraryOpenedElsewhere: Boolean = false,
    /** The Home visit on which a hint was last retired: the next Home hint waits for a later one. */
    val lastRetiredVisit: Int? = null,
)

/** Something that happened that the hints care about. */
sealed interface HintEvent {
    /** Home came to the front (a launch, the Home button, coming back from an app or a sheet). */
    data object HomeVisited : HintEvent

    /** "Got it" on [hint]. */
    data class Dismissed(val hint: Hint) : HintEvent

    /** The Stream opened, by any route: the press (or its equivalents) is found. */
    data object StreamOpened : HintEvent

    /** The Library opened. [byDockPull] says the Dock's pull did it, which is the gesture the hint teaches. */
    data class LibraryOpened(val byDockPull: Boolean) : HintEvent

    /** A hold and slide along the Dock changed the stack. */
    data object StackScrubbed : HintEvent

    /** A Dock item's menu (or a tile's) was opened. */
    data object DockMenuOpened : HintEvent
}

/** What is on screen and what exists, when the question "which hint is due?" is asked. */
data class HintContext(
    /** Simple mode has no Stream, no stacks and no Dock gestures to teach. */
    val simpleMode: Boolean = false,
    /** The Home-role card or the set-up is on screen: nothing else speaks over them. */
    val setUpShowing: Boolean = false,
    /** A Home item or a widget is being dragged. */
    val dragging: Boolean = false,
    /** The Dock is moving (a press, a pull) or choosing. */
    val dockBusy: Boolean = false,
    val streamAvailable: Boolean = false,
    val streamShown: Boolean = false,
    val libraryAvailable: Boolean = false,
    val libraryShown: Boolean = false,
    /** The Dock holds at least one item. */
    val dockHasItems: Boolean = false,
    /** How many stacks (pages) the Stream shows now. */
    val stackCount: Int = 0,
)

/**
 * The hint rules, pure. At most one hint is due at a time ([due]); each retires for good once it is dismissed or the
 * gesture it teaches is performed ([reduce]); none shows in Simple mode, over the set-up, or while something is being
 * dragged or the Dock moves.
 */
object Hints {
    /** The Library's pull is hinted after this many Home visits in which the Library was never opened. */
    const val PULL_AFTER_HOME_VISITS = 4

    /** The count stops here, so a long-lived install never overflows it. */
    private const val MAX_HOME_VISITS = 100_000

    /** The hint to show now, or null. */
    fun due(
        progress: HintProgress,
        context: HintContext,
    ): Hint? {
        if (context.simpleMode || context.setUpShowing || context.dragging || context.dockBusy) return null
        return Hint.entries.firstOrNull { hint -> hint !in progress.retired && eligible(hint, progress, context) }
    }

    private fun eligible(
        hint: Hint,
        progress: HintProgress,
        context: HintContext,
    ): Boolean {
        if (context.libraryShown) return false
        // The next Home hint waits for a later visit than the one a hint was last put away on.
        val paced = !hint.onHome || progress.lastRetiredVisit?.let { progress.homeVisits > it } != false
        return paced &&
            when (hint) {
                Hint.DOCK_PRESS -> context.streamAvailable && !context.streamShown
                Hint.DOCK_MENU -> !context.streamShown && context.dockHasItems
                Hint.DOCK_PULL ->
                    context.libraryAvailable && !context.streamShown &&
                        (progress.libraryOpenedElsewhere || progress.homeVisits >= PULL_AFTER_HOME_VISITS)
                Hint.DOCK_SCRUB -> context.streamShown && context.stackCount >= 2
            }
    }

    fun reduce(
        progress: HintProgress,
        event: HintEvent,
    ): HintProgress =
        when (event) {
            HintEvent.HomeVisited ->
                progress.copy(homeVisits = (progress.homeVisits + 1).coerceAtMost(MAX_HOME_VISITS))
            is HintEvent.Dismissed -> retire(progress, event.hint)
            HintEvent.StreamOpened -> retire(progress, Hint.DOCK_PRESS)
            is HintEvent.LibraryOpened ->
                if (event.byDockPull) {
                    retire(progress, Hint.DOCK_PULL)
                } else {
                    progress.copy(libraryOpenedElsewhere = true)
                }
            HintEvent.StackScrubbed -> retire(progress, Hint.DOCK_SCRUB)
            HintEvent.DockMenuOpened -> retire(progress, Hint.DOCK_MENU)
        }

    /** "Show hints again": every hint is due again, as on a fresh install (the Home visits start over). */
    fun reset(): HintProgress = HintProgress()

    private fun retire(
        progress: HintProgress,
        hint: Hint,
    ): HintProgress =
        if (hint in progress.retired) {
            progress
        } else {
            progress.copy(retired = progress.retired + hint, lastRetiredVisit = progress.homeVisits)
        }
}
