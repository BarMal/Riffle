package com.riffle.core.domain.launcher.onboarding

import com.riffle.core.domain.launcher.hints.Hint
import com.riffle.core.domain.launcher.hints.HintEvent
import com.riffle.core.domain.launcher.hints.HintProgress
import com.riffle.core.domain.launcher.hints.Hints
import com.riffle.core.domain.launcher.workspace.StoredValue
import com.riffle.core.domain.launcher.workspace.arr
import com.riffle.core.domain.launcher.workspace.array
import com.riffle.core.domain.launcher.workspace.bool
import com.riffle.core.domain.launcher.workspace.enumOrNull
import com.riffle.core.domain.launcher.workspace.long
import com.riffle.core.domain.launcher.workspace.num
import com.riffle.core.domain.launcher.workspace.obj
import com.riffle.core.domain.launcher.workspace.str
import com.riffle.core.domain.launcher.workspace.string

/** How this install met the set-up. Decided once, on the first launch that has onboarding state. */
enum class OnboardingOrigin {
    /** Nothing was stored: the four-step set-up runs by itself. */
    FRESH_INSTALL,

    /** An existing user updated: nothing is forced; the set-up stays reachable from Settings. */
    EXISTING_USER,
}

enum class OnboardingProgress {
    NOT_STARTED,

    /** Opened and not finished (the app was closed mid-way): a fresh install runs it again on the next launch. */
    STARTED,
    COMPLETED,

    /** Closed without finishing. A fresh install is not asked again. */
    SKIPPED,
}

/** What the launcher does about onboarding at this launch. */
enum class OnboardingPrompt {
    NONE,

    /** Open the four-step set-up now (fresh install, not finished or skipped). */
    RUN,
}

/**
 * The four steps of the set-up, one per Place (ADR 0003): Home, the Dock, the Library and the Stream. Permissions are
 * asked only in the Stream step.
 */
enum class OnboardingStep {
    HOME,
    DOCK,
    LIBRARY,
    STREAM,
    ;

    /** The step after this one, or null after the last. */
    fun next(): OnboardingStep? = entries.getOrNull(ordinal + 1)

    /** The step before this one, or null before the first. */
    fun previous(): OnboardingStep? = entries.getOrNull(ordinal - 1)

    val isLast: Boolean get() = next() == null
}

/**
 * The persisted onboarding state. Device-local (not part of backup): it describes this install. [origin] null means
 * "not decided yet" (nothing was stored, or it could not be read).
 */
data class OnboardingState(
    val origin: OnboardingOrigin? = null,
    val progress: OnboardingProgress = OnboardingProgress.NOT_STARTED,
    /** How many times the set-up was finished (Settings re-runs included). */
    val completedRuns: Int = 0,
    /**
     * "How should Home start?" was answered (a choice, or "Skip set-up", which starts with all apps): Home is seeded
     * once and never again, whatever happens to the set-up later (`places-views-plan.md`, "First run").
     */
    val homeStartChosen: Boolean = false,
    /** What the one-time Dock hints have been shown and what has been done (ADR 0003, Amendment 12). */
    val hints: HintProgress = HintProgress(),
)

/**
 * The onboarding rules, pure. The app calls [atLaunch] once per process start with whether the install looks fresh
 * (nothing stored before this launch); it decides the origin the first time and never again, so a later fresh-looking
 * start (a cleared store) never re-runs it.
 */
object Onboarding {
    /** The state with its origin decided, and what to show now. Existing users are never prompted. */
    fun atLaunch(
        state: OnboardingState,
        freshInstall: Boolean,
    ): Pair<OnboardingState, OnboardingPrompt> {
        val decided =
            if (state.origin == null) {
                state.copy(
                    origin = if (freshInstall) OnboardingOrigin.FRESH_INSTALL else OnboardingOrigin.EXISTING_USER,
                )
            } else {
                state
            }
        val finished = decided.progress in FINISHED
        return if (decided.origin == OnboardingOrigin.FRESH_INSTALL && !finished) {
            started(decided) to OnboardingPrompt.RUN
        } else {
            decided to OnboardingPrompt.NONE
        }
    }

    fun started(state: OnboardingState): OnboardingState = state.copy(progress = OnboardingProgress.STARTED)

