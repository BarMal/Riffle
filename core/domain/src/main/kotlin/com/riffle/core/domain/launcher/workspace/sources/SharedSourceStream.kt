package com.riffle.core.domain.launcher.workspace.sources

import com.riffle.core.domain.launcher.workspace.ItemSource
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceObserver
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.SourceSubscription

/** Where an upstream pushes the states it produces. Safe to call from any thread. */
fun interface SourceSink {
    fun emit(state: SourceState)
}

/** Runs a task once after a delay. The returned subscription cancels it if it has not run yet. */
fun interface DelayedTaskScheduler {
    fun schedule(
        delayMillis: Long,
        task: Runnable,
    ): SourceSubscription
}

/**
 * Implements the [ItemSource] sharing contract once, so platform adapters only describe how to reach
 * their upstream.
 *
 * - [connect] is called on the first observer and its result cancelled when the last observer leaves
 *   (after [graceMillis], if positive), so there is at most one upstream however many observers attach.
 * - Every new observer immediately receives the latest state ([SourceState.Loading] before the upstream
 *   has emitted, and again after the upstream was stopped).
 * - Observers see states in emission order and never after their own cancel returns.
 * - Thread-safe without coroutines: internal locks are never held while calling [connect], cancelling
 *   the upstream, or notifying the observer of another stream.
 *
 * The stream holds only the most recent [SourceState]; item content is transient and never persisted.
 */
class SharedSourceStream(
    override val descriptor: SourceDescriptor,
    private val graceMillis: Long = 0L,
    private val scheduler: DelayedTaskScheduler? = null,
    private val connect: (SourceSink) -> SourceSubscription,
) : ItemSource {
    private val lock = Any()
    private val entries = ArrayList<Entry>()
    private var latest: SourceState = SourceState.Loading
    private var sequence = 0L
    private var upstream: SourceSubscription? = null
    private var session = 0L
    private var running = false
    private var graceTask: SourceSubscription? = null
    private var graceEpoch = 0L

    init {
        require(graceMillis >= 0L) { "Grace period cannot be negative." }
        require(graceMillis == 0L || scheduler != null) { "A grace period needs a scheduler." }
    }

    override fun subscribe(observer: SourceObserver): SourceSubscription {
        val entry = Entry(observer)
        val attach = attach(entry)
        attach.cancelGrace?.cancel()
        entry.deliver(attach.replaySequence, attach.replay)
        if (attach.startSession != NO_SESSION) startUpstream(attach.startSession)
        return SourceSubscription { detach(entry) }
    }

    private fun attach(entry: Entry): Attach =
        synchronized(lock) {
            entries += entry
            graceEpoch++
            val cancelGrace = graceTask
            graceTask = null
            val startSession =
                if (running) {
                    NO_SESSION
                } else {
                    running = true
                    ++session
                }
            Attach(latest, sequence, startSession, cancelGrace)
        }

    private fun startUpstream(startedSession: Long) {
        val sink = SourceSink { state -> publish(startedSession, state) }
        val connection = runCatching { connect(sink) }
        if (connection.isFailure) publish(startedSession, SourceState.Unavailable)
        val established = connection.getOrNull() ?: SourceSubscription { }
        val stale =
            synchronized(lock) {
                if (running && session == startedSession) {
                    upstream = established
                    null
                } else {
                    established
                }
            }
        stale?.cancel()
    }

    private fun publish(
        fromSession: Long,
        state: SourceState,
    ) {
        val targets: List<Entry>
        val published: Long
        synchronized(lock) {
            if (!running || session != fromSession) return
            latest = state
            published = ++sequence
            targets = entries.toList()
        }
        targets.forEach { entry -> entry.deliver(published, state) }
    }

    private fun detach(entry: Entry) {
        entry.deactivate()
        val stopNow =
            synchronized(lock) {
                if (!entries.remove(entry) || entries.isNotEmpty()) return
                if (graceMillis == 0L) true else scheduleGraceLocked()
            }
        if (stopNow) stopUpstream(expectedEpoch = null)
    }

    /** Must hold [lock]. Returns true when the caller has to stop immediately instead. */
    private fun scheduleGraceLocked(): Boolean {
        val epoch = ++graceEpoch
        val delayed = scheduler ?: return true
        graceTask = delayed.schedule(graceMillis) { stopUpstream(expectedEpoch = epoch) }
        return false
    }

    private fun stopUpstream(expectedEpoch: Long?) {
        val stopped =
            synchronized(lock) {
                // An observer may have attached since the last one left; then the upstream must live on.
                val stale = entries.isNotEmpty() || (expectedEpoch != null && expectedEpoch != graceEpoch)
                if (stale || !running) {
                    null
                } else {
                    running = false
                    session++
                    graceTask = null
                    latest = SourceState.Loading
                    sequence++
                    upstream.also { upstream = null } ?: SourceSubscription { }
                }
            }
        stopped?.cancel()
    }

    private class Attach(
        val replay: SourceState,
        val replaySequence: Long,
        val startSession: Long,
        val cancelGrace: SourceSubscription?,
    )

    /** One observer; serialises its deliveries and drops stale or post-cancel ones. */
    private class Entry(private val observer: SourceObserver) {
        private var lastSequence = -1L
        private var active = true

        @Synchronized
        fun deliver(
            sequence: Long,
            state: SourceState,
        ) {
            if (!active || sequence <= lastSequence) return
            lastSequence = sequence
            observer.onState(state)
        }

        @Synchronized
        fun deactivate() {
            active = false
        }
    }

    private companion object {
        const val NO_SESSION = -1L
    }
}
