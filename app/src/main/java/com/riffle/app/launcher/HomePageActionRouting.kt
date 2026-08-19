package com.riffle.app.launcher

internal fun LauncherShellAction.isHomePageEditAction(): Boolean =
    when (this) {
        LauncherShellAction.EnterHomeEditMode,
        LauncherShellAction.ExitHomeEditMode,
        LauncherShellAction.EnterHomePageOverview,
        LauncherShellAction.AddHomePage,
        LauncherShellAction.DuplicateSelectedHomePage,
        LauncherShellAction.SelectPreviousHomePage,
        LauncherShellAction.SelectNextHomePage,
        is LauncherShellAction.SelectHomePage,
        LauncherShellAction.MoveSelectedHomePageLeft,
        LauncherShellAction.MoveSelectedHomePageRight,
        is LauncherShellAction.MoveHomePage,
        LauncherShellAction.DeleteSelectedHomePage,
        LauncherShellAction.ToggleSelectedHomePagePinned,
        is LauncherShellAction.SelectSelectedHomePageType,
        is LauncherShellAction.SelectSelectedHomePageGridDimensions,
        is LauncherShellAction.SelectHomeGridDimensions,
        is LauncherShellAction.SelectHomeGridMargin,
        is LauncherShellAction.SelectLibraryPageCompaction,
        is LauncherShellAction.SelectHomeLabelBackgroundAlpha,
        is LauncherShellAction.SelectHomeIconSize,
        is LauncherShellAction.SelectHomeLabelTextSize,
        is LauncherShellAction.SelectHomeLabelTextVisible,
        is LauncherShellAction.SelectHomeLabelMaxWidth,
        is LauncherShellAction.SelectHomeLabelMaxLines,
        is LauncherShellAction.SelectHomeLabelSizing,
        is LauncherShellAction.SelectLauncherViewMode,
        is LauncherShellAction.SelectLauncherTemplate,
        is LauncherShellAction.SelectHomeLayoutDeviceClass,
        -> true

        else -> false
    }

internal fun LauncherShellAction.isHomeLayoutConfigurationAction(): Boolean =
    when (this) {
        is LauncherShellAction.SelectSelectedHomePageType,
        is LauncherShellAction.SelectSelectedHomePageGridDimensions,
        is LauncherShellAction.SelectHomeGridDimensions,
        is LauncherShellAction.SelectHomeGridMargin,
        is LauncherShellAction.SelectLibraryPageCompaction,
        is LauncherShellAction.SelectHomeLabelBackgroundAlpha,
        is LauncherShellAction.SelectHomeIconSize,
        is LauncherShellAction.SelectHomeLabelTextSize,
        is LauncherShellAction.SelectHomeLabelTextVisible,
        is LauncherShellAction.SelectHomeLabelMaxWidth,
        is LauncherShellAction.SelectHomeLabelMaxLines,
        is LauncherShellAction.SelectHomeLabelSizing,
        // SelectLauncherViewMode is deliberately absent. A mode is not a field of a layout that
        // settings can edit -- it is which of the per-mode layouts applies. Editing it as a field
        // wrote the layout on screen into the mode being switched to, so the mode you left came
        // back holding a copy of the one you were on, dock and all.
        is LauncherShellAction.SelectLauncherTemplate,
        -> true

        else -> false
    }
