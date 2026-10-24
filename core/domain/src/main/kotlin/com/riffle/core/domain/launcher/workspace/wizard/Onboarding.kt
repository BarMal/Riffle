package com.riffle.core.domain.launcher.workspace.wizard

import com.riffle.core.domain.launcher.workspace.StoredValue
import com.riffle.core.domain.launcher.workspace.bool
import com.riffle.core.domain.launcher.workspace.enumOrNull
import com.riffle.core.domain.launcher.workspace.long
import com.riffle.core.domain.launcher.workspace.num
import com.riffle.core.domain.launcher.workspace.obj
import com.riffle.core.domain.launcher.workspace.str
import com.riffle.core.domain.launcher.workspace.string

/** How this install met the walkthrough. Decided once, on the first launch that has onboarding state. */
enum class OnboardingOrigin {
    /** Nothing was stored: the walkthrough runs by itself, and applying it replaces the seeded default. */
    FRESH_INSTALL,

    /** An existing user updated: a one-time "Set up your home" offer; applying it adds a workspace. */
    EXISTING_USER,
}

enum class WalkthroughProgress {
    NOT_STARTED,

    /** Opened and not finished (the app was closed mid-way): a fresh install runs it again on the next launch. */
    STARTED,
    COMPLETED,

    /** Closed without applying. A fresh install keeps its default and is not asked again. */
    SKIPPED,
}

enum class OfferProgress {
    NOT_SHOWN,

    /** Shown at one launch; never shown again, whatever the user did with it. */
    SHOWN,
    DISMISSED,
    ACCEPTED,
}

/** What the launcher does about onboarding at this launch. */
enum class OnboardingPrompt {
    NONE,

    /** Open the walkthrough now (fresh install, not finished or skipped). */
    RUN_WALKTHROUGH,

    /** Show the dismissible "Set up your home" offer (existing user, once). */
    SHOW_OFFER,
}

/**
 * The persisted onboarding state. Device-local (not part of backup): it describes this install, and a restore onto a
 * new phone brings the workspaces the walkthrough made, so asking again there would only duplicate them. [origin] null
 * means "not decided yet" (nothing was stored, or it could not be read).
 */
data class OnboardingState(
    val origin: OnboardingOrigin? = null,
    val walkthrough: WalkthroughProgress = WalkthroughProgress.NOT_STARTED,
    val offer: OfferProgress = OfferProgress.NOT_SHOWN,
    /** How many times the walkthrough was applied (Settings re-runs included). */
    val completedRuns: Int = 0,
)

/**
 * The onboarding rules, pure. The app calls [atLaunch] once per process start with whether the install looks fresh
 * (no workspace set stored before this launch, `WorkspaceBootstrap` had to seed); [atLaunch] decides the origin the
 * first time and never again, so a later "fresh-looking" start (a cleared workspace store) never re-runs it.
 */
object Onboarding {
    /** The state with its origin decided, and what to show now. Showing the offer records it as shown. */
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
        val finished = decided.walkthrough in FINISHED
        return when {
            decided.origin == OnboardingOrigin.FRESH_INSTALL && !finished -> decided to OnboardingPrompt.RUN_WALKTHROUGH
            decided.origin == OnboardingOrigin.EXISTING_USER && !finished && decided.offer == OfferProgress.NOT_SHOWN ->
                decided.copy(offer = OfferProgress.SHOWN) to OnboardingPrompt.SHOW_OFFER
            else -> decided to OnboardingPrompt.NONE
        }
    }

    fun started(state: OnboardingState): OnboardingState = state.copy(walkthrough = WalkthroughProgress.STARTED)

    /** Applied. A completed walkthrough also settles the offer. */
    fun completed(state: OnboardingState): OnboardingState =
        state.copy(
            walkthrough = WalkthroughProgress.COMPLETED,
            offer = settled(state.offer),
            completedRuns = state.completedRuns + 1,
        )

    /** Closed without applying. A re-run from Settings that is cancelled keeps an earlier completion. */
    fun skipped(state: OnboardingState): OnboardingState =
        if (state.walkthrough == WalkthroughProgress.COMPLETED) {
            state
        } else {
            state.copy(
                walkthrough = WalkthroughProgress.SKIPPED,
            )
        }

    fun offerDismissed(state: OnboardingState): OnboardingState = state.copy(offer = OfferProgress.DISMISSED)

    fun offerAccepted(state: OnboardingState): OnboardingState = started(state.copy(offer = OfferProgress.ACCEPTED))

    /** Settings > Set up your home: always allowed, whatever the state. */
    fun rerun(state: OnboardingState): OnboardingState =
        if (state.walkthrough == WalkthroughProgress.COMPLETED) state else started(state)

    private val FINISHED = setOf(WalkthroughProgress.COMPLETED, WalkthroughProgress.SKIPPED)

    private fun settled(offer: OfferProgress): OfferProgress =
        if (offer == OfferProgress.NOT_SHOWN) OfferProgress.DISMISSED else offer
}

/**
 * Codec of [OnboardingState] over [StoredValue]. Additive and safe: decoding never throws; a missing or unknown field
 * reads as its default, a missing or unreadable value as [OnboardingState] with no origin (the app then decides it from
 * whether the install looks fresh). A newer version is read best effort, field by field.
 */
object OnboardingCodec {
    const val VERSION = 1

    fun encode(state: OnboardingState): StoredValue.Obj =
        obj(
            "version" to num(VERSION),
            "origin" to state.origin?.let { str(it.name) },
            "walkthrough" to str(state.walkthrough.name),
            "offer" to str(state.offer.name),
            "completedRuns" to num(state.completedRuns),
        )

    fun decode(value: StoredValue?): OnboardingState {
        val root = value as? StoredValue.Obj ?: return OnboardingState()
        return OnboardingState(
            origin = enumOrNull<OnboardingOrigin>(root.string("origin")),
            walkthrough = enumOrNull<WalkthroughProgress>(root.string("walkthrough")) ?: legacyCompleted(root),
            offer = enumOrNull<OfferProgress>(root.string("offer")) ?: OfferProgress.NOT_SHOWN,
            completedRuns = root.long("completedRuns")?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt() ?: 0,
        )
    }

    /** A hand-written or future value may carry only a `completed` flag; honour it rather than re-running. */
    private fun legacyCompleted(root: StoredValue.Obj): WalkthroughProgress =
        if (root.bool("completed") == true) WalkthroughProgress.COMPLETED else WalkthroughProgress.NOT_STARTED
}
