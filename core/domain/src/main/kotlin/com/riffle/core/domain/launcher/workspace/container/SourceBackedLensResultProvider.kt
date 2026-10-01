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
 * [contextChanges] (optional) tells the provider that something [context] reads has changed (the layout's
 * exclusion rules): every live observation then re-evaluates its latest source states without touching the
 * sources, so a rule edit shows at once. It is detached with the observation.
 */
class SourceBackedLensResultProvider(
    registry: SourceRegistry,
    private val evaluator: AsyncLensEvaluator,
    private val contextChanges: ContextChanges? = null,
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
        private var changes: SourceSubscription? = null
        private var inFlight: LensEvaluation? = null
        private var issued = 0L
        private var delivered = 0L
        private var started = false
        private var closed = false

        fun start() {
            synchronized(lock) { sourceIds.forEach { id -> states[id] = SourceState.Loading } }
            sourceIds.forEach { id -> attach(id) }
            contextChanges?.observe { refresh() }?.let { watching ->
                val stale =
                    synchronized(lock) {
                        closed.also { isClosed -> if (!isClosed) changes = watching }
                    }
                if (stale) watching.cancel()
            }
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
            val watching: SourceSubscription?
            synchronized(lock) {
                if (closed) return
                closed = true
                toCancel = subscriptions.toList()
                subscriptions.clear()
                watching = changes
                changes = null
                pending = inFlight
                inFlight = null
            }
            pending?.cancel()
            watching?.cancel()
            toCancel.forEach { it.cancel() }
        }
    }
}

/**
 * A signal that the context lens evaluation reads has changed. [observe] returns a handle that detaches the
 * listener; [onChange] may be called from any thread and must be cheap.
 */
fun interface ContextChanges {
    fun observe(onChange: () -> Unit): SourceSubscription
}
