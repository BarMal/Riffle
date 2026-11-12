package com.riffle.app.launcher.stream

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import com.riffle.core.domain.launcher.stream.StreamLayoutReducer
import com.riffle.core.domain.launcher.stream.StreamLayouts
import com.riffle.core.domain.launcher.workspace.SourceSubscription
import com.riffle.core.domain.launcher.workspace.container.ContextChanges
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList

private val Context.streamLayoutDataStore by preferencesDataStore(name = "riffle_stream_layout")

/** Suspend storage for the Stream layouts; the DataStore-backed store implements it, tests fake it. */
internal interface StreamLayoutStorePort {
    suspend fun read(): StreamLayouts?

    suspend fun write(layouts: StreamLayouts)
}

/**
 * The durable Stream layouts: one JSON blob in its own DataStore file, apart from the settings and the workspace blobs,
 * so resetting or restoring either never touches how the Stream is arranged. A blob that cannot be decoded reads as
 * null.
 */
internal class DataStoreStreamLayoutStore(context: Context) : StreamLayoutStorePort {
    private val dataStore = context.streamLayoutDataStore

    override suspend fun read(): StreamLayouts? =
        dataStore.data.first()[StreamLayoutKeys.layouts]?.let { value -> decodeStreamLayouts(value) }

    override suspend fun write(layouts: StreamLayouts) {
        val encoded = encodeStreamLayouts(layouts)
        dataStore.edit { preferences -> preferences[StreamLayoutKeys.layouts] = encoded }
    }
}

private object StreamLayoutKeys {
    val layouts = stringPreferencesKey("stream_layouts")
}

/**
 * The synchronous view of the Stream layouts the UI draws from, over a suspend [StreamLayoutStorePort]. [layouts] is
 * the default until [initialize] has read the store, so the Stream always has something to draw. [update] is the only
 * way an edit happens: the held value is replaced at once, written in order on [scope] (latest wins) and observers are
 * told when an option changed (this is the [ContextChanges] the Stream provider re-evaluates on). A failing read
 * disables writing for this process, so a transient storage failure can never replace what is on disk.
 */
internal class CachedStreamLayoutRepository(
    private val store: StreamLayoutStorePort,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ContextChanges {
    private val state = MutableStateFlow(StreamLayouts())
    private val lock = Any()
    private val writeLock = Mutex()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile private var loaded = false

    @Volatile private var persistenceEnabled = true

    val layouts: StateFlow<StreamLayouts> = state.asStateFlow()

    suspend fun initialize() {
        if (loaded) return
        val read = runCatching { store.read() }.onFailure { if (it is CancellationException) throw it }
        if (read.isFailure) persistenceEnabled = false
        val stored = read.getOrNull()
        val edited =
            synchronized(lock) {
                if (loaded) return
                // An edit that raced the read is newer than what was stored for its posture; the rest stays.
                val before = state.value
                if (stored != null) state.value = StreamLayouts(stored.byPosture + before.byPosture)
                loaded = true
                before.byPosture.isNotEmpty()
            }
        if (stored != null) notifyOptionsChanged()
        if (edited && persistenceEnabled) write()
    }

    /** The layout of [deviceClass]. */
    fun layoutFor(deviceClass: HomeLayoutDeviceClass): StreamLayout = state.value.forPosture(deviceClass)

    /** Applies [action] to [deviceClass]'s layout; returns the layout it had before, for Undo. */
    fun update(
        deviceClass: HomeLayoutDeviceClass,
        action: StreamLayoutAction,
    ): StreamLayout {
        val before: StreamLayout
        val after: StreamLayout
        synchronized(lock) {
            before = state.value.forPosture(deviceClass)
            after = StreamLayoutReducer.reduce(before, action)
            if (after != before) state.value = state.value.with(deviceClass, after)
        }
        if (after != before) {
            if (after.options != before.options) notifyOptionsChanged()
            // Before the first read finishes the edit is only held: [initialize] merges it and writes.
            if (persistenceEnabled && loaded) scope.launch { write() }
        }
        return before
    }

    /** What is held, else what is stored (a suspend read for the backup flow); null when nothing was ever stored. */
    suspend fun currentOrStored(): StreamLayouts? =
        state.value.takeIf { loaded || it.byPosture.isNotEmpty() } ?: store.read()

    /** Replaces everything from a backup: written to the store and held at once. */
    suspend fun replaceFromBackup(restored: StreamLayouts) {
        store.write(restored)
        synchronized(lock) {
            state.value = restored
            loaded = true
        }
        notifyOptionsChanged()
    }

    override fun observe(onChange: () -> Unit): SourceSubscription {
        listeners += onChange
        return SourceSubscription { listeners -= onChange }
    }

    private fun notifyOptionsChanged() = listeners.forEach { it() }

    private suspend fun write() {
        writeLock.withLock {
            runCatching { store.write(state.value) }.onFailure { if (it is CancellationException) throw it }
        }
    }
}
