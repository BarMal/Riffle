package com.riffle.core.domain.launcher.workspace.settings

import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceSubscription
import com.riffle.core.domain.launcher.workspace.container.ContextChanges
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevel
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevels
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The per-source content levels the user chose (Settings > Privacy and the source pages). Every source shows
 * everything until changed. Platform neutral: the app persists it, and lens evaluation reads [levels] through
 * its context. It is also the [ContextChanges] the lens provider watches, so a change re-evaluates every
 * observed lens at once (no refresh needed).
 */
interface SourceContentPrivacy : ContextChanges {
    fun levels(): SourceContentLevels

    fun setLevel(
        id: SourceId,
        level: SourceContentLevel,
    )
}

/**
 * Durable storage for the levels, as source id to [SourceContentLevel.storedId] strings (the stored contract).
 * Only ids and level names: never item content.
 */
interface ContentLevelStore {
    fun read(): Map<String, String>

    fun write(levels: Map<String, String>)
}

/**
 * [SourceContentPrivacy] over a [ContentLevelStore]: read once, kept in memory, written on every change. A store
 * that fails to read means every source shows everything (today's behaviour); unknown level ids are ignored. One
 * that fails to write keeps this process correct.
 */
class StoredSourceContentPrivacy(private val store: ContentLevelStore) : SourceContentPrivacy {
    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private var current: SourceContentLevels = decode(runCatching { store.read() }.getOrDefault(emptyMap()))

    override fun levels(): SourceContentLevels = synchronized(lock) { current }

    override fun setLevel(
        id: SourceId,
        level: SourceContentLevel,
    ) {
        val changed =
            synchronized(lock) {
                val next = current.with(id, level)
                (next != current).also {
                    current = next
                    if (it) runCatching { store.write(encode(next)) }
                }
            }
        if (changed) listeners.forEach { it() }
    }

    override fun observe(onChange: () -> Unit): SourceSubscription {
        listeners += onChange
        return SourceSubscription { listeners -= onChange }
    }

    companion object {
        fun decode(stored: Map<String, String>): SourceContentLevels =
            SourceContentLevels(
                stored.entries
                    .filter { (id, _) -> id.isNotBlank() }
                    .mapNotNull { (id, level) -> SourceContentLevel.fromStoredId(level)?.let { SourceId(id) to it } }
                    .filter { (_, level) -> level != SourceContentLevel.DEFAULT }
                    .toMap(),
            )

        fun encode(levels: SourceContentLevels): Map<String, String> =
            levels.levels.entries.associate { (id, level) -> id.value to level.storedId }
    }
}

/** An in-memory [ContentLevelStore] for tests and previews. */
class InMemoryContentLevelStore(var stored: Map<String, String> = emptyMap()) : ContentLevelStore {
    override fun read(): Map<String, String> = stored

    override fun write(levels: Map<String, String>) {
        stored = levels
    }
}

/** One content-level row of Settings > Privacy. */
data class ContentLevelRow(
    val id: SourceId,
    val title: String,
    val level: SourceContentLevel,
)

/**
 * The Privacy page's content-level section as data: one row per registered privacy-sensitive source (those whose
 * descriptor carries [SourceCapability.PRIVACY_SENSITIVE]: notifications, media, calendar, calendar feeds), in the
 * built-in order, then any other. Sources that are not registered have no row.
 */
object ContentLevelPlanner {
    fun plan(
        descriptors: List<SourceDescriptor>,
        levels: SourceContentLevels,
    ): List<ContentLevelRow> {
        val ids =
            descriptors
                .filter { SourceCapability.PRIVACY_SENSITIVE in it.capabilities }
                .map { it.id }
                .distinct()
        val ordered = SourceIds.BUILT_IN.filter { it in ids } + ids.filterNot { it in SourceIds.BUILT_IN }
        return ordered.map { id -> ContentLevelRow(id, SourceCatalog.infoFor(id).title, levels.levelOf(id)) }
    }

    /** Whether [id] gets a content-level control (on its source page as well as on the Privacy page). */
    fun supports(
        descriptors: List<SourceDescriptor>,
        id: SourceId,
    ): Boolean = descriptors.any { it.id == id && SourceCapability.PRIVACY_SENSITIVE in it.capabilities }
}

/** Several [ContextChanges] as one: a change in any of them is a change. Null entries are skipped. */
fun mergedContextChanges(vararg changes: ContextChanges?): ContextChanges? {
    val present = changes.filterNotNull()
    return when (present.size) {
        0 -> null
        1 -> present.single()
        else ->
            ContextChanges { onChange ->
                val subscriptions = present.map { it.observe(onChange) }
                SourceSubscription { subscriptions.forEach(SourceSubscription::cancel) }
            }
    }
}
