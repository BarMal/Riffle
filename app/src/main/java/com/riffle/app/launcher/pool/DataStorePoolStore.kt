package com.riffle.app.launcher.pool

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.riffle.core.domain.launcher.workspace.pool.PoolStoreState
import kotlinx.coroutines.flow.first

private val Context.poolDataStore by preferencesDataStore(name = "riffle_pool")

/**
 * The durable placed-items pool: one JSON blob in its own DataStore file, deliberately apart from the workspace
 * blob and from `HomeLayoutSet`, so resetting or restoring either can never lose the other. A blob that cannot
 * be decoded reads as null (nothing stored) rather than throwing.
 */
internal class DataStorePoolStore(context: Context) : PoolStorePort {
    private val dataStore = context.poolDataStore

    override suspend fun read(): PoolStoreState? =
        dataStore.data.first()[PoolDataStoreKeys.pool]?.let { value -> decodePoolStore(value) }

    override suspend fun write(state: PoolStoreState) {
        val encoded = encodePoolStore(state)
        dataStore.edit { preferences -> preferences[PoolDataStoreKeys.pool] = encoded }
    }
}

private object PoolDataStoreKeys {
    val pool = stringPreferencesKey("pool")
}
