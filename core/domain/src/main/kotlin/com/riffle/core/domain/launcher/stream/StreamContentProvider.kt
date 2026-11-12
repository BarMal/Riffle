package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceRegistry
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.SourceSubscription
import com.riffle.core.domain.launcher.workspace.container.ContextChanges
import com.riffle.core.domain.launcher.workspace.container.SharedSourceRegistry

/**
 * What the Stream UI (P4) consumes: [observe] a [StreamSection] and receive a [StreamSectionResult] each time
 * something it depends on changes. The result is already excluded (Hide rules), redacted (content levels),
 * grouped, sorted and limited, so the UI only draws.
 *
 * Sources are read through a [SharedSourceRegistry] (one upstream per source however many sections, lenses or
 * widgets read it; wrapping an already shared registry again still keeps one upstream). Nothing is read until a
 * section is observed, and cancelling the last observer detaches the upstream. Typed providers ([tasks],
 * [alarms]) are subscribed only by the sections that read them.
 *
 * [context] is asked for a fresh [StreamContext] on every evaluation, so ages and "now" are current.
 * [contextChanges] tells the provider that something [context] reads changed (the exclusion rules, content levels
 * or section options): every live observation then re-evaluates its latest states without touching the sources.
 *
 * Evaluation is cheap, pure and runs on the thread that delivered the change (a source's executor, never the main
 * thread), so the listener must hop to the main thread itself. Results are delivered in order, only the newest is
 * ever delivered, and nothing is delivered after [StreamSubscription.cancel] returns. Content is never persisted.
 */
class StreamContentProvider(
    registry: SourceRegistry,
    private val tasks: TaskProvider = NotConnectedTaskProvider,
    private val alarms: NextAlarmProvider = NoAlarmProvider,
    private val contextChanges: ContextChanges? = null,
    private val context: () -> StreamContext,
) {
    private val sources = SharedSourceRegistry(registry)

    fun observe(
        section: StreamSection,
        listener: StreamSectionListener,
    ): StreamSubscription = Observation(section, listener).also { it.start() }

    private inner class Observation(
        private val section: StreamSection,
        private val listener: StreamSectionListener,
    ) : StreamSubscription {
        private val lock = Any()
        private val deliveryLock = Any()
        private val itemStates = HashMap<SourceId, SourceState>()
        private var tasksState: TypedSourceState<TaskContent> = TypedSourceState.Loading
        private var alarmState: TypedSourceState<NextAlarm> = TypedSourceState.Loading
        private val subscriptions = ArrayList<SourceSubscription>()
        private var watching: SourceSubscription? = null
        private var issued = 0L
        private var delivered = 0L
        private var started = false

        @Volatile private var closed = false

        fun start() {
            synchronized(lock) { section.itemSources.forEach { itemStates[it] = SourceState.Loading } }
            section.itemSources.forEach { id ->
                track(sources.source(id).subscribe { state -> update { itemStates[id] = state } })
            }
            if (section.readsTasks) track(tasks.subscribe { state -> update { tasksState = state } })
            if (section.readsAlarm) track(alarms.subscribe { state -> update { alarmState = state } })
            contextChanges?.observe { refresh() }?.let { handle ->
                val stale = synchronized(lock) { closed.also { if (!it) watching = handle } }
                if (stale) handle.cancel()
            }
            synchronized(lock) { started = true }
            refresh()
        }

        private fun track(subscription: SourceSubscription) {
            val stale = synchronized(lock) { closed.also { if (!it) subscriptions += subscription } }
            if (stale) subscription.cancel()
        }

        private fun update(change: () -> Unit) {
            synchronized(lock) {
                if (closed) return
                change()
            }
            refresh()
        }

        private fun refresh() {
            val sequence: Long
            val inputs: StreamSectionInputs
            synchronized(lock) {
                if (closed || !started) return
                sequence = ++issued
                inputs = StreamSectionInputs(itemStates.toMap(), tasksState, alarmState)
            }
            val result =
                runCatching { StreamSectionEvaluator.evaluate(section, inputs, context()) }
                    .getOrDefault(StreamSectionResult.Unavailable)
            deliver(sequence, result)
        }

        private fun deliver(
            sequence: Long,
            result: StreamSectionResult,
        ) {
            synchronized(deliveryLock) {
                if (closed || sequence <= delivered) return
                synchronized(lock) { if (sequence != issued) return }
                delivered = sequence
                listener.onResult(result)
            }
        }

        override fun cancel() {
            val toCancel: List<SourceSubscription>
            synchronized(lock) {
                if (closed) return
                closed = true
                toCancel = subscriptions.toList() + listOfNotNull(watching)
                subscriptions.clear()
                watching = null
            }
            toCancel.forEach { it.cancel() }
        }
    }
}
