package com.riffle.app

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.riffle.app.launcher.DefaultLauncherNotificationActionHandler
import com.riffle.app.launcher.DefaultLauncherSettingsActionHandler
import com.riffle.app.launcher.HomeLayoutDeviceClassEvent
import com.riffle.app.launcher.LauncherActionRouter
import com.riffle.app.launcher.LauncherActivityActionHandler
import com.riffle.app.launcher.LauncherAppActionCallbacks
import com.riffle.app.launcher.LauncherAppActionHandler
import com.riffle.app.launcher.LauncherAppLaunchCallbacks
import com.riffle.app.launcher.LauncherBackupImportHandlingResult
import com.riffle.app.launcher.LauncherSettingsActionCallbacks
import com.riffle.app.launcher.LauncherShell
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LauncherShellViewModel
import com.riffle.app.launcher.LauncherShellViewModelFactory
import com.riffle.app.launcher.LauncherWidgetAddHandlingResult
import com.riffle.app.launcher.LauncherWidgetRenderers
import com.riffle.app.launcher.WallpaperPickerLaunchResult
import com.riffle.app.launcher.completeWidgetAdd
import com.riffle.app.launcher.failureMessage
import com.riffle.app.launcher.fallbackWallpaperSourceAction
import com.riffle.app.launcher.isLauncherHomeIntent
import com.riffle.app.launcher.notifications.AndroidNotificationDismissalGateway
import com.riffle.app.launcher.refreshInstalledApps
import com.riffle.app.launcher.refreshNotifications
import com.riffle.app.launcher.refreshWidgetProviders
import com.riffle.app.launcher.selectedPageHostedWidgetIdForItem
import com.riffle.app.launcher.startSystemUiSync
import com.riffle.app.launcher.widgets.WidgetBindPermissionResult
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val dependencies by lazy { MainActivityDependencies(this) }
    private val homeLayoutRepository get() = dependencies.homeLayoutRepository
    private val launcherSettingsRepository get() = dependencies.launcherSettingsRepository
    private val shellViewModel: LauncherShellViewModel by viewModels {
        LauncherShellViewModelFactory(
            firstRunRepository = dependencies.firstRunRepository,
            installedAppRepository = dependencies.installedAppRepository,
            appVisibilityRepository = dependencies.appVisibilityRepository,
            homeLayoutRepository = homeLayoutRepository,
            launcherSettingsRepository = launcherSettingsRepository,
            platformDependencies = dependencies.platformDependencies(),
        )
    }
    private val homeRoleGateway get() = dependencies.homeRoleGateway
    private val appLauncher get() = dependencies.appLauncher
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
    private val appBuildIdentityLabel by lazy {
        launcherBuildIdentityLabel(
            appVersionLabel = appVersionLabel,
            packageName = packageName,
            buildType = packageName.launcherBuildTypeLabel(),
        )
    }
    private val appIconLoader get() = dependencies.appIconLoader
    private val packageChangeObserver by lazy {
        dependencies.packageChangeObserver { shellViewModel.refreshInstalledApps() }
    }
    private val wallpaperController get() = dependencies.wallpaperController
    private val wallpaperPickerGateway get() = dependencies.wallpaperPickerGateway
    private val notificationAccessGateway get() = dependencies.notificationAccessGateway
    private val overlayDockPermissionGateway get() = dependencies.overlayDockPermissionGateway
    private val overlayDockServiceController get() = dependencies.overlayDockServiceController
    private val homeLayoutDeviceClassObserver get() = dependencies.homeLayoutDeviceClassObserver
    private val activeNotificationRefreshCoordinator by lazy {
        dependencies.activeNotificationRefreshCoordinator { shellViewModel.refreshNotifications() }
    }
    private val backupDocumentHandler by lazy {
        dependencies.backupDocumentHandler { shellViewModel.state.value }
    }
    private val widgetHostGateway get() = dependencies.widgetHostGateway
    private val widgetBindingCoordinator get() = dependencies.widgetBindingCoordinator
    private val widgetAddRequestHandler by lazy {
        dependencies.widgetAddRequestHandler(
            selectedGrid = { shellViewModel.state.value.homeLayout.selectedPage.grid },
            completeWidgetAdd = shellViewModel::completeWidgetAdd,
        )
    }

    private val requestHomeRole =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            refreshPlatformStatuses()
        }

    private val requestOverlayDockPermission =
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
            notificationActionHandler =
                DefaultLauncherNotificationActionHandler(
                    notificationDismissalGateway = AndroidNotificationDismissalGateway,
                    refreshNotifications = { shellViewModel.refreshNotifications() },
                ),
            settingsActionHandler =
                DefaultLauncherSettingsActionHandler(
                    callbacks =
                        LauncherSettingsActionCallbacks(
                            applySettingsState = { action ->
                                shellViewModel.onLauncherSettingsActionSelected(action)
                                val wallpaperResult =
                                    wallpaperController.applySource(
                                        shellViewModel.state.value.launcherSettings.appearance.wallpaper.source,
                                    )
                                if (action is LauncherShellAction.SelectWallpaperSource) {
                                    wallpaperResult.fallbackWallpaperSourceAction()?.let { fallbackAction ->
                                        shellViewModel.onLauncherSettingsActionSelected(fallbackAction)
                                    }
                                    wallpaperResult.failureMessage()?.let { message ->
                                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                syncOverlayDockService()
                            },
                            requestNotificationAccess = {
                                runCatching {
                                    startActivity(notificationAccessGateway.createNotificationListenerSettingsIntent())
                                }
                            },
                            requestOverlayDockPermission = {
                                requestOverlayDockPermission.launch(
                                    overlayDockPermissionGateway.createOverlayPermissionSettingsIntent(),
                                )
                            },
                            changeWallpaper = {
                                when (wallpaperPickerGateway.launchWallpaperPicker()) {
                                    WallpaperPickerLaunchResult.Launched -> Unit
                                    WallpaperPickerLaunchResult.Unavailable ->
                                        Toast.makeText(
                                            this,
                                            "No wallpaper picker is available on this device.",
                                            Toast.LENGTH_SHORT,
                                        ).show()

                                    WallpaperPickerLaunchResult.Failed ->
                                        Toast.makeText(
                                            this,
                                            "Wallpaper picker could not be opened.",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            },
                            exportBackup = { createBackupDocument.launch(BACKUP_DOCUMENT_NAME) },
                            importBackup = { openBackupDocument.launch(BACKUP_DOCUMENT_OPEN_MIME_TYPES) },
                        ),
                ),
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
                    is LauncherBackupImportHandlingResult.Imported -> {
                        shellViewModel.onLauncherSettingsActionSelected(result.action)
                        wallpaperController.applySource(
                            shellViewModel.state.value.launcherSettings.appearance.wallpaper.source,
                        )
                    }

                    is LauncherBackupImportHandlingResult.Failure -> Unit
                }
                Toast.makeText(this, result.message.text, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        wallpaperController.applySource(shellViewModel.state.value.launcherSettings.appearance.wallpaper.source)
        activeNotificationRefreshCoordinator.start()
        lifecycle.addObserver(packageChangeObserver)
        lifecycle.addObserver(widgetHostGateway)
        refreshHomeLayoutDeviceClass(source = "onCreate")
        observeHomeLayoutDeviceClass()
        startSystemUiSync(shellViewModel.state)

        setContent {
            LauncherShell(
                viewModel = shellViewModel,
                appVersionLabel = appVersionLabel,
                appBuildIdentityLabel = appBuildIdentityLabel,
                appIconLoader = appIconLoader,
                widgetRenderers =
                    LauncherWidgetRenderers(
                        viewFactory = widgetHostGateway,
                        previewImageLoader = dependencies.widgetPreviewImageLoader,
                    ),
                onAction = launcherActionRouter::handle,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        shellViewModel.refreshInstalledApps()
        shellViewModel.refreshNotifications()
        shellViewModel.refreshWidgetProviders()
        refreshHomeLayoutDeviceClass(source = "onResume")
        refreshPlatformStatuses()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.isLauncherHomeIntent()) {
            launcherActionRouter.handle(LauncherShellAction.OpenDefaultHome)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshHomeLayoutDeviceClass(source = "onConfigurationChanged")
    }

    private fun refreshPlatformStatuses() {
        shellViewModel.onHomeRoleStatusChanged(
            homeRoleStatus = homeRoleGateway.getHomeRoleStatus(),
            notificationAccessStatus = notificationAccessGateway.getNotificationAccessStatus(),
            overlayDockPermissionStatus = overlayDockPermissionGateway.getOverlayDockPermissionStatus(),
        )
        syncOverlayDockService()
    }

    private fun syncOverlayDockService() {
        overlayDockServiceController.sync(
            settings = shellViewModel.state.value.launcherSettings,
            permissionStatus = shellViewModel.state.value.overlayDockPermissionStatus,
        )
    }

    private fun refreshHomeLayoutDeviceClass(source: String) {
        homeLayoutDeviceClassObserver.currentDeviceClassEvent(source)?.let(::applyHomeLayoutDeviceClassEvent)
    }

    private fun observeHomeLayoutDeviceClass() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeLayoutDeviceClassObserver.deviceClassEvents()
                    .collect { event ->
                        event?.let(::applyHomeLayoutDeviceClassEvent)
                    }
            }
        }
    }

    private fun applyHomeLayoutDeviceClassEvent(event: HomeLayoutDeviceClassEvent) {
        Log.i(FOLDABLE_LAYOUT_LOG_TAG, event.logText)
        shellViewModel.onHomePageEdited(
            LauncherShellAction.SelectHomeLayoutDeviceClass(
                deviceClass = event.selection.activeDeviceClass,
                availableDeviceClasses = event.selection.availableDeviceClasses,
            ),
        )
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
private const val FOLDABLE_LAYOUT_LOG_TAG = "RiffleFoldableLayout"

private fun String.launcherBuildTypeLabel(): String =
    when {
        endsWith(".debug") -> "debug"
        else -> "release"
    }

private val BACKUP_DOCUMENT_OPEN_MIME_TYPES =
    arrayOf(
        BACKUP_DOCUMENT_MIME_TYPE,
        "text/*",
        "application/octet-stream",
    )
