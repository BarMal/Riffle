package com.riffle.app.launcher

import android.content.Context
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository

class SharedPreferencesHomeLayoutRepository(context: Context) : HomeLayoutRepository {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun loadHomeLayout(): HomeLayout? =
        preferences.getString(KEY_HOME_LAYOUT, null)
            ?.let { value -> runCatching { decodeHomeLayout(value) }.getOrNull() }

    override fun saveHomeLayout(layout: HomeLayout) {
        preferences.edit()
            .putString(KEY_HOME_LAYOUT, encodeHomeLayout(layout))
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "riffle_home_layout"
        const val KEY_HOME_LAYOUT = "home_layout"
    }
}
