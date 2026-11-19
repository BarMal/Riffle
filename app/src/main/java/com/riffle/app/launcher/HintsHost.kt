package com.riffle.app.launcher

import androidx.compose.runtime.staticCompositionLocalOf
import com.riffle.app.launcher.onboarding.OnboardingController
import com.riffle.app.launcher.onboarding.OnboardingSurface
import com.riffle.core.domain.launcher.hints.HintEvent
import com.riffle.core.domain.launcher.hints.HintProgress
import com.riffle.core.domain.launcher.hints.Hints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the one-time Dock hints need from the shell: what has been seen and done ([progress], null until the onboarding
 * state is read, so nothing shows early), which onboarding surface is on screen ([surface]), where to report what the
 * person does ([send]) and Settings' "Show hints again" ([showAgain]). The rules are `Hints`'; the state is stored with
 * the onboarding state.
 */
class HintsHost internal constructor(
    internal val progress: StateFlow<HintProgress?>,
    internal val surface: StateFlow<OnboardingSurface>,
    internal val send: (HintEvent) -> Unit,
    internal val showAgain: () -> Unit,
) {
    companion object {
        internal fun of(controller: OnboardingController): HintsHost =
            HintsHost(
                progress = controller.hints,
                surface = controller.surface,
                send = controller::hintEvent,
                showAgain = controller::showHintsAgain,
            )

        /** Holds the progress in memory (previews and tests): nothing is stored. */
        internal fun inMemory(initial: HintProgress? = HintProgress()): HintsHost {
            val progress = MutableStateFlow(initial)
            return HintsHost(
                progress = progress,
                surface = MutableStateFlow(OnboardingSurface.None),
                send = { event -> progress.value = progress.value?.let { Hints.reduce(it, event) } },
                showAgain = { progress.value = Hints.reset() },
            )
        }
    }
}

/** The hints' host; null where the shell runs no onboarding (previews, most tests), which shows no hints. */
internal val LocalHintsHost = staticCompositionLocalOf<HintsHost?> { null }
