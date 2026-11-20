package com.riffle.core.domain.launcher.hints

/**
 * The hints that teach a Dock gesture (ADR 0003, Amendments 12 and 21), each shown at most once, in the order they are
 * offered when more than one is due. [storedId] is a stored contract: never rename one, only add new ids.
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
 * a backup). A hint is retired for good as soon as it has been on screen (or its gesture is performed first).
 */
data class HintProgress(
    val retired: Set<Hint> = emptySet(),
    /** How many times Home has come to the front. */
    val homeVisits: Int = 0,
    /** The Library was opened by a route other than the Dock's pull (a tap on search, a gesture, the Home menu). */
    val libraryOpenedElsewhere: Boolean = false,
    /** The Home visit on which a hint was last retired: the next hint waits [Hints.MIN_VISITS_BETWEEN_HINTS] more. */
    val lastRetiredVisit: Int? = null,
)

/** Something that happened that the hints care about. */
sealed interface HintEvent {
    /** Home came to the front (a launch, the Home button, coming back from an app or a sheet). */
    data object HomeVisited : HintEvent

    /** [hint] came on screen: it is retired from now on, whether or not it is acted on. */
    data class Shown(val hint: Hint) : HintEvent

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

/** Whether nothing may speak now: Simple mode, the set-up, a drag, or the Dock moving. */
private val HintContext.keepsQuiet: Boolean
    get() = simpleMode || setUpShowing || (dragging || dockBusy)

/**
 * The hint rules, pure. At most one hint is due at a time ([due]); each retires for good once it has been on screen or
 * the gesture it teaches is performed ([reduce]); a hint waits [MIN_VISITS_BETWEEN_HINTS] Home visits after the last
 * one was shown, so a new person meets one at most every few visits; none shows in Simple mode, over the set-up, or
 * while something is being dragged or the Dock moves.
 */
object Hints {
    /** The Library's pull is hinted after this many Home visits in which the Library was never opened. */
    const val PULL_AFTER_HOME_VISITS = 4

    /**
     * The pacing rule: a hint is shown only once this many Home visits have passed since the last one was shown (the
     * visit that showed it counts as zero), so the next hint is never on the same visit or the next.
     */
    const val MIN_VISITS_BETWEEN_HINTS = 3

    /** The count stops here, so a long-lived install never overflows it. */
    private const val MAX_HOME_VISITS = 100_000

    /** The hint to show now, or null. */
    fun due(
        progress: HintProgress,
        context: HintContext,
    ): Hint? {
        if (context.keepsQuiet) return null
        return Hint.entries.firstOrNull { hint ->
            hint !in progress.retired && applies(hint, context) && isPaced(progress) && isReasoned(hint, progress)
        }
    }

    /**
     * Whether [hint], already on screen, still has a point: nothing speaks over the set-up or a drag, and the
     * situation it describes still holds (the Stream opened, the Library opened or the Dock moved all end it).
     */
    fun stillApplies(
        hint: Hint,
        context: HintContext,
    ): Boolean = !context.keepsQuiet && applies(hint, context)

    private fun applies(
        hint: Hint,
        context: HintContext,
    ): Boolean =
        !context.libraryShown &&
            when (hint) {
                Hint.DOCK_PRESS -> context.streamAvailable && !context.streamShown
                Hint.DOCK_MENU -> !context.streamShown && context.dockHasItems
                Hint.DOCK_PULL -> context.libraryAvailable && !context.streamShown
                Hint.DOCK_SCRUB -> context.streamShown && context.stackCount >= 2
            }

    /** The next hint waits for [MIN_VISITS_BETWEEN_HINTS] Home visits after the one that was last shown. */
    private fun isPaced(progress: HintProgress): Boolean {
        val last = progress.lastRetiredVisit
        return last == null || progress.homeVisits >= last + MIN_VISITS_BETWEEN_HINTS
    }

    private fun isReasoned(
        hint: Hint,
        progress: HintProgress,
    ): Boolean =
        hint != Hint.DOCK_PULL || progress.libraryOpenedElsewhere || progress.homeVisits >= PULL_AFTER_HOME_VISITS

    fun reduce(
        progress: HintProgress,
        event: HintEvent,
    ): HintProgress =
        when (event) {
            HintEvent.HomeVisited ->
                progress.copy(homeVisits = (progress.homeVisits + 1).coerceAtMost(MAX_HOME_VISITS))
            is HintEvent.Shown -> retire(progress, event.hint)
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
