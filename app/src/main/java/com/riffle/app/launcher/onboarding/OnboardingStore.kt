package com.riffle.app.launcher.onboarding

import com.riffle.app.launcher.toJson
import com.riffle.app.launcher.toStoredValue
import com.riffle.core.domain.launcher.workspace.wizard.OnboardingCodec
import com.riffle.core.domain.launcher.workspace.wizard.OnboardingState
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Where this install's onboarding state lives ([OnboardingState]: how it met the walkthrough, and the offer). Device
 * local: never in a workspace, the launcher settings or Riffle's backup document (see
 * `workspaces-wizard-and-onboarding.md` section 4). Reads never throw; nothing stored reads as "not decided".
 */
internal interface OnboardingStorePort {
    fun read(): OnboardingState

    fun write(state: OnboardingState)
}

/** For tests and hosts without storage: holds the state in memory. */
internal class InMemoryOnboardingStore(
    var stored: OnboardingState? = null,
) : OnboardingStorePort {
    var writes: Int = 0
        private set

    override fun read(): OnboardingState = stored ?: OnboardingState()

    override fun write(state: OnboardingState) {
        stored = state
        writes++
    }
}

/** The stored text of [state]: [OnboardingCodec]'s tree as JSON. Holds no item content. */
internal fun encodeOnboardingState(state: OnboardingState): String = OnboardingCodec.encode(state).toJson().toString()

/** Never throws: null, malformed or non-object text reads as an undecided [OnboardingState]. */
internal fun decodeOnboardingState(text: String?): OnboardingState {
    val parsed = text?.let { runCatching { JSONTokener(it).nextValue() }.getOrNull() } as? JSONObject
    return OnboardingCodec.decode(parsed?.toStoredValue())
}
