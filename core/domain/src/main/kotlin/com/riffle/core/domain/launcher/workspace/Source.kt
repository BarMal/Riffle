package com.riffle.core.domain.launcher.workspace

enum class SourceCapability {
    LIVE,
    GROUPABLE,
    ACTIONABLE,
    SEARCHABLE,
    PRIVACY_SENSITIVE,
}

/** Static description of a source. Persisted lenses refer to sources by [id] only. */
data class SourceDescriptor(
    val id: SourceId,
    val capabilities: Set<SourceCapability> = emptySet(),
)

sealed interface SourceState {
    data object Loading : SourceState

    data class Ready(val items: List<Item>) : SourceState

    /** The user has not granted what the source needs (e.g. notification access). Never a prompt. */
    data object PermissionRequired : SourceState

    data object Unavailable : SourceState
}

fun interface SourceObserver {
    fun onState(state: SourceState)
}

fun interface SourceSubscription {
    fun cancel()
}

/**
 * Platform-facing seam for anything that produces [Item]s. Implementations share one upstream
 * subscription however many observers attach (one subscription per source, not per widget), and
 * replay the latest [SourceState] to a new observer. Adapters live outside the domain (WS1).
 */
interface ItemSource {
    val descriptor: SourceDescriptor

    fun subscribe(observer: SourceObserver): SourceSubscription
}

/** Looks up sources by id; lenses resolve their [LensSourceRef]s through this. */
interface SourceRegistry {
    fun descriptors(): List<SourceDescriptor>

    fun source(id: SourceId): ItemSource?
}
