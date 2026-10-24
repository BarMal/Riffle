package com.riffle.app.launcher.onboarding

import com.riffle.core.domain.launcher.workspace.wizard.Onboarding
import com.riffle.core.domain.launcher.workspace.wizard.OnboardingOrigin
import com.riffle.core.domain.launcher.workspace.wizard.OnboardingPrompt
import com.riffle.core.domain.launcher.workspace.wizard.OnboardingState
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughProgress
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** What onboarding shows over the launcher right now. */
internal sealed interface OnboardingSurface {
    data object None : OnboardingSurface

    /** The one-time "Set up your home" card on the home (existing users). */
    data object Offer : OnboardingSurface

    /**
     * The walkthrough, full screen. [firstRun] is the walkthrough context's: false for a re-run from Settings, so
     * applying only ever adds ("My home" is added or updated, nothing is replaced).
     */
    data class Walkthrough(
        val origin: OnboardingOrigin,
        val firstRun: Boolean,
    ) : OnboardingSurface
}

/**
 * The onboarding state holder (Android-free): the persisted [OnboardingState] through [Onboarding]'s rules, and which
 * [surface] is up. [decideAtLaunch] runs once per process (the shell calls it once the workspaces have loaded and the
 * Home-role card is out of the way, so the two never stack); the store is read on [io], never on the first frame.
 * Every later call is a user action and writes through the store (SharedPreferences writes are asynchronous).
 */
internal class OnboardingController(
    private val store: OnboardingStorePort,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val decided = AtomicBoolean(false)
    private val mutableSurface = MutableStateFlow<OnboardingSurface>(OnboardingSurface.None)

    @Volatile
    private var state: OnboardingState? = null

    val surface: StateFlow<OnboardingSurface> = mutableSurface.asStateFlow()

    /** The state as last read or written; null before the first read. */
    val current: OnboardingState? get() = state

    /**
     * [Onboarding.atLaunch], once per process: a fresh install runs the walkthrough (until completed or skipped), an
     * existing user gets the offer at one launch only. [freshInstall] is asked only when the origin is not decided
     * yet. A second call (another activity of the same process) does nothing and returns [OnboardingPrompt.NONE].
     */
    suspend fun decideAtLaunch(freshInstall: () -> Boolean): OnboardingPrompt {
        if (!decided.compareAndSet(false, true)) return OnboardingPrompt.NONE
        val stored = withContext(io) { store.read() }
        val (next, prompt) = Onboarding.atLaunch(stored, freshInstall = stored.origin == null && freshInstall())
        val shown = if (prompt == OnboardingPrompt.RUN_WALKTHROUGH) Onboarding.started(next) else next
        if (shown != stored) withContext(io) { store.write(shown) }
        state = shown
        // A walkthrough or the offer opened meanwhile from Settings wins; nothing is shown over it.
        if (mutableSurface.value == OnboardingSurface.None) {
            mutableSurface.value =
                when (prompt) {
                    OnboardingPrompt.RUN_WALKTHROUGH ->
                        OnboardingSurface.Walkthrough(
                            origin = shown.origin ?: OnboardingOrigin.FRESH_INSTALL,
                            firstRun = shown.walkthrough != WalkthroughProgress.COMPLETED,
                        )
                    OnboardingPrompt.SHOW_OFFER -> OnboardingSurface.Offer
                    OnboardingPrompt.NONE -> OnboardingSurface.None
                }
        }
        return prompt
    }

    /** "Not now" on the offer: it never comes back. */
    fun dismissOffer() {
        update(Onboarding::offerDismissed)
        if (mutableSurface.value == OnboardingSurface.Offer) mutableSurface.value = OnboardingSurface.None
    }

    /** "Set up" on the offer: the walkthrough opens; applying it adds a workspace. */
    fun acceptOffer() {
        val next = update(Onboarding::offerAccepted)
        mutableSurface.value = OnboardingSurface.Walkthrough(next.origin ?: OnboardingOrigin.EXISTING_USER, true)
    }

    /** Settings > Workspaces > Set up your home again: always allowed; never replaces anything. */
    fun rerun() {
        val next = update(Onboarding::rerun)
        mutableSurface.value = OnboardingSurface.Walkthrough(next.origin ?: OnboardingOrigin.EXISTING_USER, false)
    }

    /** The walkthrough closed without applying ("Skip set-up", Back from the first step). */
    fun walkthroughCancelled() {
        update(Onboarding::skipped)
        mutableSurface.value = OnboardingSurface.None
    }

    /** The walkthrough was applied (an Undo afterwards does not make it "not completed"). */
    fun walkthroughCompleted() {
        update(Onboarding::completed)
        mutableSurface.value = OnboardingSurface.None
    }

    private fun update(change: (OnboardingState) -> OnboardingState): OnboardingState {
        val before = state ?: store.read()
        val next = change(before)
        state = next
        if (next != before) store.write(next)
        return next
    }
}
