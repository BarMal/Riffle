package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.LauncherNotificationRepository
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellNotificationStateTest {
    @Test
    fun loadsNotificationGroupsIntoInitialState() {
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                platformDependencies =
                    LauncherShellPlatformDependencies(
                        epochMillisProvider = FixedEpochMillisProvider(nowEpochMillis = 10 * 60 * 1_000L),
                        notificationRepository =
                            FakeNotificationRepository(
                                notifications =
                                    listOf(
                                        notification(
                                            key = "camera-1",
                                            packageName = "com.riffle.camera",
                                            category = NotificationCategory.MESSAGE,
                                        ),
                                        notification(
                                            key = "camera-2",
                                            packageName = "com.riffle.camera",
                                            category = NotificationCategory.MESSAGE,
                                        ),
                                    ),
                            ),
                    ),
            )

        val group = viewModel.state.value.notificationGroupsByApp.single()

        assertEquals(AppPackageName("com.riffle.camera"), group.packageName)
        assertEquals(2, group.count)
        assertEquals(NotificationAgeBucket.RECENT, group.latestAgeBucket)
        assertEquals(2, viewModel.state.value.notificationCountsByCategory[NotificationCategory.MESSAGE])
    }

    @Test
    fun refreshesNotificationGroups() {
        val repository = FakeNotificationRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                platformDependencies =
                    LauncherShellPlatformDependencies(
                        notificationRepository = repository,
                        epochMillisProvider = FixedEpochMillisProvider(nowEpochMillis = 10 * 60 * 1_000L),
                    ),
            )

        repository.notifications =
            listOf(
                notification(
                    key = "camera-1",
                    packageName = "com.riffle.camera",
                    category = NotificationCategory.MESSAGE,
                ),
                notification(
                    key = "mail-1",
                    packageName = "com.riffle.mail",
                    category = NotificationCategory.EMAIL,
                ),
            )
        runBlocking { viewModel.refreshInstalledApps().join() }

        assertEquals(
            listOf(AppPackageName("com.riffle.camera"), AppPackageName("com.riffle.mail")),
            viewModel.state.value.notificationGroupsByApp.map { group -> group.packageName },
        )
        assertEquals(1, viewModel.state.value.notificationCountsByCategory[NotificationCategory.MESSAGE])
        assertEquals(1, viewModel.state.value.notificationCountsByCategory[NotificationCategory.EMAIL])
    }

    @Test
    fun refreshNotificationsDoesNotRefreshInstalledApps() {
        val installedAppRepository = FakeInstalledAppRepository(apps = listOf(app(label = "Camera")))
        val notificationRepository = FakeNotificationRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = installedAppRepository,
                platformDependencies =
                    LauncherShellPlatformDependencies(
                        notificationRepository = notificationRepository,
                    ),
            )

        installedAppRepository.apps = listOf(app(label = "Calendar"))
        notificationRepository.notifications =
            listOf(notification(key = "calendar-1", packageName = "com.riffle.calendar"))
        runBlocking { viewModel.refreshNotifications().join() }

        assertEquals(listOf("Camera"), viewModel.state.value.installedApps.map { app -> app.label })
        assertEquals(
            listOf(AppPackageName("com.riffle.calendar")),
            viewModel.state.value.notificationGroupsByApp.map { group -> group.packageName },
        )
    }

    @Test
    fun removesStaleClearableNotificationsFromLauncherState() {
        val nowEpochMillis = 10 * 24 * 60 * 60 * 1_000L
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                platformDependencies =
                    LauncherShellPlatformDependencies(
                        epochMillisProvider = FixedEpochMillisProvider(nowEpochMillis = nowEpochMillis),
                        notificationRepository =
                            FakeNotificationRepository(
                                notifications =
                                    listOf(
                                        notification(
                                            key = "old-clearable",
                                            packageName = "com.riffle.mail",
                                            category = NotificationCategory.EMAIL,
                                            canDismiss = true,
                                            postedAtEpochMillis = nowEpochMillis - 8 * 24 * 60 * 60 * 1_000L,
                                        ),
                                        notification(
                                            key = "old-pinned",
                                            packageName = "com.riffle.music",
                                            category = NotificationCategory.SERVICE,
                                            canDismiss = false,
                                            postedAtEpochMillis = nowEpochMillis - 8 * 24 * 60 * 60 * 1_000L,
                                        ),
                                    ),
                            ),
                    ),
            )

        assertEquals(
            listOf(AppPackageName("com.riffle.music")),
            viewModel.state.value.notificationGroupsByApp.map { group -> group.packageName },
        )
        assertEquals(null, viewModel.state.value.notificationCountsByCategory[NotificationCategory.EMAIL])
        assertEquals(1, viewModel.state.value.notificationCountsByCategory[NotificationCategory.SERVICE])
    }

    @Test
    fun excludesHiddenAppNotificationsFromLauncherState() {
        val camera = app(label = "Camera")
        val docs = app(label = "Docs")
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera, docs)),
                appVisibilityRepository = FakeAppVisibilityRepository(hiddenApps = setOf(docs.identity)),
                platformDependencies =
                    LauncherShellPlatformDependencies(
                        notificationRepository =
                            FakeNotificationRepository(
                                notifications =
                                    listOf(
                                        notification(key = "camera-1", packageName = camera.identity.packageName.value),
                                        notification(key = "docs-1", packageName = docs.identity.packageName.value),
                                    ),
                            ),
                    ),
            )

        assertEquals(
            listOf(camera.identity.packageName),
            viewModel.state.value.notificationGroupsByApp.map { group -> group.packageName },
        )
        assertEquals(1, viewModel.state.value.notificationCountsByPackage[camera.identity.packageName])
        assertEquals(null, viewModel.state.value.notificationCountsByPackage[docs.identity.packageName])
    }

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }

    private class FakeNotificationRepository(
        var notifications: List<LauncherNotification> = emptyList(),
    ) : LauncherNotificationRepository {
        override fun activeNotifications(): List<LauncherNotification> = notifications
    }

    private class FakeInstalledAppRepository(
        var apps: List<InstalledApp> = emptyList(),
    ) : InstalledAppRepository {
        override fun installedApps(): List<InstalledApp> = apps
    }

    private class FakeAppVisibilityRepository(
        var hiddenApps: Set<AppIdentity> = emptySet(),
    ) : AppVisibilityRepository {
        override fun hiddenAppIdentities(): Set<AppIdentity> = hiddenApps

        override fun hideApp(identity: AppIdentity) {
            hiddenApps = hiddenApps + identity
        }

        override fun showApp(identity: AppIdentity) {
            hiddenApps = hiddenApps - identity
        }
    }

    private class FixedEpochMillisProvider(
        private val nowEpochMillis: Long,
    ) : EpochMillisProvider {
        override fun nowEpochMillis(): Long = nowEpochMillis
    }

    private fun notification(
        key: String,
        packageName: String,
        category: NotificationCategory = NotificationCategory.UNKNOWN,
        canDismiss: Boolean = false,
        postedAtEpochMillis: Long = 1_000L,
    ): LauncherNotification =
        LauncherNotification(
            key = LauncherNotificationKey(key),
            packageName = AppPackageName(packageName),
            category = category,
            canDismiss = canDismiss,
            postedAtEpochMillis = postedAtEpochMillis,
        )

    private fun app(
        label: String,
        profile: AppProfile = AppProfile.personal(),
    ): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                    profile = profile,
                ),
            label = label,
        )
}
