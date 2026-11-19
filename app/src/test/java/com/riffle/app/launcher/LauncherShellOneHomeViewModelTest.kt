package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.settings.HomeBehaviourSettings
import com.riffle.core.domain.launcher.settings.HomeMode
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** One Home (ADR 0003, Amendment 8): the app-library migration, "Add new apps to Home" and "Add to Home". */
class LauncherShellOneHomeViewModelTest {
    private class Apps(var apps: List<InstalledApp>) : InstalledAppRepository {
        override fun installedApps(): List<InstalledApp> = apps
    }

    private class Layouts(var saved: HomeLayout? = null) : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout? = saved

        override fun saveHomeLayout(layout: HomeLayout) {
            saved = layout
        }
    }

    private class Settings(var saved: LauncherSettings?) : LauncherSettingsRepository {
        override fun loadLauncherSettings(): LauncherSettings? = saved

        override fun saveLauncherSettings(settings: LauncherSettings) {
            saved = settings
        }
    }

    private class FirstRun : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }

    private fun app(label: String) =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                    profile = AppProfile.personal(),
                ),
            label = label,
        )

    private fun viewModel(
        apps: Apps,
        settings: Settings,
        layouts: Layouts = Layouts(),
    ) = LauncherShellViewModel(
        firstRunRepository = FirstRun(),
        installedAppRepository = apps,
        homeLayoutRepository = layouts,
        launcherSettingsRepository = settings,
    )

    private fun LauncherShellViewModel.labels(): List<String> =
        state.value.homeLayout.pages.filter { it.type == LauncherPageType.Home }
            .flatMap { it.items }.filterIsInstance<AppShortcutItem>().map { it.label }

    @Test
    fun aStoredAppLibraryHomeBecomesAPlacedHomeOfEveryVisibleAppInOrderOnce() {
        val apps = Apps(listOf(app("Zoo"), app("Alarm"), app("Maps"), app("Camera")))
        val settings = Settings(LauncherSettings(home = HomeBehaviourSettings(mode = HomeMode.AppLibrary)))
        val layouts = Layouts()
        val model = viewModel(apps, settings, layouts)

        runBlocking { model.refreshInstalledApps().join() }

        assertEquals(listOf("Alarm", "Camera", "Maps", "Zoo"), model.labels())
        assertEquals(HomeMode.PlacedItems, model.state.value.launcherSettings.home.mode)
        assertEquals(HomeMode.PlacedItems, settings.saved!!.home.mode)
        // Idempotent: a later refresh (and a user's removal) is never undone by running it again.
        val first = model.state.value.homeLayout
        runBlocking { model.refreshInstalledApps().join() }
        assertEquals(first, model.state.value.homeLayout)
    }

    @Test
    fun theMigrationLeavesAStandardHomeAlone() {
        val apps = Apps(listOf(app("Zoo"), app("Alarm")))
        val settings = Settings(LauncherSettings())
        val model = viewModel(apps, settings)

        runBlocking { model.refreshInstalledApps().join() }

        assertEquals(emptyList<String>(), model.labels())
        assertEquals(HomeMode.PlacedItems, model.state.value.launcherSettings.home.mode)
    }

    @Test
    fun anAppInstalledLaterIsPlacedAfterTheLastItemWhenAddNewAppsIsOn() {
        val apps = Apps(listOf(app("Alarm"), app("Camera")))
        val settings = Settings(LauncherSettings(home = HomeBehaviourSettings(mode = HomeMode.AppLibrary)))
        val model = viewModel(apps, settings)
        runBlocking { model.refreshInstalledApps().join() }

        apps.apps = apps.apps + app("Notes")
        model.onPackageInstalled(AppPackageName("com.riffle.notes"), AppProfile.personal())
        runBlocking { model.refreshInstalledApps().join() }

        assertEquals(listOf("Alarm", "Camera", "Notes"), model.labels())
    }

    @Test
    fun anAppInstalledLaterOnlyAppearsInTheLibraryWhenAddNewAppsIsOff() {
        val apps = Apps(listOf(app("Alarm")))
        val off = LauncherSettings(home = HomeBehaviourSettings(addNewApps = false))
        val model = viewModel(apps, Settings(off))
        runBlocking { model.refreshInstalledApps().join() }

        apps.apps = apps.apps + app("Notes")
        model.onPackageInstalled(AppPackageName("com.riffle.notes"), AppProfile.personal())
        runBlocking { model.refreshInstalledApps().join() }

        assertEquals(emptyList<String>(), model.labels())
        assertEquals(listOf("Alarm", "Notes"), model.state.value.installedApps.map { it.label })
    }

    @Test
    fun appsPresentAtFirstRunAreNotPlacedAsNewApps() {
        val apps = Apps(listOf(app("Alarm"), app("Camera")))
        val model = viewModel(apps, Settings(LauncherSettings()))

        runBlocking { model.refreshInstalledApps().join() }

        assertEquals(emptyList<String>(), model.labels())
    }

    @Test
    fun addToHomePlacesAfterTheLastItemAndNeverTwice() {
        val apps = Apps(listOf(app("Alarm"), app("Camera"), app("Maps")))
        val model = viewModel(apps, Settings(LauncherSettings()))
        runBlocking { model.refreshInstalledApps().join() }
        model.onAddAppToHome(app("Maps"))
        model.onAddAppToHome(app("Alarm"))
        model.onAddAppToHome(app("Maps"))

        assertEquals(listOf("Maps", "Alarm"), model.labels())
        assertFalse(model.labels().groupingBy { it }.eachCount().any { it.value > 1 })
    }

    private fun backup(mode: HomeMode) =
        LauncherBackupDocument(
            homeLayoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()),
            launcherSettings = LauncherSettings(home = HomeBehaviourSettings(mode = mode)),
        )

    @Test
    fun aBackupMadeInTheAppLibraryModeRestoresAsAPlacedHomeAndAStandardOneIsKept() {
        val apps = Apps(listOf(app("Zoo"), app("Alarm")))
        val model = viewModel(apps, Settings(LauncherSettings()))
        runBlocking { model.refreshInstalledApps().join() }

        model.onLauncherSettingsActionSelected(LauncherShellAction.ImportLauncherBackup(backup(HomeMode.AppLibrary)))
        assertEquals(listOf("Alarm", "Zoo"), model.labels())
        assertEquals(HomeMode.PlacedItems, model.state.value.launcherSettings.home.mode)

        model.onLauncherSettingsActionSelected(LauncherShellAction.ImportLauncherBackup(backup(HomeMode.PlacedItems)))
        assertEquals(emptyList<String>(), model.labels())
    }
}
