package com.riffle.app.launcher

import android.content.Context

class SharedPreferencesFirstRunRepository(
    context: Context,
) : FirstRunRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun isFirstRunComplete(): Boolean = preferences.getBoolean(KEY_FIRST_RUN_COMPLETE, false)

    override fun setFirstRunComplete() {
        preferences.edit()
            .putBoolean(KEY_FIRST_RUN_COMPLETE, true)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "riffle_first_run"
        const val KEY_FIRST_RUN_COMPLETE = "first_run_complete"
    }
}
