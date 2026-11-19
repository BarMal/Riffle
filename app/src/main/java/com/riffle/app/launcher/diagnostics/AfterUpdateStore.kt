package com.riffle.app.launcher.diagnostics

import android.content.Context
import com.riffle.app.launcher.toJson
import com.riffle.app.launcher.toStoredValue
import com.riffle.core.domain.launcher.diagnostics.AfterUpdateCodec
import com.riffle.core.domain.launcher.diagnostics.AfterUpdateState
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Where the after-update diagnostics live (Settings > About > After-update starts). Device local, like
 * the onboarding state: in its own preferences file (`riffle_after_update`), never in the launcher settings or the
 * backup document, and excluded from Auto Backup. Reads never throw; nothing stored reads as an empty state.
 */
internal interface AfterUpdateStorePort {
    fun read(): AfterUpdateState

    fun write(state: AfterUpdateState)
}

/** For tests: holds the state in memory. */
internal class InMemoryAfterUpdateStore(
    var stored: AfterUpdateState = AfterUpdateState(),
) : AfterUpdateStorePort {
    var writes: Int = 0
        private set

    override fun read(): AfterUpdateState = stored

    override fun write(state: AfterUpdateState) {
        stored = state
        writes++
    }
}

/** The stored text of [state]: [AfterUpdateCodec]'s tree as JSON. */
internal fun encodeAfterUpdateState(state: AfterUpdateState): String =
    AfterUpdateCodec.encode(state).toJson().toString()

/** Never throws: null, malformed or non-object text reads as an empty state. */
internal fun decodeAfterUpdateState(text: String?): AfterUpdateState {
    val parsed = text?.let { runCatching { JSONTokener(it).nextValue() }.getOrNull() } as? JSONObject
    return AfterUpdateCodec.decode(parsed?.toStoredValue())
}

/** The preferences-backed store; the file opens on the first read or write, which callers make off the main thread. */
internal class SharedPreferencesAfterUpdateStore(
    context: Context,
) : AfterUpdateStorePort {
    private val appContext = context.applicationContext
    private val preferences by lazy { appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE) }

    override fun read(): AfterUpdateState =
        decodeAfterUpdateState(runCatching { preferences.getString(KEY_STATE, null) }.getOrNull())

    override fun write(state: AfterUpdateState) {
        runCatching { preferences.edit().putString(KEY_STATE, encodeAfterUpdateState(state)).apply() }
    }

    private companion object {
        const val PREFERENCES_NAME = "riffle_after_update"
        const val KEY_STATE = "state"
    }
}
