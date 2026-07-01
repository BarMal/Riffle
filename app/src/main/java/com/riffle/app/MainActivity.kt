package com.riffle.app

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.riffle.app.launcher.AndroidHomeRoleGateway
import com.riffle.app.launcher.AndroidLauncherWallpaperController
import com.riffle.app.launcher.LauncherBackupDocumentGateway
import com.riffle.app.launcher.LauncherBackupExportCoordinator
import com.riffle.app.launcher.LauncherBackupExportResult
import com.riffle.app.launcher.LauncherBackupImportResult
import com.riffle.app.launcher.LauncherShell
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LauncherShellPlatformDependencies
import com.riffle.app.launcher.LauncherShellViewModel
import com.riffle.app.launcher.LauncherShellViewModelFactory
import com.riffle.app.launcher.SharedPreferencesAppVisibilityRepository
import com.riffle.app.launcher.SharedPreferencesFirstRunRepository
import com.riffle.app.launcher.SharedPreferencesHomeLayoutRepository
import com.riffle.app.launcher.SharedPreferencesLauncherSettingsRepository
import com.riffle.app.launcher.apps.AndroidAppLauncher
import com.riffle.app.launcher.apps.AndroidAppShortcutRepository
import com.riffle.app.launcher.apps.AndroidPackageChangeObserver
import com.riffle.app.launcher.apps.PackageManagerAppIconLoader
import com.riffle.app.launcher.apps.PackageManagerInstalledAppRepository
import com.riffle.app.launcher.handleNotificationAction
import com.riffle.app.launcher.handleSettingsAction
import com.riffle.app.launcher.isHomePageEditAction
import com.riffle.app.launcher.notifications.ActiveNotificationRefreshCoordinator
import com.riffle.app.launcher.notifications.AndroidNotificationAccessGateway
import com.riffle.app.launcher.notifications.AndroidNotificationDismissalGateway
import com.riffle.app.launcher.notifications.SharedPreferencesActiveNotificationRepository
import com.riffle.app.launcher.refreshNotifications
import com.riffle.app.launcher.refreshWidgetProviders
import com.riffle.app.launcher.selectedPageHostedWidgetIdForItem
import com.riffle.app.launcher.widgets.AndroidInstalledWidgetProviderRepository
import com.riffle.app.launcher.widgets.AndroidWidgetHostGateway
import com.riffle.app.launcher.widgets.WidgetAddRequestResult
import com.riffle.app.launcher.widgets.WidgetBindPermissionResult
import com.riffle.app.launcher.widgets.WidgetBindingCoordinator
import com.riffle.app.launcher.widgets.widgetSpanAdjustmentToast
import com.riffle.core.domain.launcher.ShellNavigationAction
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.WidgetItem

