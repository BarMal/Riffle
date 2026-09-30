package com.riffle.app.launcher.sources

import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppShortcutRepository
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppCatalog
import com.riffle.core.domain.launcher.apps.InstalledAppRefreshResult
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.apps.RecentAppRepository
import com.riffle.core.domain.launcher.apps.withHiddenApps
import com.riffle.core.domain.launcher.notifications.LauncherNotificationRepository
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationHideRule
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemSource
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceRegistry
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.sources.AppItemMapper
import com.riffle.core.domain.launcher.workspace.sources.CalendarEventRepository
import com.riffle.core.domain.launcher.workspace.sources.CalendarItemMapper
import com.riffle.core.domain.launcher.workspace.sources.NotificationItemInput
import com.riffle.core.domain.launcher.workspace.sources.NotificationItemMapper
import com.riffle.core.domain.launcher.workspace.sources.SharedSourceStream
import com.riffle.core.domain.launcher.workspace.sources.ShortcutItemMapper
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import com.riffle.core.domain.launcher.workspace.sources.sourceStateFor
import com.riffle.core.domain.launcher.workspace.sources.toSourceAccess
import java.util.concurrent.Executor

/** Installed apps with the user's hidden-app choices applied, plus per-profile notification visibility. */
internal data class InstalledAppSnapshot(
    val apps: List<InstalledApp>,
    val profileContentVisibility: Map<AppProfileId, AppProfileContentVisibility>,
)

/** Null means the platform could not answer right now. Blocking: call off the main thread. */
internal fun interface InstalledAppSnapshotProvider {
    fun snapshot(): InstalledAppSnapshot?
}

internal class RepositoryInstalledAppSnapshotProvider(
    private val installedApps: InstalledAppRepository,
    private val appVisibility: AppVisibilityRepository,
) : InstalledAppSnapshotProvider {
    override fun snapshot(): InstalledAppSnapshot? =
        when (val result = installedApps.refreshResult()) {
            is InstalledAppRefreshResult.Authoritative ->
                InstalledAppSnapshot(result.apps.hidden(), result.profileContentVisibility)

            is InstalledAppRefreshResult.Partial ->
                InstalledAppSnapshot(result.apps.hidden(), result.profileContentVisibility)

            InstalledAppRefreshResult.Unavailable -> null
        }

    private fun List<InstalledApp>.hidden(): List<InstalledApp> = withHiddenApps(appVisibility.hiddenAppIdentities())
}

internal class AppSourceDependencies(
    val installedApps: InstalledAppSnapshotProvider,
    val shortcuts: AppShortcutRepository,
    val recentApps: RecentAppRepository,
    /** Usage access for recents. Checking it must never prompt. */
    val recentAppsAccess: () -> SourceAccess,
    /** Package, profile and shortcut changes. [SourceChangeSource.NONE] until the platform observer is wired. */
    val changes: SourceChangeSource = SourceChangeSource.NONE,
)

internal class NotificationSourceDependencies(
    val repository: LauncherNotificationRepository,
    /** Never prompts; only reports what the user already enabled. */
    val access: () -> NotificationAccessStatus,
    val changes: SourceChangeSource,
    val hideRules: () -> List<NotificationHideRule>,
)

internal class CalendarSourceDependencies(
    val repository: CalendarEventRepository,
    /** Never prompts; only reports whether the user already granted calendar access. */
    val access: () -> SourceAccess,
    /** Calendar changes and permission flips. [SourceChangeSource.NONE] means the source is not `LIVE`. */
    val changes: SourceChangeSource = SourceChangeSource.NONE,
) {
    companion object {
        /** For builds or tests without a platform calendar adapter: the source reports itself unavailable. */
        val UNAVAILABLE =
            CalendarSourceDependencies(
                repository = { _, _ -> emptyList() },
                access = { SourceAccess.UNAVAILABLE },
            )
    }
}

internal class BuiltInSourceDependencies(
    val executor: Executor,
    val nowEpochMillis: () -> Long,
    val apps: AppSourceDependencies,
    val notifications: NotificationSourceDependencies,
    val calendar: CalendarSourceDependencies = CalendarSourceDependencies.UNAVAILABLE,
)

/** A [SourceRegistry] over a fixed set of sources; each id resolves to one shared stream. */
internal class BuiltInSourceRegistry(sources: List<ItemSource>) : SourceRegistry {
    private val byId = sources.associateBy { source -> source.descriptor.id }

    override fun descriptors(): List<SourceDescriptor> = byId.values.map(ItemSource::descriptor)

    override fun source(id: SourceId): ItemSource? = byId[id]
}

/**
 * Builds the WS1 sources over the existing repositories. Nothing here starts work until a source gets its
 * first observer, nothing prompts for a permission, and each source keeps exactly one upstream however many
 * widgets observe it. Items carry only lazy image keys and are never persisted.
 */
