package com.riffle.app.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository

class LauncherShellViewModelFactory(
    private val firstRunRepository: FirstRunRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val appVisibilityRepository: AppVisibilityRepository,
    private val homeLayoutRepository: HomeLayoutRepository,
    private val launcherSettingsRepository: LauncherSettingsRepository,
    private val platformDependencies: LauncherShellPlatformDependencies = LauncherShellPlatformDependencies(),
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        when (modelClass) {
            LauncherShellViewModel::class.java ->
                LauncherShellViewModel(
                    firstRunRepository = firstRunRepository,
                    installedAppRepository = installedAppRepository,
                    appVisibilityRepository = appVisibilityRepository,
                    homeLayoutRepository = homeLayoutRepository,
                    launcherSettingsRepository = launcherSettingsRepository,
                    platformDependencies = platformDependencies.copy(loadInitialPlatformState = false),
                ) as T

            else -> error("Unknown ViewModel class: ${modelClass.name}")
        }
}
