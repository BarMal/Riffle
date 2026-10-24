package com.riffle.app.launcher.onboarding

import android.content.Context
import com.riffle.core.domain.launcher.workspace.wizard.OnboardingState

/**
 * The onboarding state in its own tiny preferences file (`riffle_onboarding`), like the first-run markers: device
 * local, excluded from Auto Backup and device transfer, and not part of Riffle's backup document. The file is opened
 * lazily, on the first read (which the shell does off the main thread, after the workspaces have loaded).
 */
internal class SharedPreferencesOnboardingStore(
    context: Context,
) : OnboardingStorePort {
    private val appContext = context.applicationContext
    private val preferences by lazy { appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE) }

    override fun read(): OnboardingState =
        decodeOnboardingState(runCatching { preferences.getString(KEY_STATE, null) }.getOrNull())

    override fun write(state: OnboardingState) {
        runCatching { preferences.edit().putString(KEY_STATE, encodeOnboardingState(state)).apply() }
    }

    private companion object {
        const val PREFERENCES_NAME = "riffle_onboarding"
        const val KEY_STATE = "state"
    }
}
