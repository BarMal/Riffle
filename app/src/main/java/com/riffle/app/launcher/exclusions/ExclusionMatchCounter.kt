package com.riffle.app.launcher.exclusions

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceRegistry
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.SourceSubscription
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleId
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleSet
import com.riffle.core.domain.launcher.workspace.exclusions.SourceExclusionFilter
import java.util.concurrent.Executor

/**
 * The "hides N items right now" numbers of the Hidden items page. No Android types.
 *
 * While started it reads, through [registry] (pass the shared registry the containers read through, so this adds
 * no upstream of its own), only the sources the tracked rules can hide from, and recomputes the counts with the
 * domain's [SourceExclusionFilter.matchCounts] whenever a source or the rules change. Only counts leave this class:
 * the items are held in memory for the computation and dropped on [stop] (and are never logged or stored), and
 * nothing here renders them. [onCounts] runs on [executor], never after [stop].
 *
 * A rule has a count only when its own source is ready and it is enabled (an off source, a missing permission or a
 * source still loading has none), so the page can show a dash instead of a wrong zero.
 */
internal class ExclusionMatchCounter(
    private val registry: SourceRegistry,
    private val onCounts: (Map<ExclusionRuleId, Int>) -> Unit,
    private val executor: Executor = Executor { it.run() },
) {
    private val lock = Any()
    private val subscriptions = HashMap<SourceId, SourceSubscription>()
    private val states = HashMap<SourceId, SourceState>()
    private var tracked = emptySet<SourceId>()
    private var rules = ExclusionRuleSet.EMPTY
    private var running = false
    private var generation = 0L

    fun start() {
        synchronized(lock) { running = true }
    }

    /** Counts [next] from now on: subscribes to the sources it needs and releases the ones it no longer does. */
    fun track(next: ExclusionRuleSet) {
        val needed = SourceExclusionFilter.sourcesFor(next)
        val release: List<SourceSubscription>
        val attach: Set<SourceId>
        synchronized(lock) {
            if (!running) return
            rules = next
            tracked = needed
            attach = needed - subscriptions.keys
            release = (subscriptions.keys - needed).mapNotNull { subscriptions.remove(it) }
            states.keys.retainAll(needed)
        }
        release.forEach { it.cancel() }
        attach.forEach { attach(it) }
        recompute()
    }

    /** Releases every subscription and forgets everything; no callback follows. */
    fun stop() {
        val release: List<SourceSubscription>
        synchronized(lock) {
            running = false
            generation++
            release = subscriptions.values.toList()
            subscriptions.clear()
            states.clear()
            tracked = emptySet()
            rules = ExclusionRuleSet.EMPTY
        }
        release.forEach { it.cancel() }
    }

    private fun attach(id: SourceId) {
        val source = registry.source(id)
        if (source == null) {
            onState(id, SourceState.Unavailable)
            return
        }
        val subscription = source.subscribe { state -> onState(id, state) }
        val stale =
            synchronized(lock) {
                if (running && id in tracked && id !in subscriptions) {
                    subscriptions[id] = subscription
                    false
                } else {
                    true
                }
            }
        if (stale) subscription.cancel()
    }

    private fun onState(
        id: SourceId,
        state: SourceState,
    ) {
        synchronized(lock) {
            if (!running || id !in tracked) return
            states[id] = state
        }
        recompute()
    }

    private fun recompute() {
        val sequence: Long
        val snapshotRules: ExclusionRuleSet
        val snapshotStates: Map<SourceId, SourceState>
        synchronized(lock) {
            if (!running) return
            sequence = ++generation
            snapshotRules = rules
            snapshotStates = HashMap(states)
        }
        executor.execute {
            val counts = count(snapshotRules, snapshotStates)
            val current = synchronized(lock) { running && sequence == generation }
            if (current) onCounts(counts)
        }
    }

    private fun count(
        set: ExclusionRuleSet,
        snapshot: Map<SourceId, SourceState>,
    ): Map<ExclusionRuleId, Int> {
        val items: List<Item> =
            snapshot.values.flatMap { (it as? SourceState.Ready)?.items.orEmpty().take(MAX_ITEMS_PER_SOURCE) }
        val counted = set.rules.filter { it.enabled && snapshot[it.source] is SourceState.Ready }
        return SourceExclusionFilter.matchCounts(ExclusionRuleSet(counted), items)
    }

    companion object {
        /** Bounds the work of one recompute however large a source is. */
        const val MAX_ITEMS_PER_SOURCE = 2_000
    }
}
