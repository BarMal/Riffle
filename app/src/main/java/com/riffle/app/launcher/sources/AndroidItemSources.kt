package com.riffle.app.launcher.sources

import android.content.ContentResolver
import com.riffle.app.launcher.apps.AndroidRecentAppRepository
import com.riffle.app.launcher.apps.PackageManagerInstalledAppRepository
import com.riffle.app.launcher.calendar.AndroidCalendarChangeSource
import com.riffle.app.launcher.calendar.AndroidCalendarEventRepository
import com.riffle.app.launcher.calendar.CalendarAccessChanges
import com.riffle.app.launcher.calendar.CalendarAccessGateway
import com.riffle.app.launcher.calendar.sourceAccess
import com.riffle.app.launcher.notifications.AndroidNotificationAccessGateway
import com.riffle.app.launcher.notifications.DataStoreActiveNotificationRepository
import com.riffle.app.launcher.notifications.RiffleNotificationListenerConnection
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.notifications.NotificationHideRule
import com.riffle.core.domain.launcher.workspace.SourceRegistry
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import java.util.concurrent.Executors

/**
 * Wires the built-in item sources to the launcher's existing platform repositories (pass the instances
 * already held by the activity dependencies). Reads run on one background thread, nothing prompts for a
 * permission, and nothing starts until a source gets an observer.
 *
 * Not wired into any UI yet (WS3/WS6). Known gaps: package/shortcut changes have no change source yet, so
 * the app and shortcut sources refresh when their stream starts; hide-rule edits are picked up on the next
 * refresh; calendar events that end while the stream is running leave on the next change or restart.
 */
internal fun androidItemSources(
    installedApps: PackageManagerInstalledAppRepository,
    appVisibility: AppVisibilityRepository,
    recentApps: AndroidRecentAppRepository,
    notificationRepository: DataStoreActiveNotificationRepository,
    notificationAccess: AndroidNotificationAccessGateway,
    hideRules: () -> List<NotificationHideRule>,
    calendar: CalendarSourceDependencies,
): SourceRegistry {
    val snapshotChanges =
        SourceChangeSource { onChanged -> notificationRepository.observeActiveNotifications(onChanged) }
    val listenerConnectionChanges =
        SourceChangeSource { onChanged -> RiffleNotificationListenerConnection.observeConnection(onChanged) }
    return builtInSourceRegistry(
        BuiltInSourceDependencies(
            executor =
                Executors.newSingleThreadExecutor { task ->
                    Thread(task, "riffle-item-sources").apply { isDaemon = true }
                },
            nowEpochMillis = System::currentTimeMillis,
            apps =
                AppSourceDependencies(
                    installedApps = RepositoryInstalledAppSnapshotProvider(installedApps, appVisibility),
                    shortcuts = installedApps,
                    recentApps = recentApps,
                    recentAppsAccess = {
                        if (recentApps.canReadRecentApps()) SourceAccess.GRANTED else SourceAccess.REQUIRED
                    },
                ),
            notifications =
                NotificationSourceDependencies(
                    repository = notificationRepository,
                    access = notificationAccess::getNotificationAccessStatus,
                    changes = SourceChangeSource.merge(listOf(snapshotChanges, listenerConnectionChanges)),
                    hideRules = hideRules,
                ),
            calendar = calendar,
        ),
    )
}

/**
 * The real calendar source wiring. Reads need `READ_CALENDAR`, which is only ever requested by an explicit
 * user action (`CalendarAccessCoordinator`); until granted the source reports `PermissionRequired` and
 * queries nothing. [accessChanges] must be fed the status refreshes so a grant or revocation reloads it.
 * The content observer is registered only while the stream has observers.
 */
internal fun androidCalendarSourceDependencies(
    contentResolver: ContentResolver,
    access: CalendarAccessGateway,
    accessChanges: CalendarAccessChanges,
): CalendarSourceDependencies =
    CalendarSourceDependencies(
        repository = AndroidCalendarEventRepository(contentResolver),
        access = access::sourceAccess,
        changes =
            SourceChangeSource.merge(listOf(AndroidCalendarChangeSource(contentResolver), accessChanges)),
    )