class MainActivity : ComponentActivity() {
    private val homeLayoutRepository by lazy { SharedPreferencesHomeLayoutRepository(this) }
    private val launcherSettingsRepository by lazy { SharedPreferencesLauncherSettingsRepository(this) }
    private val shellViewModel: LauncherShellViewModel by viewModels {
        LauncherShellViewModelFactory(
            firstRunRepository = SharedPreferencesFirstRunRepository(this),
            installedAppRepository =
                PackageManagerInstalledAppRepository(
                    context = this,
                    appShortcutRepository = AndroidAppShortcutRepository(this),
                ),
            appVisibilityRepository = SharedPreferencesAppVisibilityRepository(this),
            homeLayoutRepository = homeLayoutRepository,
            launcherSettingsRepository = launcherSettingsRepository,
            platformDependencies =
                LauncherShellPlatformDependencies(
                    notificationRepository = activeNotificationRepository,
                    widgetProviderRepository = AndroidInstalledWidgetProviderRepository(this),
                ),
        )
    }
    private val homeRoleGateway by lazy { AndroidHomeRoleGateway(this) }
    private val appLauncher by lazy { AndroidAppLauncher(this) }
    private val appIconLoader by lazy { PackageManagerAppIconLoader(packageManager) }
    private val packageChangeObserver by lazy {
        AndroidPackageChangeObserver(this) {
            runOnUiThread {
                shellViewModel.refreshInstalledApps()
            }
        }
    }
    private val wallpaperController by lazy { AndroidLauncherWallpaperController(window) }
    private val notificationAccessGateway by lazy { AndroidNotificationAccessGateway(this) }
    private val activeNotificationRepository by lazy { SharedPreferencesActiveNotificationRepository(this) }
    private val activeNotificationRefreshCoordinator by lazy {
        ActiveNotificationRefreshCoordinator(
            notificationChangeSource = activeNotificationRepository,
            dispatchOnMainThread = { action -> runOnUiThread { action() } },
            refreshNotifications = { shellViewModel.refreshNotifications() },
        )
    }
    private val backupExportCoordinator by lazy {
        LauncherBackupExportCoordinator(
            homeLayoutRepository = homeLayoutRepository,
            currentState = { shellViewModel.state.value },
        )
    }
    private val backupDocumentGateway by lazy { LauncherBackupDocumentGateway() }
    private val widgetHostGateway by lazy { AndroidWidgetHostGateway(this) }
    private val widgetBindingCoordinator by lazy { WidgetBindingCoordinator(widgetHostGateway) }

