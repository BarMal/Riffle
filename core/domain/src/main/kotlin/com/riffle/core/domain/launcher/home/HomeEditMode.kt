package com.riffle.core.domain.launcher.home

sealed interface HomeEditMode {
    data object Browsing : HomeEditMode

    data class EditingPage(val pageId: LauncherPageId) : HomeEditMode

    data object ManagingPages : HomeEditMode
}
