package com.riffle.app.launcher.pool

import android.content.Context

/**
 * The pool home's small device-local preferences file: only the "part of the saved home could not be read" notice,
 * shown once per incident. (The "Home from placed items" developer switch is gone: the pool is the only home.)
 */
internal class PoolHomePreference(context: Context) : DockOverridesRestoredFlag {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** True the first time it is called with [damaged] after a clean start (then false until a clean start again). */
    fun takeRecoveryNotice(damaged: Boolean): Boolean =
        runCatching {
            val shown = preferences.getBoolean(KEY_RECOVERY_NOTICE_SHOWN, false)
            val step = recoveryNoticeStep(alreadyShown = shown, damaged = damaged)
            if (step.shownAfter != shown) {
                preferences.edit().putBoolean(
                    KEY_RECOVERY_NOTICE_SHOWN,
                    step.shownAfter,
                ).apply()
            }
            step.show
        }.getOrDefault(false)

    /** Whether the one-time restore of the dock size from the stored workspaces (P8 left it behind) has run. */
    override fun dockOverridesRestored(): Boolean =
        runCatching { preferences.getBoolean(KEY_DOCK_OVERRIDES_RESTORED, false) }.getOrDefault(true)

    override fun markDockOverridesRestored() {
        runCatching { preferences.edit().putBoolean(KEY_DOCK_OVERRIDES_RESTORED, true).apply() }
    }

    private companion object {
        const val KEY_DOCK_OVERRIDES_RESTORED = "dock_overrides_restored"
        const val PREFERENCES_NAME = "riffle_pool_home"
        const val KEY_RECOVERY_NOTICE_SHOWN = "recovery_notice_shown"
    }
}

/** Shown once when part of the saved home could not be read. Names the way back; no item content. */
internal const val POOL_HOME_RECOVERY_NOTICE =
    "Riffle couldn't read part of your saved home screen. To restore it, import a backup: Settings > Backup."
