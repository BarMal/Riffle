package com.riffle.app.screenshots

import com.riffle.app.launcher.expressions.FakeExpressionImageLoader
import com.riffle.app.launcher.sources.AppSourceDependencies
import com.riffle.app.launcher.sources.BuiltInSourceDependencies
import com.riffle.app.launcher.sources.CalendarSourceDependencies
import com.riffle.app.launcher.sources.InstalledAppSnapshot
import com.riffle.app.launcher.sources.MediaSourceDependencies
import com.riffle.app.launcher.sources.NotificationSourceDependencies
import com.riffle.app.launcher.sources.SourceChangeSource
import com.riffle.app.launcher.sources.streamSourceRegistry
import com.riffle.app.launcher.stream.CachedStreamLayoutRepository
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.app.launcher.stream.StreamLayoutStorePort
import com.riffle.app.launcher.stream.enabledStreamRegistry
import com.riffle.app.launcher.stream.refreshingAlarmProvider
import com.riffle.app.launcher.stream.streamLoadingScheduler
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.stream.NotificationOwnAction
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamActionGateway
import com.riffle.core.domain.launcher.stream.StreamActionResult
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.StreamContentProvider
import com.riffle.core.domain.launcher.stream.StreamContext
import com.riffle.core.domain.launcher.stream.StreamLayouts
import com.riffle.core.domain.launcher.stream.StreamLoadingRule
import com.riffle.core.domain.launcher.workspace.ItemSource
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceRegistry
import com.riffle.core.domain.launcher.workspace.settings.InMemoryDisabledSourcesStore
import com.riffle.core.domain.launcher.workspace.settings.StoredSourceEnablement
import com.riffle.core.domain.launcher.workspace.sources.CalendarEvent
import com.riffle.core.domain.launcher.workspace.sources.MediaSessionInfo
import com.riffle.core.domain.launcher.workspace.sources.MediaSessionReader
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.util.concurrent.Executor

private const val STREAM_HOST_TEST_NOW = 1_767_225_600_000L

/** A [StreamHost] over no sources, with a fixed clock: the Stream draws its states, the Dock its tiles. */
internal fun emptyStreamHost(): StreamHost =
    StreamHost(
        provider =
            StreamContentProvider(
                object : SourceRegistry {
                    override fun descriptors(): List<SourceDescriptor> = emptyList()

                    override fun source(id: SourceId): ItemSource? = null
                },
                context = { StreamContext(nowEpochMillis = STREAM_HOST_TEST_NOW) },
            ),
        layouts =
            CachedStreamLayoutRepository(
                object : StreamLayoutStorePort {
                    override suspend fun read() = null

                    override suspend fun write(layouts: StreamLayouts) = Unit
                },
                CoroutineScope(Dispatchers.Unconfined + Job()),
            ),
        actions = { _, _ -> StreamActionResult.NotOffered },
        imageLoader = FakeExpressionImageLoader(),
        formats = FixedStreamFormats(),
    )

/**
 * A [StreamHost] built the way the activity builds it (`streamSourceRegistry`, `enabledStreamRegistry`, a real
 * `StreamContentProvider` with the production Loading deadline) with only the platform services faked, the item
 * sources reading on [executor], a real thread like the device's. [activeNotifications] are what the notification
 * listener holds; [installedApps] what the package manager lists.
 */
@Suppress("LongParameterList")
internal fun realWiringStreamHost(
    executor: Executor,
    calendar: SourceAccess = SourceAccess.GRANTED,
    notifications: NotificationAccessStatus = NotificationAccessStatus.GRANTED,
    usage: SourceAccess = SourceAccess.GRANTED,
    activeNotifications: List<LauncherNotification> = emptyList(),
    /** The notifications the listener holds now, when they change during the test (else [activeNotifications]). */
    activeNotificationsNow: (() -> List<LauncherNotification>)? = null,
    /** Told when the listener's notifications changed; [SourceChangeSource.NONE] when they never do. */
    notificationChanges: SourceChangeSource = SourceChangeSource.NONE,
    /** The notification's own action buttons (ids and titles) as the listener holds them; none by default. */
    ownActions: (LauncherNotificationKey) -> List<NotificationOwnAction> = { emptyList() },
    installedApps: List<InstalledApp> = emptyList(),
    loadingTimeoutMillis: Long = StreamLoadingRule.TIMEOUT_MILLIS,
    /** Which notifications take an inline reply and a snooze (none by default, as on the activity's first read). */
    canReply: (LauncherNotificationKey) -> Boolean = { false },
    canSnooze: (LauncherNotificationKey) -> Boolean = { false },
    /** The active media sessions (none by default). */
    mediaSessions: List<MediaSessionInfo> = emptyList(),
    /** What an action on content does; refuses everything by default. */
    actions: StreamActionGateway =
        object : StreamActionGateway {
            override fun perform(
                content: StreamContent,
                action: StreamAction,
            ): StreamActionResult = StreamActionResult.NotOffered
        },
): StreamHost {
    val now = StreamFixtures.NOW
    val deps =
        BuiltInSourceDependencies(
            executor = executor,
            nowEpochMillis = { now },
            apps =
                AppSourceDependencies(
                    installedApps = {
                        InstalledAppSnapshot(
                            installedApps,
                            mapOf(AppProfile.personal().id to AppProfileContentVisibility.VISIBLE),
                        )
                    },
                    shortcuts = { emptyMap() },
                    recentApps = { emptyList() },
                    recentAppsAccess = { usage },
                ),
            notifications =
                NotificationSourceDependencies(
                    repository = { activeNotificationsNow?.invoke() ?: activeNotifications },
                    access = { notifications },
                    changes = notificationChanges,
                    hideRules = { emptyList() },
                    canReply = canReply,
                    canSnooze = canSnooze,
                    ownActions = ownActions,
                ),
            calendar =
                CalendarSourceDependencies(
                    repository = { _, _ -> emptyList() },
                    access = { calendar },
                    agendaRepository =
                        { from, _ -> listOf(CalendarEvent("e1", "Standup", from + 600_000, from + 1_200_000)) },
                ),
            media =
                if (mediaSessions.isEmpty()) {
                    MediaSourceDependencies.NONE
                } else {
                    MediaSourceDependencies(
                        sessions =
                            object : MediaSessionReader {
                                override fun activeSessions(): List<MediaSessionInfo> = mediaSessions
                            },
                    )
                },
        )
    val enablement = StoredSourceEnablement(InMemoryDisabledSourcesStore())
    val registry = enabledStreamRegistry(streamSourceRegistry(deps), enablement)
    return StreamHost(
        provider =
            StreamContentProvider(
                registry = registry,
                alarms = refreshingAlarmProvider(executor, SourceChangeSource.NONE) { null },
                scheduler = streamLoadingScheduler(),
                loadingTimeoutMillis = loadingTimeoutMillis,
            ) { StreamContext(nowEpochMillis = now) },
        layouts =
            CachedStreamLayoutRepository(
                object : StreamLayoutStorePort {
                    override suspend fun read(): StreamLayouts? = null

                    override suspend fun write(layouts: StreamLayouts) = Unit
                },
                CoroutineScope(Dispatchers.Unconfined + Job()),
            ),
        actions = actions,
        imageLoader = FakeExpressionImageLoader(),
        formats = FixedStreamFormats(),
    )
}
