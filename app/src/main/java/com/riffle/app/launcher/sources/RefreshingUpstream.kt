package com.riffle.app.launcher.sources

import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.SourceSubscription
import com.riffle.core.domain.launcher.workspace.sources.SourceSink
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Something that can tell a source its data may have changed. Deliberately free of Android types so
 * adapters stay testable on the JVM; the platform wiring lives in [AndroidItemSources].
 */
fun interface SourceChangeSource {
    /** Starts observing; the returned function stops it. [onChanged] may be called from any thread. */
    fun observe(onChanged: () -> Unit): () -> Unit

    /** False when this can never report a change, so a source built on it must not claim `LIVE`. */
    val isLive: Boolean get() = true

    companion object {
        val NONE: SourceChangeSource =
            object : SourceChangeSource {
                override val isLive: Boolean = false

                override fun observe(onChanged: () -> Unit): () -> Unit = {}
            }

        fun merge(sources: List<SourceChangeSource>): SourceChangeSource =
            object : SourceChangeSource {
                override val isLive: Boolean = sources.any(SourceChangeSource::isLive)

                override fun observe(onChanged: () -> Unit): () -> Unit {
                    val stops = sources.map { source -> source.observe(onChanged) }
                    return { stops.forEach { stop -> stop() } }
                }
            }
    }
}

/**
 * The upstream behind one shared source stream. Runs [load] on [executor] (never the caller's thread, so
 * blocking platform reads stay off the main thread): once on connect and again after every change, with
 * bursts of changes coalesced into one reload. A failing [load] emits [SourceState.Unavailable].
 */
internal class RefreshingUpstream(
    private val executor: Executor,
    private val changes: SourceChangeSource,
    private val load: () -> SourceState,
) {
    fun connect(sink: SourceSink): SourceSubscription {
        val active = AtomicBoolean(true)
        val pending = AtomicBoolean(false)
        val refresh = {
            if (active.get() && pending.compareAndSet(false, true)) {
                executor.execute {
                    pending.set(false)
                    if (active.get()) sink.emit(runCatching(load).getOrDefault(SourceState.Unavailable))
                }
            }
        }
        val stopObserving = changes.observe(refresh)
        refresh()
        return SourceSubscription {
            active.set(false)
            stopObserving()
        }
    }
}
