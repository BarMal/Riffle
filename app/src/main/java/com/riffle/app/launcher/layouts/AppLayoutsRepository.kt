package com.riffle.app.launcher.layouts

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.layouts.AppLayout
import com.riffle.core.domain.launcher.layouts.AppLayouts
import com.riffle.core.domain.launcher.layouts.AppLayoutsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * The layouts of the Layouts stack over an [AppLayoutsStore]: read once, held in memory (the Stream observes [layouts])
 * and written after every change. Every edit is one pure rule of [AppLayouts] applied to the list as it is now, so
 * the screen and the migration of the Dock's pairs can both edit it. Thread-safe; a read or a write blocks, so the
 * first use is [ensureLoaded] off the main thread.
 */
@Suppress("TooManyFunctions") // One small function per edit the stack offers.
internal class AppLayoutsRepository(
    private val store: AppLayoutsStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val lock = Any()
    private var loaded = false
    private val state = MutableStateFlow<List<AppLayout>>(emptyList())

    /** The layouts, front first; empty until [ensureLoaded]. */
    val layouts: StateFlow<List<AppLayout>> = state.asStateFlow()

    /** Reads the stored layouts (once). */
    fun ensureLoaded() {
        synchronized(lock) { loadLocked() }
    }

    private fun loadLocked() {
        if (loaded) return
        loaded = true
        state.value = store.read()
    }

    /** Applies [rule] to the layouts; writes and publishes only when it changed them. Returns the list before. */
    fun edit(rule: (List<AppLayout>) -> List<AppLayout>): List<AppLayout> =
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

    /** A layout of [first] and [second] at the front (an existing one of that pair comes forward instead). */
    fun add(
        first: AppIdentity,
        second: AppIdentity,
    ): List<AppLayout> = edit { AppLayouts.add(it, AppLayout(newId(), first, second, clock())) }

    fun remove(id: String): List<AppLayout> = edit { AppLayouts.remove(it, id) }

    /** Undo of [remove]: [layout] at [index] again. */
    fun restore(
        layout: AppLayout,
        index: Int,
    ) = edit { AppLayouts.restore(it, layout, index) }

    fun swap(id: String) = edit { AppLayouts.swap(it, id) }

    fun changeFirst(
        id: String,
        app: AppIdentity,
    ) = edit { AppLayouts.changeFirst(it, id, app) }

    fun changeSecond(
        id: String,
        app: AppIdentity,
    ) = edit { AppLayouts.changeSecond(it, id, app) }

    /** [id] was opened now: it comes to the front. */
    fun opened(id: String) = edit { AppLayouts.opened(it, id, clock()) }

    /** Layouts whose apps are not among [installed] go. */
    fun pruneUninstalled(installed: Set<AppIdentity>) = edit { AppLayouts.pruneUninstalled(it, installed) }

    /** The Dock's former app pairs, added behind what is there (see `LegacyDockPairs`). Idempotent. */
    fun addMigrated(migrated: List<AppLayout>) = edit { AppLayouts.addMigrated(it, migrated) }

    /** A backup's layouts replace the current ones. */
    fun replaceFromBackup(layouts: List<AppLayout>) = edit { AppLayouts.addMigrated(emptyList(), layouts) }

    /** The layouts as they are now, loading them if need be (for a backup export). */
    fun current(): List<AppLayout> = synchronized(lock) { loadLocked().let { state.value } }
}
