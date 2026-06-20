package com.riffle.app.launcher

import androidx.lifecycle.ViewModel
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.LauncherShellStateReducer
import com.riffle.core.domain.launcher.ShellNavigationAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LauncherShellViewModel(
    private val firstRunRepository: FirstRunRepository,
    private val reducer: LauncherShellStateReducer = LauncherShellStateReducer(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(createInitialState())
    val state: StateFlow<LauncherShellState> = mutableState.asStateFlow()

    fun onHomeRoleStatusChanged(homeRoleStatus: HomeRoleStatus) {
        mutableState.value =
            reducer.homeRoleChanged(
                currentState = mutableState.value,
                homeRoleStatus = homeRoleStatus,
            ).also(::persistCompletedFirstRun)
    }

    fun onDefaultHomeRequestStarted() {
        mutableState.value = reducer.defaultHomeRequestStarted(mutableState.value)
    }

    fun onFirstRunCompleted() {
        firstRunRepository.setFirstRunComplete()
        mutableState.value = reducer.firstRunCompleted(mutableState.value)
    }

    fun onNavigationActionSelected(action: ShellNavigationAction) {
        mutableState.value =
            reducer.navigationActionSelected(
                currentState = mutableState.value,
                action = action,
            )
    }

    private fun createInitialState(): LauncherShellState =
        if (firstRunRepository.isFirstRunComplete()) {
            reducer.firstRunCompleted(LauncherShellState())
        } else {
            LauncherShellState()
        }

    private fun persistCompletedFirstRun(state: LauncherShellState) {
        if (state.shouldShowEmptyHome) {
            firstRunRepository.setFirstRunComplete()
        }
    }
}
