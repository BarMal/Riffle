package com.riffle.app

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.riffle.app.launcher.AndroidHomeLayoutDeviceClassObserver
import com.riffle.app.launcher.AndroidHomeRoleGateway
import com.riffle.app.launcher.AndroidLauncherWallpaperController
import com.riffle.app.launcher.AndroidWidgetAddWindowSizeProvider
import com.riffle.app.launcher.LauncherActionRouter
import com.riffle.app.launcher.LauncherActivityActionHandler
import com.riffle.app.launcher.LauncherAppActionCallbacks
import com.riffle.app.launcher.LauncherAppActionHandler
import com.riffle.app.launcher.LauncherAppLaunchCallbacks
import com.riffle.app.launcher.LauncherBackupDocumentGateway
import com.riffle.app.launcher.LauncherBackupDocumentHandler
import com.riffle.app.launcher.LauncherBackupExportCoordinator
import com.riffle.app.launcher.LauncherBackupImportCoordinator
import com.riffle.app.launcher.LauncherBackupImportHandlingResult
import com.riffle.app.launcher.LauncherShell
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LauncherShellPlatformDependencies
import com.riffle.app.launcher.LauncherShellViewModel
import com.riffle.app.launcher.LauncherShellViewModelFactory
import com.riffle.app.launcher.LauncherWidgetAddHandlingResult
import com.riffle.app.launcher.LauncherWidgetAddRequestHandler
import com.riffle.app.launcher.SharedPreferencesAppVisibilityRepository
import com.riffle.app.launcher.SharedPreferencesFirstRunRepository
import com.riffle.app.launcher.SharedPreferencesHomeLayoutRepository
import com.riffle.app.launcher.SharedPreferencesLauncherSettingsRepository
import com.riffle.app.launcher.apps.AndroidAppLauncher
import com.riffle.app.launcher.apps.AndroidAppShortcutRepository
import com.riffle.app.launcher.apps.AndroidPackageChangeObserver
import com.riffle.app.launcher.apps.PackageManagerAppIconLoader
import com.riffle.app.launcher.apps.PackageManagerInstalledAppRepository
import com.riffle.app.launcher.completeWidgetAdd
import com.riffle.app.launcher.handleNotificationAction
import com.riffle.app.launcher.handleSettingsAction
import com.riffle.app.launcher.homeLayoutDeviceClassFromConfiguration
import com.riffle.app.launcher.notifications.ActiveNotificationRefreshCoordinator
import com.riffle.app.launcher.notifications.AndroidNotificationAccessGateway
import com.riffle.app.launcher.notifications.AndroidNotificationDismissalGateway
import com.riffle.app.launcher.notifications.SharedPreferencesActiveNotificationRepository
import com.riffle.app.launcher.refreshNotifications
import com.riffle.app.launcher.refreshWidgetProviders
import com.riffle.app.launcher.selectedPageHostedWidgetIdForItem
import com.riffle.app.launcher.widgets.AndroidInstalledWidgetProviderRepository
import com.riffle.app.launcher.widgets.AndroidWidgetHostGateway
import com.riffle.app.launcher.widgets.WidgetBindPermissionResult
import com.riffle.app.launcher.widgets.WidgetBindingCoordinator
import kotlinx.coroutines.launch

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
                    initialHomeLayoutDeviceClass =
                        homeLayoutDeviceClassFromConfiguration(
                            screenWidthDp = resources.configuration.screenWidthDp,
                            screenHeightDp = resources.configuration.screenHeightDp,
                        ),
                ),
        )
    }
    private val homeRoleGateway by lazy { AndroidHomeRoleGateway(this) }
    private val appLauncher by lazy { AndroidAppLauncher(this) }
    private val appVersionLabel by lazy {
        packageManager
            .getPackageInfo(packageName, 0)
            .let { packageInfo ->
                launcherVersionLabel(
                    versionName = packageInfo.versionName,
                    versionCode = packageInfo.longVersionCode,
                )
            }
    }
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
    private val homeLayoutDeviceClassObserver by lazy { AndroidHomeLayoutDeviceClassObserver(this) }
    private val activeNotificationRepository by lazy { SharedPreferencesActiveNotificationRepository(this) }
    private val activeNotificationRefreshCoordinator by lazy {
        ActiveNotificationRefreshCoordinator(
            notificationChangeSource = activeNotificationRepository,
            dispatchOnMainThread = { action -> runOnUiThread { action() } },
            refreshNotifications = { shellViewModel.refreshNotifications() },
        )
    }
    private val backupDocumentHandler by lazy {
        LauncherBackupDocumentHandler(
            exportCoordinator =
                LauncherBackupExportCoordinator(
                    homeLayoutRepository = homeLayoutRepository,
                    currentState = { shellViewModel.state.value },
                ),
            importCoordinator = LauncherBackupImportCoordinator(),
            documentGateway = LauncherBackupDocumentGateway(),
        )
    }
    private val widgetHostGateway by lazy { AndroidWidgetHostGateway(this) }
    private val widgetBindingCoordinator by lazy { WidgetBindingCoordinator(widgetHostGateway) }
    private val widgetAddWindowSizeProvider by lazy { AndroidWidgetAddWindowSizeProvider(this) }
    private val widgetAddRequestHandler by lazy {
        LauncherWidgetAddRequestHandler(
            widgetBindingCoordinator = widgetBindingCoordinator,
            selectedGrid = { shellViewModel.state.value.homeLayout.selectedPage.grid },
            windowSize = widgetAddWindowSizeProvider::windowSize,
            completeWidgetAdd = shellViewModel::completeWidgetAdd,
        )
    }

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
                    shellViewModel.completeWidgetAdd(permissionResult.action)
                        ?.let { message -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }

                WidgetBindPermissionResult.Cancelled,
                WidgetBindPermissionResult.Ignored,
                -> Unit
            }
        }

    private val activityActionHandler by lazy {
        LauncherActivityActionHandler(
            requestDefaultHome = {
                shellViewModel.onDefaultHomeRequestStarted()
                requestHomeRole.launch(homeRoleGateway.createHomeRoleRequestIntent())
            },
            navigate = shellViewModel::onNavigationActionSelected,
            editHomePage = shellViewModel::onHomePageEdited,
            editHomeShortcut = shellViewModel::onHomeShortcutEdited,
            editDock = shellViewModel::onDockEdited,
            hostedWidgetIdForRemovedShortcut = { itemId ->
                shellViewModel.state.value.homeLayout.selectedPageHostedWidgetIdForItem(itemId)
            },
            deleteHostedWidget = widgetHostGateway::deleteHostedWidgetId,
        )
    }
    private val launcherActionRouter by lazy {
        LauncherActionRouter(
            activityActionHandler = activityActionHandler,
            handleNotificationAction = { action ->
                action.handleNotificationAction(
                    viewModel = shellViewModel,
                    notificationDismissalGateway = AndroidNotificationDismissalGateway,
                )
            },
            handleSettingsAction = { action ->
                action.handleSettingsAction(
                    viewModel = shellViewModel,
                    notificationAccessGateway = notificationAccessGateway,
                    openIntent = ::startActivity,
                    exportBackup = { createBackupDocument.launch(BACKUP_DOCUMENT_NAME) },
                    importBackup = { openBackupDocument.launch(BACKUP_DOCUMENT_OPEN_MIME_TYPES) },
                )
            },
            appActionHandler =
                LauncherAppActionHandler(
                    callbacks =
                        LauncherAppActionCallbacks(
                            launch =
                                LauncherAppLaunchCallbacks(
                                    launchApp = { action -> appLauncher.launch(action.identity) },
                                    launchAppShortcut = { action -> appLauncher.launchShortcut(action.shortcut) },
                                    openAppInfo = { action -> appLauncher.openAppInfo(action.identity) },
                                    uninstallApp = { action -> appLauncher.uninstall(action.identity) },
                                ),
                            addAppToHome = { action -> shellViewModel.onAddAppToHome(action.app) },
                            requestAddWidget = ::handleWidgetAddRequest,
                            applyAppState = { action -> shellViewModel.onAppActionSelected(action) },
                            appListRefreshed = {
                                Toast.makeText(this, "App list refreshed", Toast.LENGTH_SHORT).show()
                            },
                        ),
                ),
        )
    }

    private val createBackupDocument =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument(BACKUP_DOCUMENT_MIME_TYPE),
        ) { uri ->
            uri?.let { selectedUri ->
                backupDocumentHandler
                    .exportBackup { contentResolver.openOutputStream(selectedUri) }
                    .message
                    .text
                    .let { message -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
            }
        }

    private val openBackupDocument =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let { selectedUri ->
                val result =
                    backupDocumentHandler.importBackup {
                        contentResolver.openInputStream(selectedUri)
                    }

                when (result) {
                    is LauncherBackupImportHandlingResult.Imported ->
                        shellViewModel.onLauncherSettingsActionSelected(result.action)

                    is LauncherBackupImportHandlingResult.Failure -> Unit
                }
                Toast.makeText(this, result.message.text, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        wallpaperController.showSystemWallpaper()
        activeNotificationRefreshCoordinator.start()
        lifecycle.addObserver(packageChangeObserver)
        lifecycle.addObserver(widgetHostGateway)
        observeHomeLayoutDeviceClass()

        setContent {
            LauncherShell(
                viewModel = shellViewModel,
                appVersionLabel = appVersionLabel,
                appIconLoader = appIconLoader,
                widgetViewFactory = widgetHostGateway,
                onAction = launcherActionRouter::handle,
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

    private fun observeHomeLayoutDeviceClass() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeLayoutDeviceClassObserver.deviceClasses()
                    .collect { deviceClass ->
                        deviceClass?.let { layoutDeviceClass ->
                            shellViewModel.onHomePageEdited(
                                LauncherShellAction.SelectHomeLayoutDeviceClass(layoutDeviceClass),
                            )
                        }
                    }
            }
        }
    }

    private fun handleWidgetAddRequest(action: LauncherShellAction.RequestAddWidget) {
        when (val result = widgetAddRequestHandler.handle(action)) {
            is LauncherWidgetAddHandlingResult.Completed ->
                result.message?.let { message -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }

            is LauncherWidgetAddHandlingResult.RequiresPermission ->
                requestWidgetBind.launch(
                    widgetHostGateway.createBindHostedWidgetIntent(
                        result.hostedWidgetId,
                        result.provider,
                    ),
                )
        }
    }
}

private const val BACKUP_DOCUMENT_MIME_TYPE = "application/json"
private const val BACKUP_DOCUMENT_NAME = "riffle-backup.json"
private val BACKUP_DOCUMENT_OPEN_MIME_TYPES =
    arrayOf(
        BACKUP_DOCUMENT_MIME_TYPE,
        "text/*",
        "application/octet-stream",
    )
