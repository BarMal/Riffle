package com.riffle.core.domain.launcher.workspace.settings

import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceRegistry
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.SourceSubscription

/** What Settings > Sources says about one source. Always shown as text, never by colour alone. */
enum class SourceStatus(val label: String) {
    LOADING("Loading"),
    READY("Ready"),
    NEEDS_PERMISSION("Needs permission"),
    OFF("Off"),
    UNAVAILABLE("Unavailable"),
    ;

    companion object {
        fun of(state: SourceState): SourceStatus =
            when (state) {
                SourceState.Loading -> LOADING
                is SourceState.Ready -> READY
                SourceState.PermissionRequired -> NEEDS_PERMISSION
                SourceState.Off -> OFF
                SourceState.Unavailable -> UNAVAILABLE
            }
    }
}

/** The name of a built-in source and a one-line description of what it shows. */
data class SourceInfo(
    val title: String,
    val description: String,
)

/** User-facing names and one-liners for the built-in sources. Unknown ids read as a generic source. */
object SourceCatalog {
    private val infos: Map<SourceId, SourceInfo> =
        mapOf(
            SourceIds.ALL_APPS to SourceInfo("Apps", "Every app you can launch."),
            SourceIds.RECENT_APPS to SourceInfo("Recent apps", "Apps you used lately. Needs Usage access."),
            SourceIds.QUICK_ACTIONS to SourceInfo("Quick actions", "App shortcuts, such as Compose or Scan."),
            SourceIds.NOTIFICATIONS to
                SourceInfo("Notifications", "Your current notifications, grouped by app. Needs notification access."),
            SourceIds.MEDIA to SourceInfo("Media", "What is playing now, from media notifications."),
            SourceIds.CALENDAR to SourceInfo("Calendar", "Your next events. Private events stay hidden."),
            SourceIds.RSS to SourceInfo("RSS feeds", "Articles from the feeds you added, as last cached."),
            SourceIds.SEARCH to SourceInfo("Search", "Results for the query a search lens carries."),
            SourceIds.ICS to
                SourceInfo("Calendar feeds", "Events from the calendar feeds you added, as last refreshed."),
        )

    fun infoFor(id: SourceId): SourceInfo = infos[id] ?: SourceInfo(id.value, "Another source.")

    fun isKnown(id: SourceId): Boolean = id in infos
}

/** One row of Settings > Sources. */
data class SourceRow(
    val id: SourceId,
    val title: String,
    val description: String,
    val status: SourceStatus,
    val enabled: Boolean,
)

/**
 * The Sources page as data. Rows follow the registry's descriptors (built-in order first, then any other),
 * so a source that does not exist simply has no row. A disabled source always reads [SourceStatus.OFF];
 * a source nobody has reported on yet reads [SourceStatus.LOADING].
 */
object SourcesSettingsPlanner {
    fun plan(
        descriptors: List<SourceDescriptor>,
        statuses: Map<SourceId, SourceStatus>,
        disabled: Set<SourceId>,
    ): List<SourceRow> {
        val ids = descriptors.map { it.id }.distinct()
        val ordered = SourceIds.BUILT_IN.filter { it in ids } + ids.filterNot { it in SourceIds.BUILT_IN }
        return ordered.map { id ->
            val info = SourceCatalog.infoFor(id)
            val enabled = id !in disabled
            SourceRow(
                id = id,
                title = info.title,
                description = info.description,
                status = if (enabled) statuses[id] ?: SourceStatus.LOADING else SourceStatus.OFF,
                enabled = enabled,
            )
        }
    }
}

/**
 * Reads each source's status through [registry] (pass the shared registry the containers read through, so
 * this adds no upstream of its own): one subscription per source while started, all cancelled by [stop].
 * Only the status is kept, never the items. [onChange] may be called from any thread, never after [stop].
 */
class SourceStatusMonitor(
    private val registry: SourceRegistry,
    private val ids: List<SourceId>,
    private val onChange: (Map<SourceId, SourceStatus>) -> Unit,
) {
    private val lock = Any()
    private val statuses = LinkedHashMap<SourceId, SourceStatus>()
    private val subscriptions = ArrayList<SourceSubscription>()
    private var running = false

    fun start() {
        val begin =
            synchronized(lock) {
                if (running) {
                    false
                } else {
                    running = true
                    ids.distinct().forEach { statuses[it] = SourceStatus.LOADING }
                    true
                }
            }
        if (begin) ids.distinct().forEach { id -> attach(id) }
    }

    fun stop() {
        val toCancel =
            synchronized(lock) {
                running = false
                statuses.clear()
                subscriptions.toList().also { subscriptions.clear() }
            }
        toCancel.forEach { it.cancel() }
    }

    private fun attach(id: SourceId) {
        val source = registry.source(id)
        if (source == null) {
            update(id, SourceStatus.UNAVAILABLE)
            return
        }
        val subscription = source.subscribe { state -> update(id, SourceStatus.of(state)) }
        val keep =
            synchronized(lock) {
                if (running) subscriptions += subscription
                running
            }
        if (!keep) subscription.cancel()
    }

    private fun update(
        id: SourceId,
        status: SourceStatus,
    ) {
        val snapshot =
            synchronized(lock) {
                if (!running) return
                statuses[id] = status
                statuses.toMap()
            }
        onChange(snapshot)
    }
}
