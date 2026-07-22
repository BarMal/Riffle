package com.riffle.app.launcher

import com.riffle.core.domain.launcher.ShellNavigationAction

internal sealed interface LauncherActivityRoute {
    data object RequestDefaultHome : LauncherActivityRoute

    data object OpenDefaultHome : LauncherActivityRoute

    data class Navigation(
        val action: ShellNavigationAction,
    ) : LauncherActivityRoute

    data object HomePageEdit : LauncherActivityRoute

    data object HomeShortcutEdit : LauncherActivityRoute

    data object DockEdit : LauncherActivityRoute
}

internal fun LauncherShellAction.launcherActivityRoute(): LauncherActivityRoute? =
    when {
        this == LauncherShellAction.RequestDefaultHome -> LauncherActivityRoute.RequestDefaultHome
        this == LauncherShellAction.OpenDefaultHome -> LauncherActivityRoute.OpenDefaultHome
        navigationAction() != null -> LauncherActivityRoute.Navigation(checkNotNull(navigationAction()))
        isHomePageEditAction() -> LauncherActivityRoute.HomePageEdit
        isCardsChapterAction() -> LauncherActivityRoute.HomePageEdit
        isAppStageAction() -> LauncherActivityRoute.HomePageEdit
        isHomeShortcutEditAction() -> LauncherActivityRoute.HomeShortcutEdit
        isDockEditAction() -> LauncherActivityRoute.DockEdit
        else -> null
    }

private fun LauncherShellAction.isCardsChapterAction(): Boolean =
    when (this) {
        is LauncherShellAction.SelectCardsChapter,
        is LauncherShellAction.ToggleCardsChapterPinned,
        -> true

        else -> false
    }

private fun LauncherShellAction.isAppStageAction(): Boolean =
    when (this) {
        is LauncherShellAction.SelectAppStage,
        is LauncherShellAction.ToggleAppStagePinned,
        LauncherShellAction.SelectPreviousAppStage,
        LauncherShellAction.SelectNextAppStage,
        -> true

        else -> false
    }

private fun LauncherShellAction.navigationAction(): ShellNavigationAction? =
    when (this) {
        LauncherShellAction.OpenHome -> ShellNavigationAction.OpenHome
        LauncherShellAction.OpenAppDrawer -> ShellNavigationAction.OpenAppDrawer
        LauncherShellAction.OpenSearch -> ShellNavigationAction.OpenSearch
        LauncherShellAction.OpenNotifications -> ShellNavigationAction.OpenNotifications
        LauncherShellAction.OpenSettings -> ShellNavigationAction.OpenSettings
        is LauncherShellAction.OpenSettingsPage -> ShellNavigationAction.OpenSettings
        else -> null
    }

private fun LauncherShellAction.isHomeShortcutEditAction(): Boolean =
    when (this) {
        is LauncherShellAction.RemoveHomeShortcut,
        is LauncherShellAction.CreateEmptyHomeFolder,
        is LauncherShellAction.CreateHomeFolder,
        is LauncherShellAction.RenameHomeFolder,
        is LauncherShellAction.AddAppToFolder,
        is LauncherShellAction.RemoveAppFromFolder,
        is LauncherShellAction.MoveAppInFolder,
        is LauncherShellAction.MoveAppOutOfFolder,
        is LauncherShellAction.MoveHomeShortcutToCell,
        is LauncherShellAction.MoveHomeItemToPage,
        is LauncherShellAction.AddAppShortcutToHome,
        is LauncherShellAction.AddHostedWidgetToHome,
        is LauncherShellAction.ResizeHomeWidget,
        -> true

        else -> false
    }

private fun LauncherShellAction.isDockEditAction(): Boolean =
    when (this) {
        is LauncherShellAction.AddAppToDock,
        is LauncherShellAction.AddHostedWidgetToDock,
        is LauncherShellAction.SelectDockEnabled,
        is LauncherShellAction.SelectDockNotificationCardsEnabled,
        is LauncherShellAction.SelectDockCapacity,
        is LauncherShellAction.SelectDockIconSize,
        is LauncherShellAction.SelectDockBackgroundAlpha,
        is LauncherShellAction.SelectDockVisualEffect,
        is LauncherShellAction.SelectDockBackgroundSizing,
        is LauncherShellAction.SelectDockAlignment,
        is LauncherShellAction.SelectDockItemSpacing,
        is LauncherShellAction.RemoveDockShortcut,
        is LauncherShellAction.MoveDockShortcut,
        is LauncherShellAction.MoveDockShortcutToIndex,
        is LauncherShellAction.MoveHomeItemToDock,
        is LauncherShellAction.MoveDockItemToHome,
        -> true

        else -> false
    }
