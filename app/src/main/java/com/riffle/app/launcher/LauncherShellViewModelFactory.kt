package com.riffle.app.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.HomeLayoutRepository

class LauncherShellViewModelFactory(
    private val firstRunRepository: FirstRunRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val homeLayoutRepository: HomeLayoutRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        when (modelClass) {
            LauncherShellViewModel::class.java ->
                LauncherShellViewModel(
                    firstRunRepository = firstRunRepository,
                    installedAppRepository = installedAppRepository,
                    homeLayoutRepository = homeLayoutRepository,
                ) as T

            else -> error("Unknown ViewModel class: ${modelClass.name}")
        }
}
