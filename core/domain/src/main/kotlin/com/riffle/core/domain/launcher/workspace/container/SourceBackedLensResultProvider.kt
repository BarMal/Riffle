package com.riffle.core.domain.launcher.workspace.container

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceRegistry
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.SourceSubscription
import com.riffle.core.domain.launcher.workspace.lens.AsyncLensEvaluator
import com.riffle.core.domain.launcher.workspace.lens.LensEvaluation
import com.riffle.core.domain.launcher.workspace.lens.LensEvaluationContext

/**
 * The real [LensResultProvider]: subscribes to the lens's sources through a [SharedSourceRegistry] (one
 * upstream per source however many lenses read it) and re-evaluates through [evaluator] (off the main
 * thread) whenever a source changes.
 *
 * Outputs are delivered in order and only the newest evaluation is ever delivered: a change while an
 * evaluation is in flight supersedes it. Cancelling the handle detaches from every source, suppresses the
 * in-flight evaluation and guarantees no later callback.
 *
 * [context] is asked for a fresh evaluation context each time so age filters see the current time.
 */
class SourceBackedLensResultProvider(
    registry: SourceRegistry,
    private val evaluator: AsyncLensEvaluator,
    private val context: () -> LensEvaluationContext,
) : LensResultProvider {
    private val sources = SharedSourceRegistry(registry)

    override fun observe(
        lens: Lens,
        listener: LensOutputListener,
    ): LensEvaluation = Observation(lens, listener).also { it.start() }

    private inner class Observation(
        private val lens: Lens,
        private val listener: LensOutputListener,
    ) : LensEvaluation {
        private val lock = Any()
        private val sourceIds = lens.sources.distinct()
        private val states = HashMap<SourceId, SourceState>()
        private val subscriptions = ArrayList<SourceSubscription>()
        private var inFlight: LensEvaluation? = null
        private var issued = 0L
        private var delivered = 0L
        private var started = false
        private var closed = false

        fun start() {
            synchronized(lock) { sourceIds.forEach { id -> states[id] = SourceState.Loading } }
            sourceIds.forEach { id -> attach(id) }
            synchronized(lock) { started = true }
            refresh()
        }

        private fun attach(id: SourceId) {
            val subscription = sources.source(id).subscribe { state -> onSourceState(id, state) }
            val stale = synchronized(lock) { closed || !subscriptions.add(subscription) }
            if (stale) subscription.cancel()
        }

        private fun onSourceState(
            id: SourceId,
            state: SourceState,
        ) {
            synchronized(lock) {
                if (closed) return
                states[id] = state
            }
            refresh()
        }

        /** Recomputes the output for the current source states; nothing happens until [start] has attached. */
        private fun refresh() {
            val sequence: Long
            val snapshot: List<SourceState>
            synchronized(lock) {
                if (closed || !started) return
                sequence = ++issued
                snapshot = sourceIds.map { states.getValue(it) }
                inFlight?.cancel()
                inFlight = null
            }
            val availability = LensOutput.availabilityOf(snapshot)
            if (availability == LensAvailability.READY) {
                evaluate(sequence, snapshot.flatMap { (it as? SourceState.Ready)?.items.orEmpty() })
            } else {
                deliver(sequence, LensOutput(availability))
            }
        }

        private fun evaluate(
            sequence: Long,
            items: List<Item>,
        ) {
            val handle =
                evaluator.evaluate(
                    lens = lens,
                    items = items,
                    context = context(),
                    onError = { deliver(sequence, LensOutput.Unavailable) },
                    onResult = { result -> deliver(sequence, LensOutput.ready(result)) },
                )
            synchronized(lock) {
                if (closed || sequence != issued) handle.cancel() else inFlight = handle
            }
        }

        private fun deliver(
            sequence: Long,
            output: LensOutput,
        ) {
            synchronized(lock) {
                if (closed || sequence != issued || sequence <= delivered) return
                delivered = sequence
            }
            listener.onOutput(output)
        }

        override fun cancel() {
            val toCancel: List<SourceSubscription>
            val pending: LensEvaluation?
            synchronized(lock) {
                if (closed) return
                closed = true
                toCancel = subscriptions.toList()
                subscriptions.clear()
                pending = inFlight
                inFlight = null
            }
            pending?.cancel()
            toCancel.forEach { it.cancel() }
        }
    }
}
