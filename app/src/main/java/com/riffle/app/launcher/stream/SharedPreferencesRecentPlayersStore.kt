package com.riffle.app.launcher.stream

import android.content.Context
import android.content.SharedPreferences
import com.riffle.app.launcher.toJson
import com.riffle.app.launcher.toStoredValue
import com.riffle.core.domain.launcher.stream.RecentPlayer
import com.riffle.core.domain.launcher.stream.RecentPlayersCodec
import com.riffle.core.domain.launcher.stream.RecentPlayersStore
import org.json.JSONObject
import org.json.JSONTokener

/**
 * The recent players ([RecentPlayersCodec]: package, profile and time, never what was played) as one JSON string in
 * their own tiny preferences file, apart from the settings, the Stream layout and the workspace. Not part of a backup:
 * it describes this device's use. An unreadable value reads as an empty list.
 */
internal class SharedPreferencesRecentPlayersStore(
    private val preferences: SharedPreferences,
) : RecentPlayersStore {
    constructor(context: Context) : this(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE))

    override fun read(): List<RecentPlayer> =
        runCatching {
            val text = preferences.getString(KEY_PLAYERS, null) ?: return emptyList()
            val parsed = JSONTokener(text).nextValue() as? JSONObject
            RecentPlayersCodec.decode(parsed?.toStoredValue())
        }.getOrDefault(emptyList())

    override fun write(players: List<RecentPlayer>) {
        val text = RecentPlayersCodec.encode(players).toJson().toString()
        preferences.edit().putString(KEY_PLAYERS, text).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "riffle_recent_players"
        const val KEY_PLAYERS = "players"
    }
}
