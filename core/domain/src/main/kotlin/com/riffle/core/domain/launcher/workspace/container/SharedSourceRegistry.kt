package com.riffle.core.domain.launcher.workspace.container

import com.riffle.core.domain.launcher.workspace.ItemSource
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceRegistry
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.SourceSubscription
import com.riffle.core.domain.launcher.workspace.sources.SharedSourceStream

/**
 * Makes the one-upstream-per-source rule hold for every lens, container and widget reading through it,
 * whatever the wrapped registry's own sources do: each source is wrapped once in a [SharedSourceStream]
 * that connects to the real source on the first observer and disconnects after the last. A source the
 * registry does not know reads as [SourceState.Unavailable] rather than failing.
 */
class SharedSourceRegistry(private val delegate: SourceRegistry) : SourceRegistry {
    private val lock = Any()
    private val shared = HashMap<SourceId, ItemSource>()

    override fun descriptors(): List<SourceDescriptor> = delegate.descriptors()

    override fun source(id: SourceId): ItemSource = synchronized(lock) { shared.getOrPut(id) { share(id) } }

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
}
