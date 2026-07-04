package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSettings
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import com.riffle.core.domain.launcher.settings.HapticSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherBackupExportCoordinatorTest {
    @Test
    fun preservesStoredLayoutSetWhenExportingCurrentBackup() {
        val standardKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER)
        val libraryKey = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY)
        val storedLayoutSet =
            HomeLayoutSet(
                activeKey = standardKey,
                layouts =
                    mapOf(
                        standardKey to HomeLayoutDefaults.standard(),
                        libraryKey to
                            HomeLayoutDefaults.standard()
                                .copy(viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY),
                    ),
            )
        val coordinator =
            LauncherBackupExportCoordinator(
                homeLayoutRepository = FakeHomeLayoutRepository(layoutSet = storedLayoutSet),
                appVisibilityRepository = FakeAppVisibilityRepository(),
                currentState = { LauncherShellState(homeLayout = storedLayoutSet.activeLayout) },
                epochMillisProvider = FixedEpochMillisProvider,
            )

        val document = coordinator.currentBackupDocument()

        assertEquals(storedLayoutSet, document.homeLayoutSet)
    }

    @Test
    fun exportsCurrentLayoutAndSettingsOverStoredActiveValues() {
        val storedLayout = HomeLayoutDefaults.standard()
        val currentLayout =
            storedLayout.copy(
                settings =
                    storedLayout.settings.copy(
                        grid =
                            GridSettings(
                                dimensions = GridDimensions(columns = 5, rows = 6),
                            ),
                    ),
            )
        val currentSettings =
            LauncherSettings(
                appearance =
                    AppearanceSettings(
                        wallpaper = WallpaperSettings(source = WallpaperSource.SOLID_COLOR),
                    ),
                haptics = HapticSettings(feedbackStrength = HapticFeedbackStrength.STRONG),
            )
        val coordinator =
            LauncherBackupExportCoordinator(
                homeLayoutRepository =
                    FakeHomeLayoutRepository(layoutSet = HomeLayoutSet.fromLayout(storedLayout)),
                appVisibilityRepository = FakeAppVisibilityRepository(hiddenApps = setOf(cameraIdentity)),
                currentState =
                    {
                        LauncherShellState(
                            homeLayout = currentLayout,
                            launcherSettings = currentSettings,
                        )
                    },
                epochMillisProvider = FixedEpochMillisProvider,
            )

        val document = coordinator.currentBackupDocument()

        assertEquals(currentLayout, document.homeLayoutSet.activeLayout)
        assertEquals(currentSettings, document.launcherSettings)
        assertEquals(setOf(cameraIdentity), document.hiddenAppIdentities)
    }

    @Test
    fun includesExportTimestamp() {
        val coordinator =
            LauncherBackupExportCoordinator(
                homeLayoutRepository =
                    FakeHomeLayoutRepository(layoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard())),
                appVisibilityRepository = FakeAppVisibilityRepository(),
                currentState = { LauncherShellState() },
                epochMillisProvider = FixedEpochMillisProvider,
            )

        val document = coordinator.currentBackupDocument()

        assertEquals(123_456L, document.exportedAtEpochMillis)
    }

    private class FakeHomeLayoutRepository(
        private val layoutSet: HomeLayoutSet?,
    ) : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout? = layoutSet?.activeLayout

        override fun saveHomeLayout(layout: HomeLayout) = Unit

        override fun loadHomeLayoutSet(): HomeLayoutSet? = layoutSet
    }

    private object FixedEpochMillisProvider : EpochMillisProvider {
        override fun nowEpochMillis(): Long = 123_456L
    }

    private class FakeAppVisibilityRepository(
        private val hiddenApps: Set<AppIdentity> = emptySet(),
    ) : AppVisibilityRepository {
        override fun hiddenAppIdentities(): Set<AppIdentity> = hiddenApps

        override fun hideApp(identity: AppIdentity) = Unit

        override fun showApp(identity: AppIdentity) = Unit
    }

    private companion object {
        val cameraIdentity =
            AppIdentity(
                packageName = AppPackageName("com.riffle.camera"),
                activityName = AppActivityName(".MainActivity"),
                profile = AppProfile.personal(),
            )
    }
}
