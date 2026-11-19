package com.riffle.app.launcher.pairs

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.pairs.AppPair
import com.riffle.core.domain.launcher.pairs.AppPairs
import com.riffle.core.domain.launcher.pairs.AppPairsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * The pairs of the Pairs stack over an [AppPairsStore]: read once, held in memory (the Stream observes [pairs])
 * and written after every change. Every edit is one pure rule of [AppPairs] applied to the list as it is now, so
 * the screen and the migration of the Dock's pairs can both edit it. Thread-safe; a read or a write blocks, so the
 * first use is [ensureLoaded] off the main thread.
 */
@Suppress("TooManyFunctions") // One small function per edit the stack offers.
internal class AppPairsRepository(
    private val store: AppPairsStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val lock = Any()
    private var loaded = false
    private val state = MutableStateFlow<List<AppPair>>(emptyList())

    /** The pairs, front first; empty until [ensureLoaded]. */
    val pairs: StateFlow<List<AppPair>> = state.asStateFlow()

    /** Reads the stored pairs (once). */
    fun ensureLoaded() {
        synchronized(lock) { loadLocked() }
    }

    private fun loadLocked() {
        if (loaded) return
        loaded = true
        state.value = store.read()
    }

    /** Applies [rule] to the pairs; writes and publishes only when it changed them. Returns the list before. */
    fun edit(rule: (List<AppPair>) -> List<AppPair>): List<AppPair> =
        synchronized(lock) {
            loadLocked()
            val before = state.value
            val after = rule(before)
            if (after != before) {
                store.write(after)
                state.value = after
            }
            before
        }

    /** A pair of [first] and [second] at the front (an existing one of that pair comes forward instead). */
    fun add(
        first: AppIdentity,
        second: AppIdentity,
    ): List<AppPair> = edit { AppPairs.add(it, AppPair(newId(), first, second, clock())) }

    fun remove(id: String): List<AppPair> = edit { AppPairs.remove(it, id) }

    /** Undo of [remove]: [pair] at [index] again. */
    fun restore(
        pair: AppPair,
        index: Int,
    ) = edit { AppPairs.restore(it, pair, index) }

    fun swap(id: String) = edit { AppPairs.swap(it, id) }

    fun changeFirst(
        id: String,
        app: AppIdentity,
    ) = edit { AppPairs.changeFirst(it, id, app) }

    fun changeSecond(
        id: String,
        app: AppIdentity,
    ) = edit { AppPairs.changeSecond(it, id, app) }

    /** [id] was opened now: it comes to the front. */
    fun opened(id: String) = edit { AppPairs.opened(it, id, clock()) }

    /** Pairs whose apps are not among [installed] go. */
    fun pruneUninstalled(installed: Set<AppIdentity>) = edit { AppPairs.pruneUninstalled(it, installed) }

    /** The Dock's former app pairs, added behind what is there (see `LegacyDockPairs`). Idempotent. */
    fun addMigrated(migrated: List<AppPair>) = edit { AppPairs.addMigrated(it, migrated) }

    /** A backup's pairs replace the current ones. */
    fun replaceFromBackup(pairs: List<AppPair>) = edit { AppPairs.addMigrated(emptyList(), pairs) }

    /** The pairs as they are now, loading them if need be (for a backup export). */
    fun current(): List<AppPair> = synchronized(lock) { loadLocked().let { state.value } }
}
