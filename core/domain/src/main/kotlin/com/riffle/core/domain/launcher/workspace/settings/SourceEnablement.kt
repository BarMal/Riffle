package com.riffle.core.domain.launcher.workspace.settings

import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceSubscription
import java.util.concurrent.CopyOnWriteArrayList

/** Told that [id] was turned on or off. May be called from any thread. */
fun interface SourceEnablementListener {
    fun onChanged(id: SourceId)
}

/**
 * Which sources the user turned off (Settings > Sources). Every source is on until turned off. Platform
 * neutral: the app persists it; the source registry consults it so a disabled source emits
 * [com.riffle.core.domain.launcher.workspace.SourceState.Off] without ever reading its repository.
 */
interface SourceEnablement {
    fun isEnabled(id: SourceId): Boolean

    fun disabledIds(): Set<SourceId>

    fun setEnabled(
        id: SourceId,
        enabled: Boolean,
    )

    fun observe(listener: SourceEnablementListener): SourceSubscription
}

/** Durable storage for the disabled source ids (strings, so the stored contract is the id text). */
interface DisabledSourcesStore {
    fun read(): Set<String>

    fun write(ids: Set<String>)
}

/**
 * [SourceEnablement] over a [DisabledSourcesStore]: read once, kept in memory, written on every change.
 * A store that fails to read means everything is on; one that fails to write keeps this process correct.
 */
class StoredSourceEnablement(private val store: DisabledSourcesStore) : SourceEnablement {
    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<SourceEnablementListener>()
    private var disabled: Set<SourceId> =
        runCatching { store.read() }.getOrDefault(emptySet()).filter { it.isNotBlank() }.map(::SourceId).toSet()

    override fun isEnabled(id: SourceId): Boolean = synchronized(lock) { id !in disabled }

    override fun disabledIds(): Set<SourceId> = synchronized(lock) { disabled }

    override fun setEnabled(
        id: SourceId,
        enabled: Boolean,
    ) {
        val changed =
            synchronized(lock) {
                val next = if (enabled) disabled - id else disabled + id
                (next != disabled).also {
                    disabled = next
                    if (it) runCatching { store.write(next.map { source -> source.value }.toSet()) }
                }
            }
        if (changed) listeners.forEach { it.onChanged(id) }
    }

    override fun observe(listener: SourceEnablementListener): SourceSubscription {
        listeners += listener
        return SourceSubscription { listeners -= listener }
    }
}

/** An in-memory [DisabledSourcesStore] for tests and previews. */
class InMemoryDisabledSourcesStore(var stored: Set<String> = emptySet()) : DisabledSourcesStore {
    override fun read(): Set<String> = stored

    override fun write(ids: Set<String>) {
        stored = ids
    }
}
