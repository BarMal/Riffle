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

    override fun isSetupCardDismissed(): Boolean =
        preferences.getBoolean(
            KEY_SETUP_CARD_DISMISSED,
            isFirstRunComplete(),
        )

    override fun setSetupCardDismissed() {
        preferences.edit()
            .putBoolean(KEY_SETUP_CARD_DISMISSED, true)
            .apply()
    }

    override fun isHomeRoleRequestPending(): Boolean = preferences.getBoolean(KEY_HOME_ROLE_REQUEST_PENDING, false)

    override fun setHomeRoleRequestPending(pending: Boolean) {
        preferences.edit()
            .putBoolean(KEY_HOME_ROLE_REQUEST_PENDING, pending)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "riffle_first_run"
        const val KEY_FIRST_RUN_COMPLETE = "first_run_complete"
        const val KEY_SETUP_CARD_DISMISSED = "setup_card_dismissed"
        const val KEY_HOME_ROLE_REQUEST_PENDING = "home_role_request_pending"
    }
}