internal fun builtInSourceRegistry(deps: BuiltInSourceDependencies): SourceRegistry =
    BuiltInSourceRegistry(
        listOf(
            allAppsSource(deps),
            recentAppsSource(deps),
            quickActionsSource(deps),
            notificationsSource(deps),
            mediaSource(deps),
            calendarSource(deps),
        ),
    )

private fun stream(
    deps: BuiltInSourceDependencies,
    id: SourceId,
    capabilities: Set<SourceCapability>,
    changes: SourceChangeSource,
    load: () -> SourceState,
): SharedSourceStream =
    SharedSourceStream(
        descriptor = SourceDescriptor(id, capabilities + liveIf(changes)),
    ) { sink -> RefreshingUpstream(deps.executor, changes, load).connect(sink) }

private fun liveIf(changes: SourceChangeSource): Set<SourceCapability> =
    if (changes.isLive) setOf(SourceCapability.LIVE) else emptySet()

private fun allAppsSource(deps: BuiltInSourceDependencies): SharedSourceStream =
    stream(
        deps,
        SourceIds.ALL_APPS,
        setOf(SourceCapability.GROUPABLE, SourceCapability.SEARCHABLE),
        deps.apps.changes,
    ) {
        deps.apps.installedApps.snapshot()
            ?.let { snapshot -> SourceState.Ready(AppItemMapper().allApps(snapshot.apps)) }
            ?: SourceState.Unavailable
    }

/** Usage stats have no change callback, so recents are read when the stream starts and are not `LIVE`. */
private fun recentAppsSource(deps: BuiltInSourceDependencies): SharedSourceStream =
    stream(deps, SourceIds.RECENT_APPS, emptySet(), SourceChangeSource.NONE) {
        sourceStateFor(deps.apps.recentAppsAccess()) {
            val apps = deps.apps.installedApps.snapshot()?.apps.orEmpty()
            AppItemMapper().recentApps(deps.apps.recentApps.recentAppUsages(), apps)
        }
    }

private fun quickActionsSource(deps: BuiltInSourceDependencies): SharedSourceStream =
    stream(deps, SourceIds.QUICK_ACTIONS, setOf(SourceCapability.GROUPABLE), deps.apps.changes) {
        deps.apps.installedApps.snapshot()
            ?.let { snapshot ->
                val visible = InstalledAppCatalog().visibleApps(snapshot.apps)
                SourceState.Ready(ShortcutItemMapper().quickActions(visible, deps.apps.shortcuts.shortcutsFor(visible)))
            }
            ?: SourceState.Unavailable
    }

private fun notificationsSource(deps: BuiltInSourceDependencies): SharedSourceStream =
    notificationStream(deps, SourceIds.NOTIFICATIONS, setOf(SourceCapability.GROUPABLE, SourceCapability.ACTIONABLE)) {
        NotificationItemMapper().notificationItems(it)
    }

private fun mediaSource(deps: BuiltInSourceDependencies): SharedSourceStream =
    notificationStream(deps, SourceIds.MEDIA, emptySet()) { NotificationItemMapper().mediaItems(it) }

private fun notificationStream(
    deps: BuiltInSourceDependencies,
    id: SourceId,
    capabilities: Set<SourceCapability>,
    map: (NotificationItemInput) -> List<Item>,
): SharedSourceStream {
    val notifications = deps.notifications
    val changes = SourceChangeSource.merge(listOf(notifications.changes, deps.apps.changes))
    return stream(deps, id, capabilities + SourceCapability.PRIVACY_SENSITIVE, changes) {
        sourceStateFor(notifications.access().toSourceAccess()) {
            val snapshot = deps.apps.installedApps.snapshot()
            val labels = snapshot?.apps.orEmpty().associate { app -> app.identity.packageName to app.label }
            map(
                NotificationItemInput(
                    notifications = notifications.repository.activeNotifications(),
                    nowEpochMillis = deps.nowEpochMillis(),
                    hideRules = notifications.hideRules(),
                    profileContentVisibility = snapshot?.profileContentVisibility.orEmpty(),
                    appLabel = labels::get,
                ),
            )
        }
    }
}

private fun calendarSource(deps: BuiltInSourceDependencies): SharedSourceStream =
    stream(deps, SourceIds.CALENDAR, setOf(SourceCapability.PRIVACY_SENSITIVE), deps.calendar.changes) {
        sourceStateFor(deps.calendar.access()) {
            val now = deps.nowEpochMillis()
            CalendarItemMapper().nextEvents(deps.calendar.repository.upcomingEvents(now, CALENDAR_QUERY_LIMIT), now)
        }
    }

private const val CALENDAR_QUERY_LIMIT = 5
