package com.riffle.app.launcher.ics

import com.riffle.app.launcher.sources.SourceChangeSource
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsEvent
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedId
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList

/** The parsed events of one feed as last refreshed. Never the raw body: that is dropped after parsing. */
internal data class CachedIcsFeed(
    val events: List<IcsEvent>,
    val fetchedAtEpochMillis: Long,
)

/** The persisted text of the two stores. Both are device-local; see `DataStoreIcsStore`. */
internal class IcsStoredText(
    val settings: String?,
    val cache: String?,
)

/** Suspend persistence behind [CachedIcsFeedRepository]; the DataStore adapter is the only implementation. */
internal interface IcsStorePort {
    suspend fun read(): IcsStoredText

    suspend fun writeSettings(json: String)

    suspend fun writeCache(json: String)
}

/**
 * The synchronous view of the ICS feed list and its parsed-event cache that the source, the refresh and the
 * settings page read, over a suspend [IcsStorePort]. Loading starts when this is created (nothing else does
 * any I/O): until it has finished [isLoaded] is false and [settings] is empty, so a source reads `Loading`
 * and nothing is fetched. Edits made before then are refused (null), so one can never be lost into an
 * unloaded copy.
 *
 * A read that fails disables writing for this process, so a transient storage failure can never replace a
 * feed list that exists on disk. Writes are serialised on [scope], latest value wins. [changes] fires for any
 * change (load, edit, refreshed cache) and is what makes the source `LIVE`.
 *
 * The feed URLs are secrets: they exist only here and in the store, never in a log, the backup or the UI.
 */
internal class CachedIcsFeedRepository(
    private val store: IcsStorePort,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : SourceChangeSource {
    private val lock = Any()
    private val writeLock = Mutex()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val mutableSettings = MutableStateFlow(IcsFeedSettings())
    private val mutableLoaded = MutableStateFlow(false)

    @Volatile
    private var cache: Map<IcsFeedId, CachedIcsFeed> = emptyMap()

    @Volatile
    private var persistenceEnabled = true

    /** The current feed list; empty until loaded. */
    val settingsFlow: StateFlow<IcsFeedSettings> = mutableSettings.asStateFlow()

    val loadedFlow: StateFlow<Boolean> = mutableLoaded.asStateFlow()

    val isLoaded: Boolean get() = mutableLoaded.value

    init {
        scope.launch { load() }
    }

    fun settings(): IcsFeedSettings = mutableSettings.value

    fun cached(feedId: IcsFeedId): CachedIcsFeed? = cache[feedId]

    /** Applies [transform] to the feed list and persists it; null (nothing changed) before the load finished. */
    fun updateSettings(transform: (IcsFeedSettings) -> IcsFeedSettings?): IcsFeedSettings? {
        val next =
            synchronized(lock) {
                val current = mutableSettings.value
                val candidate = if (isLoaded) transform(current) else null
                candidate?.takeIf { it != current }?.also { mutableSettings.value = it }
            }
        if (next != null) {
            changed()
            if (persistenceEnabled) scope.launch { writeSettings() }
        }
        return next
    }

    /** Replaces one feed's parsed events (a refresh result). */
    fun replaceCache(
        feedId: IcsFeedId,
        feed: CachedIcsFeed,
    ) {
        synchronized(lock) { cache = cache + (feedId to feed) }
        changed()
        if (persistenceEnabled) scope.launch { writeCache() }
    }

    /** Forgets one feed's events; a removed or disabled feed never leaves content behind. */
    fun clearCache(feedId: IcsFeedId) {
        val removed = synchronized(lock) { (feedId in cache).also { if (it) cache = cache - feedId } }
        if (removed) {
            changed()
            if (persistenceEnabled) scope.launch { writeCache() }
        }
    }

    override fun observe(onChanged: () -> Unit): () -> Unit {
        listeners += onChanged
        return { listeners -= onChanged }
    }

    private suspend fun load() {
        val read = runCatching { store.read() }.onFailure { if (it is CancellationException) throw it }
        if (read.isFailure) persistenceEnabled = false
        val text = read.getOrNull()
        val stored = text?.settings?.let(IcsJsonCodecs::decodeSettings) ?: IcsFeedSettings()
        val storedCache = text?.cache?.let(IcsJsonCodecs::decodeCache).orEmpty()
        synchronized(lock) {
            mutableSettings.value = stored
            // A feed that is no longer configured never keeps content.
            cache = storedCache.filterKeys { id -> stored.feeds.any { it.id == id } }
        }
        mutableLoaded.value = true
        changed()
    }

    private suspend fun writeSettings() {
        writeLock.withLock {
            val json = IcsJsonCodecs.encodeSettings(mutableSettings.value)
            runCatching { store.writeSettings(json) }.onFailure { if (it is CancellationException) throw it }
        }
    }

    private suspend fun writeCache() {
        writeLock.withLock {
            val json = IcsJsonCodecs.encodeCache(cache)
            runCatching { store.writeCache(json) }.onFailure { if (it is CancellationException) throw it }
        }
    }

    private fun changed() {
        listeners.forEach { it() }
    }
}
