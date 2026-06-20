package com.riffle.app.launcher

import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.ShellNavigationAction
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeEditMode
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeShortcutMoveDirection
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPageId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherShellViewModelTest {
    @Test
    fun startsWithDefaultHomePromptVisible() {
        val viewModel = LauncherShellViewModel(firstRunRepository = FakeFirstRunRepository())

        assertTrue(viewModel.state.value.shouldShowDefaultHomePrompt)
    }

    @Test
    fun recordsDefaultHomeRequestInProgress() {
        val viewModel = LauncherShellViewModel(firstRunRepository = FakeFirstRunRepository())

        viewModel.onDefaultHomeRequestStarted()

        assertEquals(FirstRunStatus.REQUESTING_HOME_ROLE, viewModel.state.value.firstRunStatus)
    }

    @Test
    fun hidesPromptWhenAppBecomesDefaultHome() {
        val repository = FakeFirstRunRepository()
        val viewModel = LauncherShellViewModel(firstRunRepository = repository)

        viewModel.onHomeRoleStatusChanged(HomeRoleStatus.DEFAULT_HOME)

        assertFalse(viewModel.state.value.shouldShowDefaultHomePrompt)
        assertTrue(viewModel.state.value.shouldShowEmptyHome)
        assertTrue(repository.isFirstRunComplete())
    }

    @Test
    fun restoresCompletedFirstRunFromRepository() {
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(isComplete = true),
            )

        assertEquals(FirstRunStatus.COMPLETE, viewModel.state.value.firstRunStatus)
        assertTrue(viewModel.state.value.shouldShowEmptyHome)
    }

    @Test
    fun navigatesBetweenShellDestinations() {
        val viewModel = LauncherShellViewModel(firstRunRepository = FakeFirstRunRepository())

        viewModel.onNavigationActionSelected(ShellNavigationAction.OpenAppDrawer)
        assertEquals(ShellDestination.APP_DRAWER, viewModel.state.value.destination)

        viewModel.onNavigationActionSelected(ShellNavigationAction.OpenHome)
        assertEquals(ShellDestination.HOME, viewModel.state.value.destination)
    }

    @Test
    fun loadsVisibleInstalledAppsIntoInitialState() {
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository =
                    FakeInstalledAppRepository(
                        apps =
                            listOf(
                                app(label = "Hidden", visibility = AppVisibility.HIDDEN),
                                app(label = "Camera"),
                                app(label = "Browser"),
                            ),
                    ),
            )

        assertEquals(
            listOf("Browser", "Camera"),
            viewModel.state.value.installedApps.map { app -> app.label },
        )
    }

    @Test
    fun refreshesInstalledApps() {
        val repository = FakeInstalledAppRepository(apps = listOf(app(label = "Camera")))
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = repository,
            )

        repository.apps = listOf(app(label = "Calendar"))
        viewModel.refreshInstalledApps()

        assertEquals(listOf("Calendar"), viewModel.state.value.installedApps.map { app -> app.label })
    }

    @Test
    fun filtersSearchResultsWhenQueryChanges() {
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository =
                    FakeInstalledAppRepository(
                        apps =
                            listOf(
                                app(label = "Camera"),
                                app(label = "Calendar"),
                                app(label = "Maps"),
                            ),
                    ),
            )

        viewModel.onSearchQueryChanged("cam")

        assertEquals("cam", viewModel.state.value.searchQuery)
        assertEquals(listOf("Camera"), viewModel.state.value.searchResults.map { app -> app.label })
    }

    @Test
    fun refreshesSearchResultsForCurrentQuery() {
        val repository = FakeInstalledAppRepository(apps = listOf(app(label = "Camera")))
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = repository,
            )
        viewModel.onSearchQueryChanged("cal")

        repository.apps = listOf(app(label = "Calendar"))
        viewModel.refreshInstalledApps()

        assertEquals("cal", viewModel.state.value.searchQuery)
        assertEquals(listOf("Calendar"), viewModel.state.value.searchResults.map { app -> app.label })
    }

    @Test
    fun addsAppShortcutToFirstAvailableHomeCell() {
        val camera = app(label = "Camera")
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera)),
            )

        viewModel.onAddAppToHome(camera)

        val shortcut = viewModel.state.value.homeLayout.selectedPage.items.single() as AppShortcutItem
        assertEquals(camera.identity, shortcut.appIdentity)
        assertEquals("Camera", shortcut.label)
        assertEquals(GridPlacement(cell = GridCell(column = 0, row = 0)), shortcut.placement)
    }

    @Test
    fun addsDuplicateAppShortcutsWithUniqueItemIds() {
        val camera = app(label = "Camera")
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera)),
            )

        viewModel.onAddAppToHome(camera)
        viewModel.onAddAppToHome(camera)

        val shortcuts = viewModel.state.value.homeLayout.selectedPage.items.filterIsInstance<AppShortcutItem>()
        assertEquals(2, shortcuts.map { shortcut -> shortcut.id }.distinct().size)
        assertEquals(
            listOf(
                GridPlacement(cell = GridCell(column = 0, row = 0)),
                GridPlacement(cell = GridCell(column = 1, row = 0)),
            ),
            shortcuts.map { shortcut -> shortcut.placement },
        )
    }

    @Test
    fun restoresSavedHomeLayout() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())

        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        assertEquals(repository.savedLayout, viewModel.state.value.homeLayout)
    }

    @Test
    fun savesHomeLayoutAfterAddingAppShortcut() {
        val camera = app(label = "Camera")
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera)),
                homeLayoutRepository = repository,
            )

        viewModel.onAddAppToHome(camera)

        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun entersAndExitsHomeEditMode() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onHomePageEdited(LauncherShellAction.EnterHomeEditMode)

        assertEquals(
            HomeEditMode.EditingPage(viewModel.state.value.homeLayout.selectedPageId),
            viewModel.state.value.homeLayout.editMode,
        )

        viewModel.onHomePageEdited(LauncherShellAction.ExitHomeEditMode)

        assertEquals(HomeEditMode.Browsing, viewModel.state.value.homeLayout.editMode)
    }

    @Test
    fun addsHomePageSelectsItAndSavesLayout() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onHomePageEdited(LauncherShellAction.AddHomePage)

        assertEquals(listOf(LauncherPageId("home"), LauncherPageId("home-2")), viewModel.state.value.homeLayout.pageIds)
        assertEquals(LauncherPageId("home-2"), viewModel.state.value.homeLayout.selectedPageId)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun selectsAdjacentHomePagesAndSavesLayout() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        viewModel.onHomePageEdited(LauncherShellAction.AddHomePage)

        viewModel.onHomePageEdited(LauncherShellAction.SelectPreviousHomePage)

        assertEquals(LauncherPageId("home"), viewModel.state.value.homeLayout.selectedPageId)

        viewModel.onHomePageEdited(LauncherShellAction.SelectNextHomePage)

        assertEquals(LauncherPageId("home-2"), viewModel.state.value.homeLayout.selectedPageId)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun ignoresPageSelectionOutsideLayoutBounds() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        val layoutBeforeSelection = viewModel.state.value.homeLayout

        viewModel.onHomePageEdited(LauncherShellAction.SelectPreviousHomePage)

        assertEquals(layoutBeforeSelection, viewModel.state.value.homeLayout)
        assertEquals(layoutBeforeSelection, repository.savedLayout)
    }

    @Test
    fun movesSelectedHomePageLeftAndSavesLayout() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        viewModel.onHomePageEdited(LauncherShellAction.AddHomePage)

        viewModel.onHomePageEdited(LauncherShellAction.MoveSelectedHomePageLeft)

        assertEquals(listOf(LauncherPageId("home-2"), LauncherPageId("home")), viewModel.state.value.homeLayout.pageIds)
        assertEquals(LauncherPageId("home-2"), viewModel.state.value.homeLayout.selectedPageId)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun movesSelectedHomePageRightAndSavesLayout() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        viewModel.onHomePageEdited(LauncherShellAction.AddHomePage)
        viewModel.onHomePageEdited(LauncherShellAction.SelectPreviousHomePage)

        viewModel.onHomePageEdited(LauncherShellAction.MoveSelectedHomePageRight)

        assertEquals(listOf(LauncherPageId("home-2"), LauncherPageId("home")), viewModel.state.value.homeLayout.pageIds)
        assertEquals(LauncherPageId("home"), viewModel.state.value.homeLayout.selectedPageId)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun ignoresSelectedHomePageMoveOutsideLayoutBounds() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        val layoutBeforeMove = viewModel.state.value.homeLayout

        viewModel.onHomePageEdited(LauncherShellAction.MoveSelectedHomePageLeft)

        assertEquals(layoutBeforeMove, viewModel.state.value.homeLayout)
        assertEquals(layoutBeforeMove, repository.savedLayout)
    }

    @Test
    fun deletesSelectedHomePageAndSavesLayout() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        viewModel.onHomePageEdited(LauncherShellAction.AddHomePage)

        viewModel.onHomePageEdited(LauncherShellAction.DeleteSelectedHomePage)

        assertEquals(listOf(LauncherPageId("home")), viewModel.state.value.homeLayout.pageIds)
        assertEquals(LauncherPageId("home"), viewModel.state.value.homeLayout.selectedPageId)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun ignoresDeletingLastHomePage() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        val layoutBeforeDeletion = viewModel.state.value.homeLayout

        viewModel.onHomePageEdited(LauncherShellAction.DeleteSelectedHomePage)

        assertEquals(layoutBeforeDeletion, viewModel.state.value.homeLayout)
        assertEquals(layoutBeforeDeletion, repository.savedLayout)
    }

    @Test
    fun removesHomeShortcutAndSavesLayout() {
        val camera = app(label = "Camera")
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera)),
                homeLayoutRepository = repository,
            )
        viewModel.onAddAppToHome(camera)
        val shortcut = viewModel.state.value.homeLayout.selectedPage.items.single() as AppShortcutItem

        viewModel.onHomeShortcutEdited(LauncherShellAction.RemoveHomeShortcut(shortcut.id))

        assertEquals(emptyList<AppShortcutItem>(), viewModel.state.value.homeLayout.selectedPage.items)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun ignoresMissingHomeShortcutRemoval() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onHomeShortcutEdited(LauncherShellAction.RemoveHomeShortcut(LauncherItemId("missing")))

        assertEquals(HomeLayoutDefaults.standard(), viewModel.state.value.homeLayout)
        assertEquals(HomeLayoutDefaults.standard(), repository.savedLayout)
    }

    @Test
    fun movesHomeShortcutAndSavesLayout() {
        val camera = app(label = "Camera")
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera)),
                homeLayoutRepository = repository,
            )
        viewModel.onAddAppToHome(camera)
        val shortcut = viewModel.state.value.homeLayout.selectedPage.items.single() as AppShortcutItem

        viewModel.onHomeShortcutEdited(
            LauncherShellAction.MoveHomeShortcut(
                itemId = shortcut.id,
                direction = HomeShortcutMoveDirection.RIGHT,
            ),
        )

        assertEquals(
            GridPlacement(cell = GridCell(column = 1, row = 0)),
            viewModel.state.value.homeLayout.selectedPage.items.single().placement,
        )
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun ignoresHomeShortcutMoveRejectedByGrid() {
        val camera = app(label = "Camera")
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera)),
                homeLayoutRepository = repository,
            )
        viewModel.onAddAppToHome(camera)
        val layoutBeforeMove = viewModel.state.value.homeLayout
        val shortcut = layoutBeforeMove.selectedPage.items.single() as AppShortcutItem

        viewModel.onHomeShortcutEdited(
            LauncherShellAction.MoveHomeShortcut(
                itemId = shortcut.id,
                direction = HomeShortcutMoveDirection.LEFT,
            ),
        )

        assertEquals(layoutBeforeMove, viewModel.state.value.homeLayout)
        assertEquals(layoutBeforeMove, repository.savedLayout)
    }

    private class FakeFirstRunRepository(
        private var isComplete: Boolean = false,
    ) : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = isComplete

        override fun setFirstRunComplete() {
            isComplete = true
        }
    }

    private class FakeInstalledAppRepository(
        var apps: List<InstalledApp> = emptyList(),
    ) : InstalledAppRepository {
        override fun installedApps(): List<InstalledApp> = apps
    }

    private class FakeHomeLayoutRepository(
        var savedLayout: HomeLayout? = null,
    ) : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout? = savedLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            savedLayout = layout
        }
    }

    private fun app(
        label: String,
        visibility: AppVisibility = AppVisibility.VISIBLE,
    ): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = label,
            visibility = visibility,
        )

    private val HomeLayout.pageIds: List<LauncherPageId>
        get() = pages.map { page -> page.id }
}
