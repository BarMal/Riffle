package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.apps.InstalledAppCatalog
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherAppStateActionCoordinatorTest {
    @Test
    fun refreshInstalledAppsActionStartsInstalledAppRefresh() {
        val harness = Harness()

        val job = harness.coordinator.handle(LauncherShellAction.RefreshInstalledApps)

        assertNotNull(job)
        assertEquals(1, harness.installedAppRefreshCount)
        assertEquals(0, harness.widgetProviderRefreshCount)
    }

    @Test
    fun appVisibilityActionAppliesVisibilityBeforeRefreshingInstalledApps() {
        val repository = FakeAppVisibilityRepository()
        val harness = Harness(appVisibilityRepository = repository)

        val job = harness.coordinator.handle(LauncherShellAction.HideApp(appIdentity))

        assertNotNull(job)
        assertEquals(1, harness.installedAppRefreshCount)
        assertEquals(setOf(appIdentity), repository.hiddenIdentities)
    }

    @Test
    fun appListActionUpdatesStateWithoutRefresh() {
        val harness = Harness()

        val job = harness.coordinator.handle(LauncherShellAction.SearchQueryChanged("camera"))

        assertNull(job)
        assertEquals("camera", harness.state.searchQuery)
        assertEquals(0, harness.installedAppRefreshCount)
        assertEquals(0, harness.widgetProviderRefreshCount)
    }

    @Test
    fun openingWidgetPickerStartsWidgetProviderRefreshAndOpensPicker() {
        val harness = Harness()

        val job = harness.coordinator.handle(LauncherShellAction.OpenWidgetPicker)

        assertNotNull(job)
        assertTrue(harness.state.isWidgetPickerOpen)
        assertEquals(0, harness.installedAppRefreshCount)
        assertEquals(1, harness.widgetProviderRefreshCount)
    }

    @Test
    fun closingWidgetPickerDoesNotRefreshWidgetProviders() {
        val harness = Harness(state = LauncherShellState(isWidgetPickerOpen = true))

        val job = harness.coordinator.handle(LauncherShellAction.CloseWidgetPicker)

        assertNull(job)
        assertEquals(false, harness.state.isWidgetPickerOpen)
        assertEquals(0, harness.widgetProviderRefreshCount)
    }

    private class Harness(
        state: LauncherShellState = LauncherShellState(),
        appVisibilityRepository: AppVisibilityRepository = FakeAppVisibilityRepository(),
    ) {
        var state = state
        var installedAppRefreshCount = 0
        var widgetProviderRefreshCount = 0

        val coordinator =
            LauncherAppStateActionCoordinator(
                appVisibilityRepository = appVisibilityRepository,
                appListActionReducer = LauncherAppListActionReducer(InstalledAppCatalog()),
                widgetPickerActionReducer = LauncherWidgetPickerActionReducer(),
                currentState = { this.state },
                updateState = { state -> this.state = state },
                refreshInstalledApps = { beforeRefresh ->
                    beforeRefresh()
                    installedAppRefreshCount += 1
                    Job()
                },
                refreshWidgetProviders = {
                    widgetProviderRefreshCount += 1
                    Job()
                },
            )
    }

    private class FakeAppVisibilityRepository : AppVisibilityRepository {
        var hiddenIdentities: Set<AppIdentity> = emptySet()

        override fun hiddenAppIdentities(): Set<AppIdentity> = hiddenIdentities

        override fun hideApp(identity: AppIdentity) {
            hiddenIdentities = hiddenIdentities + identity
        }

        override fun showApp(identity: AppIdentity) {
            hiddenIdentities = hiddenIdentities - identity
        }
    }

    private companion object {
        val appIdentity =
            AppIdentity(
                packageName = AppPackageName("com.example.app"),
                activityName = AppActivityName(".MainActivity"),
            )
    }
}
