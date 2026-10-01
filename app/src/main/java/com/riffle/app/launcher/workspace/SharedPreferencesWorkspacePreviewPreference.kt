package com.riffle.app.launcher.workspace

import android.content.Context

/**
 * The preview switch in its own tiny preferences file, so reading or flipping it touches neither the launcher
 * settings nor any workspace data (flipping it is reversible without data loss). Defaults to off.
 */
internal class SharedPreferencesWorkspacePreviewPreference(context: Context) : WorkspacePreviewPreference {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun isEnabled(): Boolean = runCatching { preferences.getBoolean(KEY_ENABLED, false) }.getOrDefault(false)

    override fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "riffle_workspace_preview"
        const val KEY_ENABLED = "enabled"
    }
}
