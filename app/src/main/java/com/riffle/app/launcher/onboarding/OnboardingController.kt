package com.riffle.app.launcher.onboarding

import com.riffle.core.domain.launcher.onboarding.Onboarding
import com.riffle.core.domain.launcher.onboarding.OnboardingPrompt
import com.riffle.core.domain.launcher.onboarding.OnboardingState
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

    /** The four-step set-up, full screen. */
    data object SetUp : OnboardingSurface
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
     * [Onboarding.atLaunch], once per process: a fresh install runs the set-up (until finished or skipped); an
     * existing user gets nothing. [freshInstall] is asked only when the origin is not decided yet. A second call
     * (another activity of the same process) does nothing and returns [OnboardingPrompt.NONE].
     */
    suspend fun decideAtLaunch(freshInstall: () -> Boolean): OnboardingPrompt {
        if (!decided.compareAndSet(false, true)) return OnboardingPrompt.NONE
        val stored = withContext(io) { store.read() }
        val (next, prompt) = Onboarding.atLaunch(stored, freshInstall = stored.origin == null && freshInstall())
        if (next != stored) withContext(io) { store.write(next) }
        state = next
        // A set-up opened meanwhile from Settings wins; nothing is shown over it.
        if (prompt == OnboardingPrompt.RUN && mutableSurface.value == OnboardingSurface.None) {
            mutableSurface.value = OnboardingSurface.SetUp
        }
        return prompt
    }

    /** Settings > Set up Riffle again: always allowed; it changes nothing the user placed. */
    fun rerun() {
        update(Onboarding::rerun)
        mutableSurface.value = OnboardingSurface.SetUp
    }

    /** The set-up closed before the last step ("Skip", Back from the first step). */
    fun cancelled() {
        update(Onboarding::skipped)
        mutableSurface.value = OnboardingSurface.None
    }

    /** The last step was finished. */
    fun completed() {
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
