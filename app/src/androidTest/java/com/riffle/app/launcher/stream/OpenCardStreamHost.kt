package com.riffle.app.launcher.stream

import com.riffle.app.launcher.expressions.NoExpressionImageLoader
import com.riffle.app.launcher.sources.AppSourceDependencies
import com.riffle.app.launcher.sources.BuiltInSourceDependencies
import com.riffle.app.launcher.sources.CalendarSourceDependencies
import com.riffle.app.launcher.sources.InstalledAppSnapshot
import com.riffle.app.launcher.sources.MediaSourceDependencies
import com.riffle.app.launcher.sources.NotificationSourceDependencies
import com.riffle.app.launcher.sources.SourceChangeSource
import com.riffle.app.launcher.sources.streamSourceRegistry
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.stream.StreamActionGateway
import com.riffle.core.domain.launcher.stream.StreamContentProvider
import com.riffle.core.domain.launcher.stream.StreamContext
import com.riffle.core.domain.launcher.stream.StreamLayouts
import com.riffle.core.domain.launcher.workspace.settings.InMemoryDisabledSourcesStore
import com.riffle.core.domain.launcher.workspace.settings.StoredSourceEnablement
import com.riffle.core.domain.launcher.workspace.sources.MediaSessionInfo
import com.riffle.core.domain.launcher.workspace.sources.MediaSessionReader
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.util.concurrent.Executor

/**
 * A [StreamHost] built the way the activity builds it (the real registry, provider and mappers) over active
 * notifications and media sessions the test supplies, with every platform service faked and [actions] standing in for
 * the action gateway. Each notification takes an inline reply and a snooze. The Stream's other sections have no access.
 */
internal fun openCardStreamHost(
    executor: Executor,
    notifications: List<LauncherNotification>,
    sessions: List<MediaSessionInfo>,
    installedApps: List<InstalledApp>,
    actions: StreamActionGateway,
    nowEpochMillis: Long,
): StreamHost {
    val deps =
        BuiltInSourceDependencies(
            executor = executor,
            nowEpochMillis = { nowEpochMillis },
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
                    recentAppsAccess = { SourceAccess.REQUIRED },
                ),
            notifications =
                NotificationSourceDependencies(
                    repository = { notifications },
                    access = { NotificationAccessStatus.GRANTED },
                    changes = SourceChangeSource.NONE,
                    hideRules = { emptyList() },
                    canReply = { _: LauncherNotificationKey -> true },
                    canSnooze = { _: LauncherNotificationKey -> true },
                ),
            calendar =
                CalendarSourceDependencies(
                    repository = { _, _ -> emptyList() },
                    access = { SourceAccess.REQUIRED },
                ),
            media =
                MediaSourceDependencies(
                    sessions =
                        object : MediaSessionReader {
                            override fun activeSessions(): List<MediaSessionInfo> = sessions
                        },
                ),
        )
    val enablement = StoredSourceEnablement(InMemoryDisabledSourcesStore())
    return StreamHost(
        provider =
            StreamContentProvider(
                registry = enabledStreamRegistry(streamSourceRegistry(deps), enablement),
                alarms = refreshingAlarmProvider(executor, SourceChangeSource.NONE) { null },
                scheduler = streamLoadingScheduler(),
            ) { StreamContext(nowEpochMillis = nowEpochMillis) },
        layouts =
            CachedStreamLayoutRepository(
                object : StreamLayoutStorePort {
                    override suspend fun read(): StreamLayouts? = null

                    override suspend fun write(layouts: StreamLayouts) = Unit
                },
                CoroutineScope(Dispatchers.Unconfined + Job()),
            ),
        actions = actions,
        imageLoader = NoExpressionImageLoader,
    )
}
