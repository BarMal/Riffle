package com.riffle.app.launcher

import android.content.Context
import com.riffle.core.domain.launcher.ShellDestination

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

    override fun isSetupCardDismissed(): Boolean {
        if (preferences.contains(KEY_SETUP_CARD_DISMISSED)) {
            return preferences.getBoolean(KEY_SETUP_CARD_DISMISSED, false)
        }

        return isFirstRunComplete().also { wasFirstRunComplete ->
            if (wasFirstRunComplete) {
                setSetupCardDismissed()
            }
        }
    }

    override fun setSetupCardDismissed() {
        preferences.edit()
            .putBoolean(KEY_SETUP_CARD_DISMISSED, true)
            .apply()
    }

    override fun isHomeRoleRequestPending(): Boolean =
        homeRoleRequestContext() != null || preferences.getBoolean(KEY_HOME_ROLE_REQUEST_PENDING, false)

    override fun setHomeRoleRequestPending(pending: Boolean) {
        preferences.edit()
            .putBoolean(KEY_HOME_ROLE_REQUEST_PENDING, pending)
            .apply()
    }

    override fun homeRoleRequestDestination(): ShellDestination? =
        preferences
            .getString(KEY_HOME_ROLE_REQUEST_DESTINATION, null)
            ?.let { storedValue ->
                ShellDestination.entries.firstOrNull { destination -> destination.name == storedValue }
            }

    override fun setHomeRoleRequestDestination(destination: ShellDestination?) {
        preferences.edit()
            .apply {
                if (destination == null) {
                    remove(KEY_HOME_ROLE_REQUEST_DESTINATION)
                } else {
                    putString(KEY_HOME_ROLE_REQUEST_DESTINATION, destination.name)
                }
            }.apply()
    }

    override fun homeRoleRequestContext(): HomeRoleRequestContext? =
        preferences
            .getString(KEY_HOME_ROLE_REQUEST_CONTEXT, null)
            ?.let { storedValue ->
                ShellDestination.entries
                    .firstOrNull { destination -> destination.name == storedValue }
                    ?.let(::HomeRoleRequestContext)
            }

    override fun setHomeRoleRequestContext(context: HomeRoleRequestContext?) {
        preferences.edit()
            .apply {
                if (context == null) {
                    remove(KEY_HOME_ROLE_REQUEST_CONTEXT)
                } else {
                    putString(KEY_HOME_ROLE_REQUEST_CONTEXT, context.destination.name)
                }
                remove(KEY_HOME_ROLE_REQUEST_PENDING)
                remove(KEY_HOME_ROLE_REQUEST_DESTINATION)
            }.apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "riffle_first_run"
        const val KEY_FIRST_RUN_COMPLETE = "first_run_complete"
        const val KEY_SETUP_CARD_DISMISSED = "setup_card_dismissed"
        const val KEY_HOME_ROLE_REQUEST_PENDING = "home_role_request_pending"
        const val KEY_HOME_ROLE_REQUEST_DESTINATION = "home_role_request_destination"
        const val KEY_HOME_ROLE_REQUEST_CONTEXT = "home_role_request_context"
    }
}
