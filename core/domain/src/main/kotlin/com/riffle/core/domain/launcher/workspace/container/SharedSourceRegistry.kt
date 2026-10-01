package com.riffle.core.domain.launcher.workspace.container

import com.riffle.core.domain.launcher.workspace.ItemSource
import com.riffle.core.domain.launcher.workspace.ParameterizedItemSource
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceObserver
import com.riffle.core.domain.launcher.workspace.SourceParameter
import com.riffle.core.domain.launcher.workspace.SourceRegistry
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.SourceSubscription
import com.riffle.core.domain.launcher.workspace.sources.SharedSourceStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Makes the one-upstream-per-source rule hold for every lens, container and widget reading through it,
 * whatever the wrapped registry's own sources do: each source is wrapped once in a [SharedSourceStream]
 * that connects to the real source on the first observer and disconnects after the last. A source the
 * registry does not know reads as [SourceState.Unavailable] rather than failing.
 */
class SharedSourceRegistry(
    private val delegate: SourceRegistry,
    private val maxParameterStreams: Int = DEFAULT_MAX_PARAMETER_STREAMS,
) : SourceRegistry {
    private val lock = Any()
    private val shared = HashMap<SourceId, ItemSource>()
    private val parameterized = HashMap<ParameterKey, ParameterEntry>()

    init {
        require(maxParameterStreams > 0) { "At least one parameter stream must be allowed." }
    }

    override fun descriptors(): List<SourceDescriptor> = delegate.descriptors()

    override fun source(id: SourceId): ItemSource = synchronized(lock) { shared.getOrPut(id) { share(id) } }

    /**
     * The shared stream for [id] under [parameter]. With no parameter, or for a source that is not a
     * [ParameterizedItemSource], this is [source] (one upstream per source). Otherwise there is one upstream per
     * distinct (source, parameter), created on the first observer and dropped after the last, and at most
     * `maxParameterStreams` of them live at once: an observer beyond the cap reads [SourceState.Unavailable]
     * rather than starting another upstream, and attaches normally once a slot frees.
     */
    fun source(
        id: SourceId,
        parameter: SourceParameter?,
    ): ItemSource {
        val upstream = parameter?.let { delegate.source(id) as? ParameterizedItemSource }
        return if (parameter == null || upstream == null) source(id) else ParameterView(id, parameter, upstream)
    }

    /** How many parameterized upstreams are live right now (a count, never the parameters). */
    fun liveParameterStreams(): Int = synchronized(lock) { parameterized.size }

    private inner class ParameterView(
        private val id: SourceId,
        private val parameter: SourceParameter,
        private val upstream: ParameterizedItemSource,
    ) : ItemSource {
        override val descriptor: SourceDescriptor get() = upstream.descriptor

        override fun subscribe(observer: SourceObserver): SourceSubscription {
            val key = ParameterKey(id, parameter)
            val entry = acquire(key)
            if (entry == null) {
                observer.onState(SourceState.Unavailable)
                return SourceSubscription { }
            }
            val inner = entry.stream.subscribe(observer)
            val cancelled = AtomicBoolean(false)
            return SourceSubscription {
                if (cancelled.compareAndSet(false, true)) {
                    inner.cancel()
                    release(key, entry)
                }
            }
        }

        private fun acquire(key: ParameterKey): ParameterEntry? =
            synchronized(lock) {
                val existing = parameterized[key]
                when {
                    existing != null -> existing.also { it.holders++ }
                    parameterized.size >= maxParameterStreams -> null
                    else -> newEntry().also { parameterized[key] = it }
                }
            }

        private fun newEntry() =
            ParameterEntry(
                SharedSourceStream(upstream.descriptor) { sink ->
                    upstream.subscribe(parameter) { state -> sink.emit(state) }
                },
            )

        private fun release(
            key: ParameterKey,
            entry: ParameterEntry,
        ) {
            synchronized(lock) {
                entry.holders--
                if (entry.holders == 0 && parameterized[key] === entry) parameterized.remove(key)
            }
        }
    }

    /** A map key only: its [SourceParameter] keeps the text out of every string form. */
    private data class ParameterKey(val id: SourceId, val parameter: SourceParameter)

    private class ParameterEntry(val stream: SharedSourceStream, var holders: Int = 1)

    private fun share(id: SourceId): ItemSource {
        val descriptor = delegate.descriptors().firstOrNull { it.id == id } ?: SourceDescriptor(id)
        return SharedSourceStream(descriptor) { sink ->
            val upstream = delegate.source(id)
            if (upstream == null) {
                sink.emit(SourceState.Unavailable)
                SourceSubscription { }
            } else {
                upstream.subscribe { state -> sink.emit(state) }
            }
        }
    }

    companion object {
        /** Live parameterized upstreams allowed at once; each one is a source query kept running. */
        const val DEFAULT_MAX_PARAMETER_STREAMS = 4
    }
}
