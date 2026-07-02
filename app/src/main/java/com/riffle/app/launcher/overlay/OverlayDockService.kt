package com.riffle.app.launcher.overlay

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import com.riffle.app.launcher.DataStoreHomeLayoutRepository
import com.riffle.app.launcher.DataStoreLauncherSettingsRepository
import com.riffle.app.launcher.apps.AndroidAppLauncher
import com.riffle.app.launcher.apps.PackageManagerInstalledAppRepository
import com.riffle.app.launcher.visibleTo
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.settings.LauncherSettings

class OverlayDockService : Service() {
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private val appLauncher by lazy { AndroidAppLauncher(this) }
    private val viewFactory by lazy { OverlayDockViewFactory(context = this, appLauncher = appLauncher) }
    private var overlayView: View? = null
    private var expanded: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (!shouldShowOverlay()) {
            stopSelf()
            return START_NOT_STICKY
        }

        renderOverlay()
        return START_STICKY
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun shouldShowOverlay(): Boolean =
        Settings.canDrawOverlays(this) &&
            (loadLauncherSettings().overlayDock.enabled)

    private fun renderOverlay() {
        removeOverlay()

        val overlaySettings = loadLauncherSettings().overlayDock
        val shortcuts = overlayDockShortcuts()
        val view =
            if (expanded) {
                viewFactory.expandedDockView(
                    shortcuts = shortcuts,
                    settings = overlaySettings,
                    onCollapse = {
                        expanded = false
                        renderOverlay()
                    },
                    onLaunch = {
                        expanded = false
                        renderOverlay()
                    },
                )
            } else {
                viewFactory.collapsedHandleView(
                    settings = overlaySettings,
                    onExpand = {
                        expanded = true
                        renderOverlay()
                    },
                )
            }

        overlayView = view
        windowManager.addView(view, viewFactory.overlayLayoutParams(overlaySettings))
    }

    private fun overlayDockShortcuts(): List<AppShortcutItem> {
        val layout = DataStoreHomeLayoutRepository(this).loadHomeLayout() ?: HomeLayoutDefaults.standard()
        val installedApps = PackageManagerInstalledAppRepository(this).installedApps()
        val visibleDock = layout.visibleTo(installedApps).dock

        return visibleDock.items
            .filterIsInstance<AppShortcutItem>()
            .take(visibleDock.capacity.coerceAtLeast(0))
    }

    private fun loadLauncherSettings(): LauncherSettings =
        DataStoreLauncherSettingsRepository(this).loadLauncherSettings() ?: LauncherSettings()

    private fun removeOverlay() {
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
    }
}
