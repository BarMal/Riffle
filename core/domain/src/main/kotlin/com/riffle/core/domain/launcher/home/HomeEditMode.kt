package com.riffle.core.domain.launcher.home

sealed interface HomeEditMode {
    data object Browsing : HomeEditMode

    /**
     * Editing one page. [selected] are the apps and folders the person has ticked (see [HomeSelection]): never stored,
     * dropped with the mode, and only what stands on the selected page counts.
     */
    data class EditingPage(
        val pageId: LauncherPageId,
        val selected: Set<LauncherItemId> = emptySet(),
    ) : HomeEditMode

    data object ManagingPages : HomeEditMode
}
