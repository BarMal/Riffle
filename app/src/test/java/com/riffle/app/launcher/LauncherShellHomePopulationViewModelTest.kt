package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockDefaultsResolver
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeStart
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.settings.HomeBehaviourSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Filling Home: "Add all apps", "Arrange A to Z" (both with Undo), and the first-run seed that runs once. */
class LauncherShellHomePopulationViewModelTest {
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

    private val installed = listOf("Zoo", "Phone", "Alarm", "Maps", "Camera", "Notes").map(::app)

    private fun viewModel(
        apps: Apps = Apps(installed),
        settings: Settings = Settings(LauncherSettings()),
        roles: List<String> = listOf("com.riffle.phone", "com.riffle.camera", "com.riffle.missing"),
    ) = LauncherShellViewModel(
        firstRunRepository = FirstRun(),
        installedAppRepository = apps,
        homeLayoutRepository = Layouts(),
        launcherSettingsRepository = settings,
        platformDependencies =
            LauncherShellPlatformDependencies(
                dockDefaultsResolver = DockDefaultsResolver { roles.map(::AppPackageName) },
            ),
    )

    private fun LauncherShellViewModel.homeLabels(): List<String> =
        state.value.homeLayout.pages.filter { it.type == LauncherPageType.Home }
            .flatMap { it.items }.filterIsInstance<AppShortcutItem>().map { it.label }

    private fun LauncherShellViewModel.dockLabels(): List<String> =
        state.value.homeLayout.dock.items.filterIsInstance<AppShortcutItem>().map { it.label }

    private fun LauncherShellViewModel.refresh() = runBlocking { refreshInstalledApps().join() }

    @Test
    fun addAllPlacesEveryAppAToZAndUndoRestoresTheLayoutExactly() {
        val model = viewModel()
        model.refresh()
        model.onAddAppToHome(app("Zoo"))
        val before = model.state.value.homeLayout

        model.onLauncherSettingsActionSelected(LauncherShellAction.AddAllAppsToHome)

        assertEquals(listOf("Zoo", "Alarm", "Camera", "Maps", "Notes", "Phone"), model.homeLabels())
        model.onLauncherSettingsActionSelected(LauncherShellAction.UndoHomeBulkEdit(before))
        assertEquals(before, model.state.value.homeLayout)
    }

    @Test
    fun addAllWithEverythingOnHomeChangesNothing() {
        val model = viewModel()
        model.refresh()
        model.onLauncherSettingsActionSelected(LauncherShellAction.AddAllAppsToHome)
        val once = model.state.value.homeLayout

        model.onLauncherSettingsActionSelected(LauncherShellAction.AddAllAppsToHome)

        assertEquals(once, model.state.value.homeLayout)
    }

    @Test
    fun arrangeSortsHomeAndUndoPutsTheHandMadeArrangementBack() {
        val model = viewModel()
        model.refresh()
        listOf("Zoo", "Maps", "Alarm").forEach { model.onAddAppToHome(app(it)) }
        val before = model.state.value.homeLayout

        model.onLauncherSettingsActionSelected(LauncherShellAction.ArrangeHomeAtoZ)

        assertEquals(listOf("Alarm", "Maps", "Zoo"), model.homeLabels())
        val arranged = model.state.value.homeLayout
        model.onLauncherSettingsActionSelected(LauncherShellAction.ArrangeHomeAtoZ)
        assertEquals(arranged, model.state.value.homeLayout)
        model.onLauncherSettingsActionSelected(LauncherShellAction.UndoHomeBulkEdit(before))
        assertEquals(before, model.state.value.homeLayout)
    }

    @Test
    fun theCompactChoiceIsStoredAndTheEmptyHomeHintIsDismissedOnce() {
        val settings = Settings(LauncherSettings())
        val model = viewModel(settings = settings)

        model.onLauncherSettingsActionSelected(LauncherShellAction.SetCompactArrange(false))
        model.onLauncherSettingsActionSelected(LauncherShellAction.DismissEmptyHomeHint)

        assertFalse(settings.saved!!.home.compactArrange)
        assertTrue(settings.saved!!.home.emptyHomeHintDismissed)
    }

    @Test
    fun aFreshInstallSeedsTheDockAndEveryOtherAppOnceAndARefreshNeverReAddsARemovedApp() {
        val model = viewModel()
        model.refresh()

        model.seedHome(HomeStart.ALL_APPS)

        // Roles that do not resolve to an installed app are skipped silently; docked apps are not also on Home.
        assertEquals(listOf("Phone", "Camera"), model.dockLabels())
        assertEquals(listOf("Alarm", "Maps", "Notes", "Zoo"), model.homeLabels())
        assertTrue(model.state.value.launcherSettings.home.emptyHomeHintDismissed)

        val removed = model.state.value.homeLayout.pages.flatMap { it.items }.first { it is AppShortcutItem }
        model.onHomeShortcutEdited(LauncherShellAction.RemoveHomeShortcut(removed.id))
        val afterRemoval = model.state.value.homeLayout
        model.refresh()
        model.seedHome(HomeStart.ALL_APPS)
        model.refresh()

        assertEquals(afterRemoval, model.state.value.homeLayout)
        assertFalse(model.homeLabels().contains((removed as AppShortcutItem).label))
    }

    @Test
    fun startEmptySeedsOnlyTheDock() {
        val model = viewModel()
        model.refresh()

        model.seedHome(HomeStart.EMPTY)

        assertEquals(listOf("Phone", "Camera"), model.dockLabels())
        assertEquals(emptyList<String>(), model.homeLabels())
    }

    @Test
    fun theSeedWaitsForTheAppListAndThenRunsOnce() {
        val apps = Apps(emptyList())
        val model = viewModel(apps = apps)
        model.refresh()

        model.seedHome(HomeStart.ALL_APPS)
        assertEquals(emptyList<String>(), model.homeLabels())

        apps.apps = installed
        model.refresh()
        assertEquals(listOf("Alarm", "Maps", "Notes", "Zoo"), model.homeLabels())
        assertEquals(listOf("Phone", "Camera"), model.dockLabels())
    }

    @Test
    fun theSeedUsesTheStoredCompactChoice() {
        val roomy = Settings(LauncherSettings(home = HomeBehaviourSettings(compactArrange = false)))
        val many = (1..30).map { app("App%02d".format(it)) }
        val model = viewModel(apps = Apps(many), settings = roomy, roles = emptyList())
        model.refresh()

        model.seedHome(HomeStart.ALL_APPS)

        val perPage = model.state.value.homeLayout.pages.map { it.items.size }
        val cells = model.state.value.homeLayout.pages.first().grid.cellCount
        assertEquals(cells * 3 / 4, perPage.first())
        assertEquals(30, perPage.sum())
    }

    @Test
    fun aHomeThatAlreadyHoldsSomethingIsNeverSeeded() {
        val model = viewModel()
        model.refresh()
        model.onAddAppToHome(app("Zoo"))

        model.seedHome(HomeStart.ALL_APPS)

        assertEquals(listOf("Zoo"), model.homeLabels())
        assertEquals(emptyList<String>(), model.dockLabels())
    }
}
