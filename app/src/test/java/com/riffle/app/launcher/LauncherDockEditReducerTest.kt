package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockEngine
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LauncherDockEditReducerTest {
    @Test
    fun appliesDockEditsToActiveLayout() {
        val repository = FakeHomeLayoutRepository()
        val state = LauncherShellState()

        val updatedState =
            reducer(repository).reduce(
                state = state,
                action = LauncherShellAction.AddAppToDock(phone),
            )

        assertEquals(listOf("Phone"), updatedState.homeLayout.dock.items.filterIsInstance<AppShortcutItem>().labels)
        assertEquals(updatedState.homeLayout, repository.savedLayoutSet?.activeLayout)
    }

    @Test
    fun ignoresRejectedDockEdits() {
        val repository = FakeHomeLayoutRepository()
        val state = LauncherShellState()

        val updatedState =
            reducer(repository).reduce(
                state = state,
                action = LauncherShellAction.OpenHome,
            )

        assertSame(state, updatedState)
        assertEquals(null, repository.savedLayout)
    }

    @Test
    fun appliesDockConfigurationToSettingsTargetLayout() {
        val layout = HomeLayoutDefaults.standard()
        val layoutSet = HomeLayoutSet.fromLayout(layout)
        val repository = FakeHomeLayoutRepository(savedLayoutSet = layoutSet)
        val state =
            LauncherShellState(
                destination = ShellDestination.SETTINGS,
                homeLayout = layout,
                homeLayoutSet = layoutSet,
            )

        val updatedState =
            reducer(repository).reduce(
                state = state,
                action = LauncherShellAction.SelectDockCapacity(capacity = 7),
            )

        assertEquals(7, updatedState.homeLayout.dock.capacity)
        assertEquals(7, repository.savedLayoutSet?.activeLayout?.dock?.capacity)
    }

    private fun reducer(repository: HomeLayoutRepository): LauncherDockEditReducer =
        LauncherDockEditReducer(
            dockEngine = DockEngine(),
            homeLayoutRepository = repository,
        )

    private class FakeHomeLayoutRepository(
        var savedLayout: HomeLayout? = null,
        var savedLayoutSet: HomeLayoutSet? = null,
    ) : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout? = savedLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            savedLayout = layout
        }

        override fun loadHomeLayoutSet(): HomeLayoutSet? = savedLayoutSet ?: super.loadHomeLayoutSet()

        override fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) {
            savedLayoutSet = layoutSet
        }
    }

    private companion object {
        val phoneIdentity =
            AppIdentity(
                packageName = AppPackageName("com.example.phone"),
                activityName = AppActivityName(".MainActivity"),
            )
        val phone = InstalledApp(identity = phoneIdentity, label = "Phone")
    }

    private val List<AppShortcutItem>.labels: List<String>
        get() = map { item -> item.label }
}
