package com.riffle.app.launcher

import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.search.LauncherSearchResult
import com.riffle.core.domain.launcher.workspace.sources.CalendarAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellViewModelPermissionStatusTest {
    @Test
    fun refreshesHomeSettingSearchSummaryWhenPlatformStatusesChange() {
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

        assertEquals(
            "Notifications allowed · Home set · Floating dock allowed",
            permissionsResult.subtitle,
        )
    }

    @Test
    fun calendarAccessStartsUnknownAndOnlyReflectsPlatformStatus() {
        val viewModel = LauncherShellViewModel(firstRunRepository = FakeFirstRunRepository())
        assertEquals(CalendarAccessStatus.UNKNOWN, viewModel.state.value.calendarAccessStatus)

        viewModel.onCalendarAccessStatusChanged(CalendarAccessStatus.DENIED_PERMANENTLY)

        assertEquals(CalendarAccessStatus.DENIED_PERMANENTLY, viewModel.state.value.calendarAccessStatus)
        assertEquals(
            CalendarAccessStatus.DENIED_PERMANENTLY,
            viewModel.state.value.settingsSurfaceState().calendarAccessStatus,
        )
    }

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }
}
