package com.riffle.app.launcher.exclusions

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.NotificationHideRule
import com.riffle.core.domain.launcher.workspace.SourceSubscription
import com.riffle.core.domain.launcher.workspace.container.ContextChanges
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMigration
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleSet
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The synchronous view of the per-layout exclusion rules that lens evaluation reads, over a suspend
 * [ExclusionStorePort]. [rules] is empty until [initialize] has run, so before then (and while the workspace
 * preview is off, because nothing calls [initialize]) nothing is excluded.
 *
 * [initialize] reads what is stored, copies the legacy global hidden apps and hide rules into every layout
 * once ([ExclusionMigration]; a second call changes nothing) and persists the result only if it changed. The
 * legacy stores are only read. A read that throws (as opposed to a blob that cannot be decoded, which reads
 * as nothing stored) disables persistence for this process, so a transient storage failure can never replace
 * rules that exist on disk.
 *
 * [update] is the only way rules change after that (the management page and the contextual Hide action go
 * through it): the cache is replaced at once, the new value is persisted in order on [scope] (latest wins),
 * and everything that observes the rules is told. This repository is the [ContextChanges] the lens provider
 * observes, so a rule edit (or the first load) re-evaluates live lenses without re-reading any source.
 * [version] changes whenever the held value does, for UI that re-plans.
 */
internal class CachedExclusionRepository(
    private val store: ExclusionStorePort,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ContextChanges {
    @Volatile
    private var cached: LayoutExclusionRules? = null

    @Volatile
    private var persistenceEnabled = true
    private val lock = Any()
    private val writeLock = Mutex()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val mutableVersion = MutableStateFlow(0)

    /** Increases every time the held rules change (including the first load). */
    val version: StateFlow<Int> = mutableVersion.asStateFlow()

    suspend fun initialize(
        hiddenApps: Set<AppIdentity>,
        hideRules: List<NotificationHideRule>,
    ) {
        if (cached != null) return
        val read = runCatching { store.read() }.onFailure { if (it is CancellationException) throw it }
        if (read.isFailure) persistenceEnabled = false
        val stored = read.getOrNull()
        val migrated = ExclusionMigration.migrate(stored ?: LayoutExclusionRules(), hiddenApps, hideRules)
        val installed =
            synchronized(lock) {
                // A change that raced the read is newer than anything the store held.
                if (cached == null) {
                    cached = migrated
                    true
                } else {
                    false
                }
            }
        if (!installed) return
        changed()
        if (read.isSuccess && migrated != stored && persistenceEnabled) write(migrated)
    }

    /** The rule set of [deviceClass]; empty before [initialize]. */
    fun rules(deviceClass: HomeLayoutDeviceClass): ExclusionRuleSet =
        cached?.forLayout(deviceClass) ?: ExclusionRuleSet.EMPTY

    /** Every layout's rules, or null before [initialize]. */
    fun snapshot(): LayoutExclusionRules? = cached

    /**
     * Replaces the held rules with [transform]'s result and tells observers; returns the value now held, or null
     * before [initialize] (nothing is changed then, so an edit can never be lost into an unloaded cache).
     */
    fun update(transform: (LayoutExclusionRules) -> LayoutExclusionRules): LayoutExclusionRules? {
        val outcome =
            synchronized(lock) {
                val current = cached ?: return null
                val next = transform(current)
                if (next != current) cached = next
                next to (next != current)
            }
        if (outcome.second) {
            changed()
            if (persistenceEnabled) scope.launch { write(null) }
        }
        return outcome.first
    }

    override fun observe(onChange: () -> Unit): SourceSubscription {
        listeners += onChange
        return SourceSubscription { listeners -= onChange }
    }

    private fun changed() {
        mutableVersion.update { it + 1 }
        listeners.forEach { it() }
    }

    /** Persists the latest value (or [value] when nothing newer is held); writes are serialised. */
    private suspend fun write(value: LayoutExclusionRules?) {
        writeLock.withLock {
            val latest = cached ?: value ?: return
            runCatching { store.write(latest) }.onFailure { if (it is CancellationException) throw it }
        }
    }
}
