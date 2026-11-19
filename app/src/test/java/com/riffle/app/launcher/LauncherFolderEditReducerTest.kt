package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.FolderEngine
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LauncherFolderEditReducerTest {
    @Test
    fun appliesFolderEditsAndSavesLayoutOnce() {
        val repository = CountingHomeLayoutRepository()
        val state = launcherState()

        val updated =
            reducer(repository).reduce(
                state = state,
                action = LauncherShellAction.CreateEmptyHomeFolder(label = "Folder"),
            )

        val folder = updated.homeLayout.selectedPage.items.single() as FolderItem
        assertEquals("Folder", folder.label)
        assertEquals(updated.homeLayout, repository.savedLayoutSet?.activeLayout)
        assertEquals(1, repository.saveLayoutSetCount)
    }

    @Test
    fun returnsUnchangedStateForRejectedFolderEdits() {
        val repository = CountingHomeLayoutRepository()
        val state = launcherState()

        val updated =
            reducer(repository).reduce(
                state = state,
                action = LauncherShellAction.CreateEmptyHomeFolder(label = " "),
            )

        assertSame(state, updated)
        assertEquals(0, repository.saveLayoutSetCount)
    }

    private fun reducer(repository: HomeLayoutRepository): LauncherFolderEditReducer =
        LauncherFolderEditReducer(
            folderEngine = FolderEngine(),
            homeLayoutRepository = repository,
        )

    private fun launcherState(layout: HomeLayout = HomeLayoutDefaults.standard()): LauncherShellState =
        LauncherShellState(
            homeLayout = layout,
            homeLayoutSet = HomeLayoutSet.fromLayout(layout),
        )

    private class CountingHomeLayoutRepository : HomeLayoutRepository {
        var savedLayoutSet: HomeLayoutSet? = null
        var saveLayoutSetCount = 0

        override fun loadHomeLayout(): HomeLayout? = savedLayoutSet?.activeLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            savedLayoutSet = HomeLayoutSet.fromLayout(layout)
        }

        override fun loadHomeLayoutSet(): HomeLayoutSet? = savedLayoutSet

        override fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) {
            saveLayoutSetCount += 1
            savedLayoutSet = layoutSet
        }
    }

    private fun app(label: String): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = label,
        )
}
