package com.riffle.app.launcher

import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.search.LauncherSearchResult
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellViewModelPermissionStatusTest {
    @Test
    fun refreshesPermissionSettingSearchSummaryWhenPlatformStatusesChange() {
        val viewModel = LauncherShellViewModel(firstRunRepository = FakeFirstRunRepository())

        viewModel.onAppActionSelected(LauncherShellAction.SearchQueryChanged("permission"))
        viewModel.onHomeRoleStatusChanged(
            homeRoleStatus = HomeRoleStatus.DEFAULT_HOME,
            notificationAccessStatus = NotificationAccessStatus.GRANTED,
            overlayDockPermissionStatus = OverlayDockPermissionStatus.GRANTED,
        )

        val permissionsResult =
            viewModel.state.value.searchSettingsResults
                .filterIsInstance<LauncherSearchResult.Setting>()
                .single { result -> result.title == "Permissions" }

        assertEquals("Home set, notifications allowed, overlay allowed", permissionsResult.subtitle)
    }

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }
}
