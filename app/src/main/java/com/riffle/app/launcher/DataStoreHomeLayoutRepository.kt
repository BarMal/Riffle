package com.riffle.app.launcher

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.homeLayoutDataStore by preferencesDataStore(
    name = "riffle_home_layout",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, HomeLayoutDataStoreKeys.PREFERENCES_NAME))
    },
)

class DataStoreHomeLayoutRepository(context: Context) : HomeLayoutRepository {
    private val dataStore = context.homeLayoutDataStore

    override fun loadHomeLayout(): HomeLayout? = loadHomeLayoutSet()?.activeLayout

    override fun saveHomeLayout(layout: HomeLayout) {
        val layoutSet =
            loadHomeLayoutSet()
                ?.withActiveLayout(layout)
                ?: HomeLayoutSet.fromLayout(layout)

        saveHomeLayoutSet(layoutSet)
    }

    override fun loadHomeLayoutSet(): HomeLayoutSet? =
        readString(HomeLayoutDataStoreKeys.homeLayout)
            ?.let { value -> runCatching { decodeHomeLayoutSet(value) }.getOrNull() }

    override fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) {
        writeString(
            key = HomeLayoutDataStoreKeys.homeLayout,
            value = encodeHomeLayoutSet(layoutSet),
        )
    }

    private fun readString(key: Preferences.Key<String>): String? = runBlocking { dataStore.data.first()[key] }

    private fun writeString(
        key: Preferences.Key<String>,
        value: String,
    ) {
        runBlocking {
            dataStore.edit { preferences ->
                preferences[key] = value
            }
        }
    }
}

private object HomeLayoutDataStoreKeys {
    const val PREFERENCES_NAME = "riffle_home_layout"
    val homeLayout = stringPreferencesKey("home_layout")
}
