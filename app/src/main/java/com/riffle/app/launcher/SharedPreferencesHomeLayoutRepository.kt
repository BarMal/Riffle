package com.riffle.app.launcher

import android.content.Context
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet

class SharedPreferencesHomeLayoutRepository(context: Context) : HomeLayoutRepository {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun loadHomeLayout(): HomeLayout? = loadHomeLayoutSet()?.activeLayout

    override fun saveHomeLayout(layout: HomeLayout) {
        val layoutSet =
            loadHomeLayoutSet()
                ?.withActiveLayout(layout)
                ?: HomeLayoutSet.fromLayout(layout)

        saveHomeLayoutSet(layoutSet)
    }

    override fun loadHomeLayoutSet(): HomeLayoutSet? =
        preferences.getString(KEY_HOME_LAYOUT, null)
            ?.let { value -> runCatching { decodeHomeLayoutSet(value) }.getOrNull() }

    override fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) {
        preferences.edit()
            .putString(KEY_HOME_LAYOUT, encodeHomeLayoutSet(layoutSet))
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "riffle_home_layout"
        const val KEY_HOME_LAYOUT = "home_layout"
    }
}
