package com.riffle.core.domain.launcher.workspace.testing

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemId
import com.riffle.core.domain.launcher.workspace.ItemSource
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceObserver
import com.riffle.core.domain.launcher.workspace.SourceRegistry
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.SourceSubscription

/**
 * In-memory [ItemSource] for tests and previews in other workstreams. Replays the latest state to new
 * observers and counts attach/detach so tests can assert shared-subscription behaviour.
 */
class FakeItemSource(
    override val descriptor: SourceDescriptor,
    initial: SourceState = SourceState.Loading,
) : ItemSource {
    private val observers = mutableListOf<SourceObserver>()

    var state: SourceState = initial
        private set

    val observerCount: Int get() = observers.size

    override fun subscribe(observer: SourceObserver): SourceSubscription {
        observers += observer
        observer.onState(state)
        return SourceSubscription { observers -= observer }
    }

    fun emit(newState: SourceState) {
        state = newState
        observers.toList().forEach { it.onState(newState) }
    }

    fun emitItems(items: List<Item>) = emit(SourceState.Ready(items))
}

class FakeSourceRegistry(sources: List<ItemSource>) : SourceRegistry {
    private val byId = sources.associateBy { it.descriptor.id }

    override fun descriptors(): List<SourceDescriptor> = byId.values.map { it.descriptor }

    override fun source(id: SourceId): ItemSource? = byId[id]
}

/** Convenience item builder for tests. */
fun fakeItem(
    id: String,
    sourceId: String = "fake",
    title: String? = id,
    groupKey: String? = null,
    timeEpochMillis: Long? = null,
): Item =
    Item(
        id = ItemId(id),
        sourceId = SourceId(sourceId),
        target = ItemTarget.None,
        title = title,
        groupKey = groupKey,
        groupLabel = groupKey,
        timeEpochMillis = timeEpochMillis,
    )
