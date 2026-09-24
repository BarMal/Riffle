package com.riffle.app.launcher

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

private val Context.homeLayoutDataStore by preferencesDataStore(
    name = "riffle_home_layout",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, HomeLayoutDataStoreKeys.PREFERENCES_NAME))
    },
)

/**
 * The durable home layout set: one JSON blob in DataStore, read and written with suspend calls only.
 *
 * Nothing on the UI path talks to this directly -- [HomeLayoutRepositories] wraps it in a
 * [WriteBehindHomeLayoutRepository] that serves reads from memory and writes here on [Dispatchers.IO].
 */
internal class DataStoreHomeLayoutStore(context: Context) {
    private val dataStore = context.homeLayoutDataStore

    suspend fun read(): HomeLayoutSet? =
        dataStore.data.first()[HomeLayoutDataStoreKeys.homeLayout]
            ?.let { value -> runCatching { decodeHomeLayoutSet(value) }.getOrNull() }

    suspend fun write(layoutSet: HomeLayoutSet) {
        val encoded = encodeHomeLayoutSet(layoutSet)
        dataStore.edit { preferences ->
            preferences[HomeLayoutDataStoreKeys.homeLayout] = encoded
        }
    }
}

/**
 * One home layout repository per process, so every activity and view model instance shares the same
 * in-memory layout set (and the same pending write) instead of re-reading a store that may lag it.
 */
internal object HomeLayoutRepositories {
    @Volatile
    private var instance: WriteBehindHomeLayoutRepository? = null

    fun writeBehind(context: Context): WriteBehindHomeLayoutRepository =
        instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { repository -> instance = repository }
        }

    private fun create(context: Context): WriteBehindHomeLayoutRepository {
        val store = DataStoreHomeLayoutStore(context)
        return WriteBehindHomeLayoutRepository(
            initialLayoutSet = loadHomeLayoutSetAtStartup(store),
            persist = { layoutSet -> store.write(layoutSet) },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            onWriteFailed = { failure -> Log.w(HOME_LAYOUT_STORE_LOG_TAG, "Home layout write failed", failure) },
        )
    }
}

private const val HOME_LAYOUT_STORE_LOG_TAG = "RiffleHomeLayout"

private object HomeLayoutDataStoreKeys {
    const val PREFERENCES_NAME = "riffle_home_layout"
    val homeLayout = stringPreferencesKey("home_layout")
}
