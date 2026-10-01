package com.riffle.app.launcher.exclusions

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import kotlinx.coroutines.flow.first

private val Context.exclusionDataStore by preferencesDataStore(name = "riffle_exclusions")

/** Suspend storage for the per-layout exclusion rules; the DataStore-backed store implements it, tests fake it. */
internal interface ExclusionStorePort {
    suspend fun read(): LayoutExclusionRules?

    suspend fun write(rules: LayoutExclusionRules)
}

/**
 * The durable exclusion rules: one JSON blob in its own DataStore file, deliberately apart from the workspace
 * blob so resetting or restoring workspaces can never un-hide anything. A blob that cannot be decoded reads
 * as null rather than throwing.
 */
internal class DataStoreExclusionStore(context: Context) : ExclusionStorePort {
    private val dataStore = context.exclusionDataStore

    override suspend fun read(): LayoutExclusionRules? =
        dataStore.data.first()[ExclusionDataStoreKeys.rules]?.let { value -> decodeExclusionRules(value) }

    override suspend fun write(rules: LayoutExclusionRules) {
        val encoded = encodeExclusionRules(rules)
        dataStore.edit { preferences -> preferences[ExclusionDataStoreKeys.rules] = encoded }
    }
}

private object ExclusionDataStoreKeys {
    val rules = stringPreferencesKey("source_exclusions")
}
