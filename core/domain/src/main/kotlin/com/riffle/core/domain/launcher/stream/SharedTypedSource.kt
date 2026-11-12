package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.workspace.SourceSubscription

/**
 * Implements the sharing contract of [TypedSource] once: [connect] runs on the first observer and its result is
 * cancelled when the last one leaves, so there is one upstream however many sections read it; a new observer gets
 * the latest state at once ([TypedSourceState.Loading] before the upstream emitted, and again after it was stopped);
 * nothing is delivered to an observer after its own cancel returns. Only the latest state is held, never a history.
 */
class SharedTypedSource<T>(
    private val connect: ((TypedSourceState<T>) -> Unit) -> SourceSubscription,
) : TypedSource<T> {
    private class Entry<T>(val observer: (TypedSourceState<T>) -> Unit) {
        @Volatile var active = true
    }

    private val lock = Any()
    private val entries = ArrayList<Entry<T>>()
    private var latest: TypedSourceState<T> = TypedSourceState.Loading
    private var upstream: SourceSubscription? = null
    private var generation = 0L

    override fun subscribe(observer: (TypedSourceState<T>) -> Unit): SourceSubscription {
        val entry = Entry(observer)
        var start = 0L
        val replay: TypedSourceState<T>
        synchronized(lock) {
            entries += entry
            replay = latest
            if (entries.size == 1) start = ++generation
        }
        observer(replay)
        if (start != 0L) {
            val connection = connect { state -> emit(start, state) }
            val stale = synchronized(lock) { (generation != start).also { if (!it) upstream = connection } }
            if (stale) connection.cancel()
        }
        return SourceSubscription { leave(entry) }
    }

    private fun emit(
        session: Long,
        state: TypedSourceState<T>,
    ) {
        val targets =
            synchronized(lock) {
                if (session != generation) return
                latest = state
                entries.toList()
            }
        targets.forEach { entry -> if (entry.active) entry.observer(state) }
    }

    private fun leave(entry: Entry<T>) {
        entry.active = false
        val toCancel =
            synchronized(lock) {
                entries -= entry
                if (entries.isEmpty()) {
                    generation++
                    latest = TypedSourceState.Loading
                    upstream.also { upstream = null }
                } else {
                    null
                }
            }
        toCancel?.cancel()
    }
}
