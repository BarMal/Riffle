package com.riffle.app.launcher.pairs

import android.content.Context
import android.content.SharedPreferences
import com.riffle.app.launcher.toJson
import com.riffle.app.launcher.toStoredValue
import com.riffle.core.domain.launcher.pairs.AppPair
import com.riffle.core.domain.launcher.pairs.AppPairsCodec
import com.riffle.core.domain.launcher.pairs.AppPairsStore
import org.json.JSONObject
import org.json.JSONTokener

/**
 * The pairs ([AppPairsCodec]: ids, the two apps and the time last opened, nothing about windows) as one JSON string
 * in their own small preferences file, apart from the settings, the Stream pair and the workspace. An unreadable
 * value reads as an empty list. They are part of a backup (see `AndroidWorkspaceBackupPort`): they are what the owner
 * made, as the Dock's pinned items are.
 */
internal class SharedPreferencesAppPairsStore(
    private val preferences: SharedPreferences,
) : AppPairsStore {
    constructor(context: Context) : this(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE))

    override fun read(): List<AppPair> =
        runCatching {
            val text = preferences.getString(KEY_PAIRS, null) ?: return emptyList()
            val parsed = JSONTokener(text).nextValue() as? JSONObject
            AppPairsCodec.decode(parsed?.toStoredValue())
        }.getOrDefault(emptyList())

    override fun write(pairs: List<AppPair>) {
        val text = AppPairsCodec.encode(pairs).toJson().toString()
        // Committed, not applied: a migration writes the pairs before it lets go of the Dock's pairs.
        preferences.edit().putString(KEY_PAIRS, text).commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "riffle_app_pairs"
        const val KEY_PAIRS = "pairs"
    }
}
