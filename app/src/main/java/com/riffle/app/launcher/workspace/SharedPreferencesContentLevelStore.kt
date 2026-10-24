package com.riffle.app.launcher.workspace

import android.content.Context
import com.riffle.core.domain.launcher.workspace.settings.ContentLevelStore

/**
 * The per-source content levels (Settings > Privacy), in the same tiny preferences file as the disabled sources:
 * device-local like them, never in the launcher settings, a workspace or Riffle's backup document. Holds source
 * ids and level names only (never item content), as `id=level` entries. Every source shows everything until
 * changed; with the preview off nothing opens this file.
 */
internal class SharedPreferencesContentLevelStore(context: Context) : ContentLevelStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): Map<String, String> =
        runCatching { decodeContentLevelEntries(preferences.getStringSet(KEY_LEVELS, emptySet()).orEmpty()) }
            .getOrDefault(emptyMap())

    override fun write(levels: Map<String, String>) {
        preferences.edit().putStringSet(KEY_LEVELS, encodeContentLevelEntries(levels)).apply()
    }

    private companion object {
        // Shared with SharedPreferencesDisabledSourcesStore: one file for per-install source choices.
        const val PREFERENCES_NAME = "riffle_workspace_sources"
        const val KEY_LEVELS = "content_levels"
    }
}

/** `id=level` entries; the level never contains `=`, so the last one splits. Malformed entries are dropped. */
internal fun decodeContentLevelEntries(entries: Set<String>): Map<String, String> =
    entries
        .mapNotNull { entry ->
            val split = entry.lastIndexOf('=')
            if (split <= 0 || split == entry.lastIndex) {
                null
            } else {
                entry.substring(0, split) to entry.substring(split + 1)
            }
        }.toMap()

internal fun encodeContentLevelEntries(levels: Map<String, String>): Set<String> =
    levels.entries.map { (id, level) -> "$id=$level" }.toSet()
