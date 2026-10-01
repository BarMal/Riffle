package com.riffle.app.launcher

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import kotlinx.coroutines.flow.first

private val Context.workspaceDataStore by preferencesDataStore(name = "riffle_workspaces")

/**
 * The durable workspace set: one JSON blob in its own DataStore, read and written with suspend calls
 * only. Reached through [CachedWorkspaceRepository], which runs WorkspaceMigration.ensureMigrated on a
 * null or partial read. A blob that cannot be decoded reads as null rather than throwing.
 */
internal class DataStoreWorkspaceStore(context: Context) : WorkspaceStorePort {
    private val dataStore = context.workspaceDataStore

    override suspend fun read(): WorkspaceSet? =
        dataStore.data.first()[WorkspaceDataStoreKeys.workspaces]
            ?.let { value -> decodeWorkspaceSet(value) }

    override suspend fun write(set: WorkspaceSet) {
        val encoded = encodeWorkspaceSet(set)
        dataStore.edit { preferences ->
            preferences[WorkspaceDataStoreKeys.workspaces] = encoded
        }
    }
}

private object WorkspaceDataStoreKeys {
    val workspaces = stringPreferencesKey("workspaces")
}
