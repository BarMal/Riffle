package com.riffle.app.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class LauncherShellViewModelFactory(
    private val firstRunRepository: FirstRunRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        when (modelClass) {
            LauncherShellViewModel::class.java ->
                LauncherShellViewModel(
                    firstRunRepository = firstRunRepository,
                ) as T

            else -> error("Unknown ViewModel class: ${modelClass.name}")
        }
}