    fun completed(state: OnboardingState): OnboardingState =
        state.copy(progress = OnboardingProgress.COMPLETED, completedRuns = state.completedRuns + 1)

    /** Home was seeded (or the choice was made): the set-up never asks, and nothing seeds, again. */
    fun homeStartChosen(state: OnboardingState): OnboardingState = state.copy(homeStartChosen = true)

    /** Something the one-time hints care about happened. */
    fun hintEvent(
        state: OnboardingState,
        event: HintEvent,
    ): OnboardingState {
        val next = Hints.reduce(state.hints, event)
        return if (next == state.hints) state else state.copy(hints = next)
    }

    /** Settings > About > Show hints again: every hint is due again. */
    fun showHintsAgain(state: OnboardingState): OnboardingState = state.copy(hints = Hints.reset())

    /** Closed without finishing. A re-run from Settings that is cancelled keeps an earlier completion. */
    fun skipped(state: OnboardingState): OnboardingState =
        if (state.progress == OnboardingProgress.COMPLETED) state else state.copy(progress = OnboardingProgress.SKIPPED)

    /** Settings > Set up Riffle again: always allowed, whatever the state. */
    fun rerun(state: OnboardingState): OnboardingState =
        if (state.progress == OnboardingProgress.COMPLETED) state else started(state)

    private val FINISHED = setOf(OnboardingProgress.COMPLETED, OnboardingProgress.SKIPPED)
}

/**
 * Codec of [OnboardingState] over [StoredValue]. Additive and safe: decoding never throws; a missing or unknown field
 * reads as its default, a missing or unreadable value as [OnboardingState] with no origin. The keys are those of the
 * walkthrough this replaced (`walkthrough`, `completedRuns`; a stored `offer` is ignored), so what an earlier build
 * wrote still reads.
 */
object OnboardingCodec {
    const val VERSION = 1

    fun encode(state: OnboardingState): StoredValue.Obj =
        obj(
            "version" to num(VERSION),
            "origin" to state.origin?.let { str(it.name) },
            "walkthrough" to str(state.progress.name),
            "completedRuns" to num(state.completedRuns),
            "homeStartChosen" to StoredValue.Bool(state.homeStartChosen),
            "hints" to encodeHints(state.hints),
        )

    fun decode(value: StoredValue?): OnboardingState {
        val root = value as? StoredValue.Obj ?: return OnboardingState()
        return OnboardingState(
            origin = enumOrNull<OnboardingOrigin>(root.string("origin")),
            progress = enumOrNull<OnboardingProgress>(root.string("walkthrough")) ?: legacyCompleted(root),
            completedRuns = root.long("completedRuns")?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt() ?: 0,
            homeStartChosen = root.bool("homeStartChosen") ?: false,
            hints = decodeHints(root.obj("hints")),
        )
    }

    private fun encodeHints(hints: HintProgress): StoredValue.Obj =
        obj(
            "retired" to arr(hints.retired.map { str(it.storedId) }),
            "homeVisits" to num(hints.homeVisits),
            "libraryOpenedElsewhere" to StoredValue.Bool(hints.libraryOpenedElsewhere),
            "lastRetiredVisit" to hints.lastRetiredVisit?.let { num(it) },
        )

    /** Absent in everything written before the hints: nothing retired, no visits counted. Unknown ids are dropped. */
    private fun decodeHints(root: StoredValue.Obj?): HintProgress =
        if (root == null) {
            HintProgress()
        } else {
            HintProgress(
                retired =
                    root.array("retired")
                        .mapNotNull { Hint.fromStoredId((it as? StoredValue.Str)?.value) }
                        .toSet(),
                homeVisits = root.long("homeVisits")?.coerceIn(0, MAX_STORED_VISITS)?.toInt() ?: 0,
                libraryOpenedElsewhere = root.bool("libraryOpenedElsewhere") ?: false,
                lastRetiredVisit = root.long("lastRetiredVisit")?.coerceIn(0, MAX_STORED_VISITS)?.toInt(),
            )
        }

    private const val MAX_STORED_VISITS = 100_000L

    /** A hand-written or future value may carry only a `completed` flag; honour it rather than re-running. */
    private fun legacyCompleted(root: StoredValue.Obj): OnboardingProgress =
        if (root.bool("completed") == true) OnboardingProgress.COMPLETED else OnboardingProgress.NOT_STARTED
}
