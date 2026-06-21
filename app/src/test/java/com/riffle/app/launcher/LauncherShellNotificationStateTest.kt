package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.LauncherNotificationRepository
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellNotificationStateTest {
    @Test
    fun loadsNotificationGroupsIntoInitialState() {
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                epochMillisProvider = FixedEpochMillisProvider(nowEpochMillis = 10 * 60 * 1_000L),
                notificationRepository =
                    FakeNotificationRepository(
                        notifications =
                            listOf(
                                notification(key = "camera-1", packageName = "com.riffle.camera"),
                                notification(key = "camera-2", packageName = "com.riffle.camera"),
                            ),
                    ),
            )

        val group = viewModel.state.value.notificationGroupsByApp.single()

        assertEquals(AppPackageName("com.riffle.camera"), group.packageName)
        assertEquals(2, group.count)
        assertEquals(NotificationAgeBucket.RECENT, group.latestAgeBucket)
    }

    @Test
    fun refreshesNotificationGroups() {
        val repository = FakeNotificationRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                notificationRepository = repository,
                epochMillisProvider = FixedEpochMillisProvider(nowEpochMillis = 10 * 60 * 1_000L),
            )

        repository.notifications =
            listOf(
                notification(key = "camera-1", packageName = "com.riffle.camera"),
                notification(key = "mail-1", packageName = "com.riffle.mail"),
            )
        viewModel.refreshInstalledApps()

        assertEquals(
            listOf(AppPackageName("com.riffle.camera"), AppPackageName("com.riffle.mail")),
            viewModel.state.value.notificationGroupsByApp.map { group -> group.packageName },
        )
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

    private class FixedEpochMillisProvider(
        private val nowEpochMillis: Long,
    ) : EpochMillisProvider {
        override fun nowEpochMillis(): Long = nowEpochMillis
    }

    private fun notification(
        key: String,
        packageName: String,
    ): LauncherNotification =
        LauncherNotification(
            key = LauncherNotificationKey(key),
            packageName = AppPackageName(packageName),
            postedAtEpochMillis = 1_000L,
        )
}
