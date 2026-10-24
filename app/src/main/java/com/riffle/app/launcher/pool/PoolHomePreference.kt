package com.riffle.app.launcher.pool

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The S4 cutover switch (Settings > Developer > "Home from placed items"): on, the standard home renders and edits
 * from the placed-items pool; off, it falls back to the legacy `HomeLayoutSet` path (kept for one release). Stored in
 * its own tiny preferences file and read once per process start: flipping it takes effect the next time Riffle
 * starts, when [openHomeLayouts] hands the data over in the chosen direction. Defaults to on.
 */
internal class PoolHomePreference(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = runCatching { preferences.getBoolean(KEY_ENABLED, true) }.getOrDefault(true)

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** True the first time it is called with [damaged] after a clean start (then false until a clean start again). */
    fun takeRecoveryNotice(damaged: Boolean): Boolean =
        runCatching {
            val shown = preferences.getBoolean(KEY_RECOVERY_NOTICE_SHOWN, false)
            if (damaged != shown) preferences.edit().putBoolean(KEY_RECOVERY_NOTICE_SHOWN, damaged).apply()
            damaged && !shown
        }.getOrDefault(false)

    private companion object {
        const val PREFERENCES_NAME = "riffle_pool_home"
        const val KEY_ENABLED = "enabled"
        const val KEY_RECOVERY_NOTICE_SHOWN = "recovery_notice_shown"
    }
}

/** Shown once when part of the saved home could not be read. Names the way back; no item content. */
internal const val POOL_HOME_RECOVERY_NOTICE =
    "Riffle couldn't read part of your saved home screen. To restore it, import a backup: Settings > Backup."

/**
 * The switch as the settings row sees it: the stored choice ([enabled]) and whether this process actually renders
 * the home from the pool ([activeThisProcess]); they differ until the next start, or when a store failure kept the
 * other path.
 */
internal class PoolHomeSwitch(
    private val preference: PoolHomePreference,
    val activeThisProcess: Boolean,
) {
    private val mutableEnabled = MutableStateFlow(preference.isEnabled())
    val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        preference.setEnabled(enabled)
        mutableEnabled.value = enabled
    }
}
