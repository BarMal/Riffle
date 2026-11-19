package com.riffle.app.launcher.savedhome

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.riffle.core.domain.launcher.home.saved.SavedHomeLayouts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The saved Home layouts (Settings > Home > Home layouts). [layouts] is the value in memory; [update] changes it and
 * returns only when the change is durable, so a caller that must have a safety copy on disk before it replaces Home
 * can await it. Writes are serialised.
 */
interface SavedHomeLayoutsStore {
    val layouts: StateFlow<SavedHomeLayouts>

    /** Loads what is stored once; a later call is cheap. Safe to call from any thread. */
    suspend fun load(): SavedHomeLayouts

    /** Applies [transform] to the loaded value, writes the result and returns it. Throws if the write fails. */
    suspend fun update(transform: (SavedHomeLayouts) -> SavedHomeLayouts): SavedHomeLayouts

    /** Replaces everything (a backup import). */
    suspend fun replaceAll(layouts: SavedHomeLayouts) {
        update { layouts }
    }
}

/** Keeps the layouts in memory only (tests and the default of a view model without platform storage). */
class InMemorySavedHomeLayoutsStore(initial: SavedHomeLayouts = SavedHomeLayouts()) : SavedHomeLayoutsStore {
    private val state = MutableStateFlow(initial)
    override val layouts: StateFlow<SavedHomeLayouts> = state.asStateFlow()

    override suspend fun load(): SavedHomeLayouts = state.value

    override suspend fun update(transform: (SavedHomeLayouts) -> SavedHomeLayouts): SavedHomeLayouts =
        transform(state.value).also { state.value = it }
}

private val Context.savedHomeLayoutsDataStore by preferencesDataStore(name = "riffle_saved_home_layouts")

/**
 * One JSON blob in its own DataStore file, apart from the Home pool, the layout set and the settings (so resetting or
 * restoring those cannot lose a saved layout). Included in Android's backup the same way the Home layout is: it holds
 * no widget host id and no content. A blob written by a newer build is decoded as far as this build understands it and
 * kept once under its own key before this build first overwrites it.
 */
internal class DataStoreSavedHomeLayoutsStore(context: Context) : SavedHomeLayoutsStore {
    private val dataStore = context.applicationContext.savedHomeLayoutsDataStore
    private val mutex = Mutex()
    private val state = MutableStateFlow(SavedHomeLayouts())
    private var loaded = false
    private var newerBlobKept = false

    override val layouts: StateFlow<SavedHomeLayouts> = state.asStateFlow()

    override suspend fun load(): SavedHomeLayouts = mutex.withLock { ensureLoaded() }

    private suspend fun ensureLoaded(): SavedHomeLayouts {
        if (!loaded) {
            val raw = dataStore.data.first()[Keys.layouts]
            val decoded = raw?.let(::decodeSavedHomeLayouts)
            newerBlobKept = decoded?.newerThanThisBuild != true
            state.value = decoded?.layouts ?: SavedHomeLayouts()
            loaded = true
        }
        return state.value
    }

    override suspend fun update(transform: (SavedHomeLayouts) -> SavedHomeLayouts): SavedHomeLayouts =
        mutex.withLock {
            val next = transform(ensureLoaded())
            val encoded = encodeSavedHomeLayouts(next)
            dataStore.edit { preferences ->
                val current = preferences[Keys.layouts]
                if (!newerBlobKept && current != null && preferences[Keys.newerBuildCopy] == null) {
                    preferences[Keys.newerBuildCopy] = current
                }
                preferences[Keys.layouts] = encoded
            }
            newerBlobKept = true
            state.value = next
            next
        }

    private object Keys {
        val layouts = stringPreferencesKey("saved_home_layouts")
        val newerBuildCopy = stringPreferencesKey("saved_home_layouts_newer_build_copy")
    }
}
