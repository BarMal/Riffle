package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import com.riffle.core.domain.launcher.settings.MotionSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherBackupSnapshotTest {
    @Test
    fun backupDocumentPreservesSystemBarAndMotionSettings() {
        val settings = backupSettings()

        val document =
            launcherBackupDocument(
                storedLayoutSet = null,
                activeLayout = HomeLayoutDefaults.standard(),
                launcherSettings = settings,
            )

        assertEquals(settings, document.launcherSettings)
    }

    @Test
    fun importedBackupPersistsSystemBarAndMotionSettings() {
        val settings = backupSettings()
        val document =
            LauncherBackupDocument(
                homeLayoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()),
                launcherSettings = settings,
            )
        val homeLayoutRepository = FakeHomeLayoutRepository()
        val launcherSettingsRepository = FakeLauncherSettingsRepository()

        val state =
            LauncherShellState().withImportedBackup(
                document = document,
                homeLayoutRepository = homeLayoutRepository,
                launcherSettingsRepository = launcherSettingsRepository,
                appVisibilityRepository = FakeAppVisibilityRepository(),
            )

        assertEquals(settings, state.launcherSettings)
        assertEquals(settings, launcherSettingsRepository.savedSettings)
    }

    private class FakeHomeLayoutRepository : HomeLayoutRepository {
        var savedLayoutSet: HomeLayoutSet? = null

        override fun loadHomeLayout(): HomeLayout? = savedLayoutSet?.activeLayout

        override fun saveHomeLayout(layout: HomeLayout) = Unit

        override fun loadHomeLayoutSet(): HomeLayoutSet? = savedLayoutSet

        override fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) {
            savedLayoutSet = layoutSet
        }
    }

    private class FakeLauncherSettingsRepository : LauncherSettingsRepository {
        var savedSettings: LauncherSettings? = null

        override fun loadLauncherSettings(): LauncherSettings? = savedSettings

        override fun saveLauncherSettings(settings: LauncherSettings) {
            savedSettings = settings
        }
    }

    private class FakeAppVisibilityRepository : AppVisibilityRepository {
        override fun hiddenAppIdentities(): Set<AppIdentity> = emptySet()

        override fun hideApp(identity: AppIdentity) = Unit

        override fun showApp(identity: AppIdentity) = Unit
    }

    private fun backupSettings(): LauncherSettings =
        LauncherSettings(
            appearance =
                AppearanceSettings(
                    fullscreenHome = false,
                    hideStatusBarOnHome = true,
                    hideNavigationBarOnHome = false,
                ),
            motion = MotionSettings(reducedMotion = true),
        )
}
