package com.riffle.app.launcher.workspace

import android.content.Context
import com.riffle.core.domain.launcher.workspace.settings.DisabledSourcesStore

/**
 * The sources the user turned off, in their own tiny preferences file: reading or changing them touches
 * neither the launcher settings nor any workspace data, and with the preview off nothing opens this file.
 * Holds source ids only (never item content). Everything is on until turned off.
 */
internal class SharedPreferencesDisabledSourcesStore(context: Context) : DisabledSourcesStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): Set<String> =
        runCatching { preferences.getStringSet(KEY_DISABLED, emptySet()).orEmpty().toSet() }.getOrDefault(emptySet())

    override fun write(ids: Set<String>) {
        preferences.edit().putStringSet(KEY_DISABLED, ids).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "riffle_workspace_sources"
        const val KEY_DISABLED = "disabled"
    }
}
