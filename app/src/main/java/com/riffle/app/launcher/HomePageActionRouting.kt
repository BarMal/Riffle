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
        LauncherShellAction.DeleteSelectedHomePage,
        is LauncherShellAction.SelectSelectedHomePageType,
        is LauncherShellAction.SelectHomeGridDimensions,
        is LauncherShellAction.SelectLibraryPageCompaction,
        is LauncherShellAction.SelectHomeLabelBackgroundAlpha,
        is LauncherShellAction.SelectHomeLabelTextSize,
        is LauncherShellAction.SelectHomeLabelTextVisible,
        is LauncherShellAction.SelectHomeLabelMaxWidth,
        is LauncherShellAction.SelectHomeLabelMaxLines,
        is LauncherShellAction.SelectHomeLabelSizing,
        is LauncherShellAction.SelectLauncherViewMode,
        is LauncherShellAction.SelectHomeLayoutDeviceClass,
        -> true

        else -> false
    }

internal fun LauncherShellAction.isHomeLayoutConfigurationAction(): Boolean =
    when (this) {
        is LauncherShellAction.SelectHomeGridDimensions,
        is LauncherShellAction.SelectLibraryPageCompaction,
        is LauncherShellAction.SelectHomeLabelBackgroundAlpha,
        is LauncherShellAction.SelectHomeLabelTextSize,
        is LauncherShellAction.SelectHomeLabelTextVisible,
        is LauncherShellAction.SelectHomeLabelMaxWidth,
        is LauncherShellAction.SelectHomeLabelMaxLines,
        is LauncherShellAction.SelectHomeLabelSizing,
        -> true

        else -> false
    }
