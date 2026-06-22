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
        -> true

        else -> false
    }
