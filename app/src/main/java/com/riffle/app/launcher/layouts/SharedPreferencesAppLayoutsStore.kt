package com.riffle.app.launcher.layouts

import android.content.Context
import android.content.SharedPreferences
import com.riffle.app.launcher.toJson
import com.riffle.app.launcher.toStoredValue
import com.riffle.core.domain.launcher.layouts.AppLayout
import com.riffle.core.domain.launcher.layouts.AppLayoutsCodec
import com.riffle.core.domain.launcher.layouts.AppLayoutsStore
import org.json.JSONObject
import org.json.JSONTokener

/**
 * The layouts ([AppLayoutsCodec]: ids, the two apps and the time last opened, nothing about windows) as one JSON string
 * in their own small preferences file, apart from the settings, the Stream layout and the workspace. An unreadable
 * value reads as an empty list. They are part of a backup (see `AndroidWorkspaceBackupPort`): they are what the owner
 * made, as the Dock's pinned items are.
 */
internal class SharedPreferencesAppLayoutsStore(
    private val preferences: SharedPreferences,
) : AppLayoutsStore {
    constructor(context: Context) : this(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE))

    override fun read(): List<AppLayout> =
        runCatching {
            val text = preferences.getString(KEY_LAYOUTS, null) ?: return emptyList()
            val parsed = JSONTokener(text).nextValue() as? JSONObject
            AppLayoutsCodec.decode(parsed?.toStoredValue())
        }.getOrDefault(emptyList())

    override fun write(layouts: List<AppLayout>) {
        val text = AppLayoutsCodec.encode(layouts).toJson().toString()
        // Committed, not applied: a migration writes the layouts before it lets go of the Dock's pairs.
        preferences.edit().putString(KEY_LAYOUTS, text).commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "riffle_app_layouts"
        const val KEY_LAYOUTS = "layouts"
    }
}