    private val requestHomeRole =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            refreshPlatformStatuses()
        }

    private val requestWidgetBind =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val permissionResult =
                widgetBindingCoordinator.onPermissionResult(result.resultCode == Activity.RESULT_OK)
            when (permissionResult) {
                is WidgetBindPermissionResult.Bound ->
                    completeHostedWidgetAdd(
                        activity = this,
                        viewModel = shellViewModel,
                        action = permissionResult.action,
                    )

                WidgetBindPermissionResult.Cancelled,
                WidgetBindPermissionResult.Ignored,
                -> Unit
            }
        }

    private val createBackupDocument =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument(BACKUP_DOCUMENT_MIME_TYPE),
        ) { uri ->
            uri?.let { selectedUri ->
                when (
                    backupDocumentGateway.exportDocument(
                        document = backupExportCoordinator.currentBackupDocument(),
                        openOutputStream = { contentResolver.openOutputStream(selectedUri) },
                    )
                ) {
                    LauncherBackupExportResult.Success ->
                        Toast.makeText(this, "Backup exported", Toast.LENGTH_SHORT).show()

                    LauncherBackupExportResult.Failure ->
                        Toast.makeText(this, "Backup export failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

    private val openBackupDocument =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let { selectedUri ->
                when (
                    val importResult =
                        backupDocumentGateway.importDocument {
                            contentResolver.openInputStream(selectedUri)
                        }
                ) {
                    is LauncherBackupImportResult.Imported -> {
                        shellViewModel.onLauncherSettingsActionSelected(
                            LauncherShellAction.ImportLauncherBackup(importResult.document),
                        )
                        Toast.makeText(this, "Backup imported", Toast.LENGTH_SHORT).show()
                    }

                    LauncherBackupImportResult.Failure ->
                        Toast.makeText(this, "Backup import failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wallpaperController.showSystemWallpaper()
        activeNotificationRefreshCoordinator.start()
        lifecycle.addObserver(packageChangeObserver)
        lifecycle.addObserver(widgetHostGateway)

        setContent {
            LauncherShell(
                viewModel = shellViewModel,
                appIconLoader = appIconLoader,
                widgetViewFactory = widgetHostGateway,
                onAction = ::handleAction,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        shellViewModel.refreshInstalledApps()
        shellViewModel.refreshNotifications()
        shellViewModel.refreshWidgetProviders()
        refreshPlatformStatuses()
    }

    private fun refreshPlatformStatuses() {
        shellViewModel.onHomeRoleStatusChanged(
            homeRoleStatus = homeRoleGateway.getHomeRoleStatus(),
            notificationAccessStatus = notificationAccessGateway.getNotificationAccessStatus(),
        )
    }

    private fun handleAction(action: LauncherShellAction) {
        val handled =
            handleFirstRunAction(action) ||
                handleNavigationAction(action) ||
                handleHomePageAction(action) ||
                handleHomeShortcutAction(action) ||
                handleDockAction(action) ||
                action.handleNotificationAction(
                    viewModel = shellViewModel,
                    notificationDismissalGateway = AndroidNotificationDismissalGateway,
                ) ||
                action.handleSettingsAction(
                    viewModel = shellViewModel,
                    notificationAccessGateway = notificationAccessGateway,
                    openIntent = ::startActivity,
                    exportBackup = { createBackupDocument.launch(BACKUP_DOCUMENT_NAME) },
                    importBackup = { openBackupDocument.launch(BACKUP_DOCUMENT_OPEN_MIME_TYPES) },
                )

        if (!handled) {
            handleAppAction(action)
        }
    }

    private fun handleFirstRunAction(action: LauncherShellAction): Boolean =
        when (action) {
            LauncherShellAction.RequestDefaultHome -> {
                shellViewModel.onDefaultHomeRequestStarted()
                requestHomeRole.launch(homeRoleGateway.createHomeRoleRequestIntent())
                true
            }

            else -> false
        }

    private fun handleNavigationAction(action: LauncherShellAction): Boolean =
        action.navigationAction()
            ?.also(shellViewModel::onNavigationActionSelected)
            ?.let { true }
            ?: false

    private fun handleHomePageAction(action: LauncherShellAction): Boolean =
        action.isHomePageEditAction()
            .also { isHomePageEditAction ->
                if (isHomePageEditAction) {
                    shellViewModel.onHomePageEdited(action)
                }
            }

    private fun handleHomeShortcutAction(action: LauncherShellAction): Boolean =
        when (action) {
            is LauncherShellAction.RemoveHomeShortcut -> {
                shellViewModel.state.value.homeLayout
                    .selectedPageHostedWidgetIdForItem(action.itemId)
                    ?.let(widgetHostGateway::deleteHostedWidgetId)
                shellViewModel.onHomeShortcutEdited(action)
                true
            }

            is LauncherShellAction.CreateEmptyHomeFolder,
            is LauncherShellAction.CreateHomeFolder,
            is LauncherShellAction.RenameHomeFolder,
            is LauncherShellAction.AddAppToFolder,
            is LauncherShellAction.RemoveAppFromFolder,
            is LauncherShellAction.MoveHomeShortcutToCell,
            is LauncherShellAction.AddAppShortcutToHome,
            is LauncherShellAction.AddHostedWidgetToHome,
            is LauncherShellAction.ResizeHomeWidget,
            -> {
                shellViewModel.onHomeShortcutEdited(action)
                true
            }

            else -> false
        }

    private fun handleDockAction(action: LauncherShellAction): Boolean =
        when (action) {
            is LauncherShellAction.AddAppToDock,
            is LauncherShellAction.SelectDockEnabled,
            is LauncherShellAction.SelectDockCapacity,
            is LauncherShellAction.SelectDockIconSize,
            is LauncherShellAction.SelectDockBackgroundAlpha,
            is LauncherShellAction.SelectDockBackgroundSizing,
            is LauncherShellAction.SelectDockItemSpacing,
            is LauncherShellAction.RemoveDockShortcut,
            is LauncherShellAction.MoveDockShortcut,
            -> {
                shellViewModel.onDockEdited(action)
                true
            }

            else -> false
        }

    private fun handleAppAction(action: LauncherShellAction) {
        when (action) {
            is LauncherShellAction.LaunchApp -> appLauncher.launch(action.identity)
            is LauncherShellAction.LaunchAppShortcut -> appLauncher.launchShortcut(action.shortcut)
            is LauncherShellAction.OpenAppInfo -> appLauncher.openAppInfo(action.identity)
            is LauncherShellAction.UninstallApp -> appLauncher.uninstall(action.identity)
            is LauncherShellAction.AddAppToHome -> shellViewModel.onAddAppToHome(action.app)
            is LauncherShellAction.RequestAddWidget -> {
                when (
                    val requestResult =
                        widgetBindingCoordinator.requestAddWidget(
                            action = action,
                            grid = shellViewModel.state.value.homeLayout.selectedPage.grid,
                            availableWidthDp = resources.configuration.screenWidthDp,
                            availableHeightDp = resources.configuration.screenHeightDp,
                        )
                ) {
                    is WidgetAddRequestResult.Bound ->
                        completeHostedWidgetAdd(
                            activity = this,
                            viewModel = shellViewModel,
                            action = requestResult.action,
                        )

                    is WidgetAddRequestResult.RequiresPermission ->
                        requestWidgetBind.launch(
                            widgetHostGateway.createBindHostedWidgetIntent(
                                requestResult.hostedWidgetId,
                                requestResult.provider,
                            ),
                        )
                }
            }

            is LauncherShellAction.HideApp,
            is LauncherShellAction.UnhideApp,
            LauncherShellAction.RefreshInstalledApps,
            is LauncherShellAction.AppDrawerQueryChanged,
            is LauncherShellAction.AppDrawerProfileFilterSelected,
            is LauncherShellAction.SearchQueryChanged,
            is LauncherShellAction.SearchProfileFilterSelected,
            LauncherShellAction.OpenWidgetPicker,
            LauncherShellAction.CloseWidgetPicker,
            -> {
                shellViewModel.onAppActionSelected(action)
                if (action == LauncherShellAction.RefreshInstalledApps) {
                    Toast.makeText(this, "App list refreshed", Toast.LENGTH_SHORT).show()
                }
            }

            else -> Unit
        }
    }
}

private fun LauncherShellAction.navigationAction(): ShellNavigationAction? =
    when (this) {
        LauncherShellAction.OpenHome -> ShellNavigationAction.OpenHome
        LauncherShellAction.OpenAppDrawer -> ShellNavigationAction.OpenAppDrawer
        LauncherShellAction.OpenSearch -> ShellNavigationAction.OpenSearch
        LauncherShellAction.OpenNotifications -> ShellNavigationAction.OpenNotifications
        LauncherShellAction.OpenSettings -> ShellNavigationAction.OpenSettings
        else -> null
    }

private const val BACKUP_DOCUMENT_MIME_TYPE = "application/json"
private const val BACKUP_DOCUMENT_NAME = "riffle-backup.json"
private val BACKUP_DOCUMENT_OPEN_MIME_TYPES =
    arrayOf(
        BACKUP_DOCUMENT_MIME_TYPE,
        "text/*",
        "application/octet-stream",
    )

private fun completeHostedWidgetAdd(
    activity: ComponentActivity,
    viewModel: LauncherShellViewModel,
    action: LauncherShellAction.AddHostedWidgetToHome,
) {
    viewModel.onHomeShortcutEdited(action)
    widgetSpanAdjustmentMessage(
        viewModel = viewModel,
        label = action.label,
        idealSpan = action.preferredSpan,
        hostedWidgetId = action.hostedWidgetId,
    )?.let { message ->
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }
    viewModel.onAppActionSelected(LauncherShellAction.CloseWidgetPicker)
}

private fun widgetSpanAdjustmentMessage(
    viewModel: LauncherShellViewModel,
    label: String,
    idealSpan: GridSpan,
    hostedWidgetId: HostedWidgetId,
): String? =
    viewModel.state.value.homeLayout.pages
        .flatMap { page -> page.items }
        .filterIsInstance<WidgetItem>()
        .firstOrNull { widget -> widget.appWidgetId == hostedWidgetId }
        ?.placement
        ?.span
        ?.let { actualSpan ->
            widgetSpanAdjustmentToast(
                label = label,
                idealSpan = idealSpan,
                actualSpan = actualSpan,
            )
        }
