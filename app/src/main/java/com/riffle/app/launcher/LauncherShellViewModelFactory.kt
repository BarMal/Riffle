package com.riffle.app.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.riffle.core.domain.launcher.apps.InstalledAppRepository

class LauncherShellViewModelFactory(
    private val firstRunRepository: FirstRunRepository,
    private val installedAppRepository: InstalledAppRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        when (modelClass) {
            LauncherShellViewModel::class.java ->
                LauncherShellViewModel(
                    firstRunRepository = firstRunRepository,
                    installedAppRepository = installedAppRepository,
                ) as T

            else -> error("Unknown ViewModel class: ${modelClass.name}")
        }
}
