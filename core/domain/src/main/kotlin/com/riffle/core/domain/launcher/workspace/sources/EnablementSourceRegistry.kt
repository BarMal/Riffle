package com.riffle.core.domain.launcher.workspace.sources

import com.riffle.core.domain.launcher.workspace.ItemSource
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceRegistry
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.SourceSubscription
import com.riffle.core.domain.launcher.workspace.settings.SourceEnablement

/**
 * Makes a source the user turned off emit [SourceState.Off] without ever subscribing to (so never reading)
 * the wrapped registry's source. Each source is wrapped once in a [SharedSourceStream], so any number of
 * observers share one connection; when [enablement] changes the connection is rebuilt: turning a source on
 * connects the real source (its own states follow, starting with whatever it replays), turning it off
 * cancels the upstream and emits [SourceState.Off].
 *
 * With nothing disabled the states observers see are exactly the wrapped sources' own. A source the
 * wrapped registry does not know reads as [SourceState.Unavailable].
 */
class EnablementSourceRegistry(
    private val delegate: SourceRegistry,
    private val enablement: SourceEnablement,
) : SourceRegistry {
    private val lock = Any()
    private val wrapped = HashMap<SourceId, ItemSource>()

    override fun descriptors(): List<SourceDescriptor> = delegate.descriptors()

    override fun source(id: SourceId): ItemSource = synchronized(lock) { wrapped.getOrPut(id) { wrap(id) } }

    private fun wrap(id: SourceId): ItemSource {
        val descriptor = delegate.descriptors().firstOrNull { it.id == id } ?: SourceDescriptor(id)
        return SharedSourceStream(descriptor) { sink -> Connection(id, sink).also { it.open() } }
    }

    /** One connect of a wrapped stream: follows [enablement] until cancelled. */
    private inner class Connection(
        private val id: SourceId,
        private val sink: SourceSink,
    ) : SourceSubscription {
        private val connectionLock = Any()
        private var upstream: SourceSubscription? = null
        private var generation = 0L
        private var closed = false
        private var listening: SourceSubscription? = null

        fun open() {
            val subscription = enablement.observe { changed -> if (changed == id) refresh() }
            val stale = synchronized(connectionLock) { closed.also { if (!it) listening = subscription } }
            if (stale) subscription.cancel() else refresh()
        }

        private fun refresh() {
            val current: Long
            val previous: SourceSubscription?
            synchronized(connectionLock) {
                if (closed) return
                current = ++generation
                previous = upstream
                upstream = null
            }
            previous?.cancel()
            connect(current)
        }

        private fun connect(current: Long) {
            if (!enablement.isEnabled(id)) {
                sink.emit(SourceState.Off)
                return
            }
            val source = delegate.source(id)
            if (source == null) sink.emit(SourceState.Unavailable) else attach(current, source)
        }

        private fun attach(
            current: Long,
            source: ItemSource,
        ) {
            val subscription = source.subscribe { state -> if (isCurrent(current)) sink.emit(state) }
            val stale =
                synchronized(connectionLock) {
                    (closed || current != generation).also { if (!it) upstream = subscription }
                }
            if (stale) subscription.cancel()
        }

        private fun isCurrent(current: Long): Boolean =
            synchronized(
                connectionLock,
            ) { !closed && current == generation }

        override fun cancel() {
            val toCancel: List<SourceSubscription?>
            synchronized(connectionLock) {
                if (closed) return
                closed = true
                toCancel = listOf(listening, upstream)
                listening = null
                upstream = null
            }
            toCancel.forEach { it?.cancel() }
        }
    }
}
