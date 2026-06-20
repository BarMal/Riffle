package com.riffle.app.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class LauncherShellViewModelFactory(
    private val firstRunRepository: FirstRunRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == LauncherShellViewModel::class.java) {
            "Unknown ViewModel class: ${modelClass.name}"
        }

        return LauncherShellViewModel(
            firstRunRepository = firstRunRepository,
        ) as T
    }
}
