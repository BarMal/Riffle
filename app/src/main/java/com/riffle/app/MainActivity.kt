package com.riffle.app

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.riffle.app.launcher.AndroidHomeRoleGateway
import com.riffle.app.launcher.AndroidLauncherWallpaperController
import com.riffle.app.launcher.LauncherBackupDocument
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
import com.riffle.app.launcher.encodeLauncherBackupDocument
import com.riffle.app.launcher.handleNotificationAction
import com.riffle.app.launcher.handleSettingsAction
import com.riffle.app.launcher.isHomePageEditAction
import com.riffle.app.launcher.launcherBackupDocument
import com.riffle.app.launcher.notifications.AndroidNotificationAccessGateway
import com.riffle.app.launcher.notifications.AndroidNotificationDismissalGateway
import com.riffle.app.launcher.notifications.SharedPreferencesActiveNotificationRepository
import com.riffle.app.launcher.selectedPageHostedWidgetIdForItem
import com.riffle.app.launcher.widgets.AndroidInstalledWidgetProviderRepository
import com.riffle.app.launcher.widgets.AndroidWidgetHostGateway
import com.riffle.app.launcher.widgets.WidgetBindingResult
import com.riffle.app.launcher.widgets.preferredGridSpan
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
    private val widgetHostGateway by lazy { AndroidWidgetHostGateway(this) }
    private var pendingWidgetBind: PendingWidgetBind? = null

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
            val pending = pendingWidgetBind
            pendingWidgetBind = null
            when {
                pending == null -> Unit
                result.resultCode == Activity.RESULT_OK -> {
                    shellViewModel.onHomeShortcutEdited(
                        LauncherShellAction.AddHostedWidgetToHome(
                            hostedWidgetId = pending.hostedWidgetId,
                            label = pending.label,
                            preferredSpan = pending.preferredSpan,
                        ),
                    )
                    widgetSpanAdjustmentMessage(
                        viewModel = shellViewModel,
                        label = pending.label,
                        idealSpan = pending.preferredSpan,
                        hostedWidgetId = pending.hostedWidgetId,
                    )?.let { message ->
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                    shellViewModel.onAppActionSelected(LauncherShellAction.CloseWidgetPicker)
                }

                else -> widgetHostGateway.deleteHostedWidgetId(pending.hostedWidgetId)
            }
        }

    private val createBackupDocument =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument(BACKUP_DOCUMENT_MIME_TYPE),
        ) { uri ->
            uri?.let { selectedUri ->
                exportLauncherBackup(
                    activity = this,
                    uri = selectedUri,
                    document =
                        launcherBackupDocument(
                            storedLayoutSet = homeLayoutRepository.loadHomeLayoutSet(),
                            activeLayout = shellViewModel.state.value.homeLayout,
                            launcherSettings = shellViewModel.state.value.launcherSettings,
                        ),
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wallpaperController.showSystemWallpaper()
        activeNotificationRepository.observeActiveNotifications {
            runOnUiThread {
                shellViewModel.refreshInstalledApps()
            }
        }
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
                val hostedWidgetId = widgetHostGateway.allocateHostedWidgetId()
                val preferredSpan =
                    action.dimensions.preferredGridSpan(
                        grid = shellViewModel.state.value.homeLayout.selectedPage.grid,
                        availableWidthDp = resources.configuration.screenWidthDp,
                        availableHeightDp = resources.configuration.screenHeightDp,
                    )
                when (widgetHostGateway.bindHostedWidget(hostedWidgetId, action.provider)) {
                    WidgetBindingResult.Bound -> {
                        shellViewModel.onHomeShortcutEdited(
                            LauncherShellAction.AddHostedWidgetToHome(
                                hostedWidgetId = hostedWidgetId,
                                label = action.label,
                                preferredSpan = preferredSpan,
                            ),
                        )
                        widgetSpanAdjustmentMessage(
                            viewModel = shellViewModel,
                            label = action.label,
                            idealSpan = preferredSpan,
                            hostedWidgetId = hostedWidgetId,
                        )?.let { message ->
                            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                        }
                        shellViewModel.onAppActionSelected(LauncherShellAction.CloseWidgetPicker)
                    }

                    WidgetBindingResult.RequiresPermission -> {
                        pendingWidgetBind?.let { pending ->
                            widgetHostGateway.deleteHostedWidgetId(pending.hostedWidgetId)
                        }
                        pendingWidgetBind =
                            PendingWidgetBind(
                                hostedWidgetId = hostedWidgetId,
                                label = action.label,
                                preferredSpan = preferredSpan,
                            )
                        requestWidgetBind.launch(
                            widgetHostGateway.createBindHostedWidgetIntent(hostedWidgetId, action.provider),
                        )
                    }
                }
            }

            is LauncherShellAction.HideApp,
            is LauncherShellAction.UnhideApp,
            is LauncherShellAction.AppDrawerQueryChanged,
            is LauncherShellAction.AppDrawerProfileFilterSelected,
            is LauncherShellAction.SearchQueryChanged,
            is LauncherShellAction.SearchProfileFilterSelected,
            LauncherShellAction.OpenWidgetPicker,
            LauncherShellAction.CloseWidgetPicker,
            -> shellViewModel.onAppActionSelected(action)

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

private data class PendingWidgetBind(
    val hostedWidgetId: HostedWidgetId,
    val label: String,
    val preferredSpan: GridSpan,
)

private const val BACKUP_DOCUMENT_MIME_TYPE = "application/json"
private const val BACKUP_DOCUMENT_NAME = "riffle-backup.json"

private fun exportLauncherBackup(
    activity: ComponentActivity,
    uri: Uri,
    document: LauncherBackupDocument,
) {
    val backupJson = encodeLauncherBackupDocument(document)

    runCatching {
        activity.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(backupJson.toByteArray(Charsets.UTF_8))
        } ?: error("Could not open backup destination")
    }.fold(
        onSuccess = {
            Toast.makeText(activity, "Backup exported", Toast.LENGTH_SHORT).show()
        },
        onFailure = {
            Toast.makeText(activity, "Backup export failed", Toast.LENGTH_SHORT).show()
        },
    )
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
