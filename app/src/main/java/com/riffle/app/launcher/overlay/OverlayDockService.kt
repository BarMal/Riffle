package com.riffle.app.launcher.overlay

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import com.riffle.app.launcher.DataStoreLauncherSettingsRepository
import com.riffle.app.launcher.apps.AndroidAppLauncher
import com.riffle.app.launcher.apps.AndroidRecentAppRepository
import com.riffle.app.launcher.apps.PackageManagerInstalledAppRepository
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.OverlayDockSettings
import com.riffle.core.domain.launcher.settings.coerceOverlayDockSettings

class OverlayDockService : Service() {
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private val appLauncher by lazy { AndroidAppLauncher(this) }
    private val recentAppRepository by lazy { AndroidRecentAppRepository(this) }
    private val viewFactory by lazy { OverlayDockViewFactory(context = this, appLauncher = appLauncher) }
    private val launcherSettingsRepository by lazy { DataStoreLauncherSettingsRepository(this) }
    private var overlayView: View? = null
    private var expanded: Boolean = false
    private var currentOverlaySettings: OverlayDockSettings? = null

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

        val overlaySettings = loadLauncherSettings().overlayDock.coerceOverlayDockSettings()
        currentOverlaySettings = overlaySettings
        val content = overlayDockContent(overlaySettings)
        val view =
            if (expanded) {
                viewFactory.expandedDockView(
                    content = content,
                    settings = overlaySettings,
                    onCollapse = {
                        expanded = false
                        renderOverlay()
                    },
                    onLaunch = {
                        expanded = false
                        renderOverlay()
                    },
                    onRequestUsageAccess = {
                        startActivity(
                            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            },
                        )
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
                    onVerticalOffsetChange = ::moveCollapsedHandle,
                    onVerticalOffsetCommit = ::saveCollapsedHandleOffset,
                )
            }

        overlayView = view
        windowManager.addView(view, viewFactory.overlayLayoutParams(overlaySettings, expanded = expanded))
    }

    private fun overlayDockContent(settings: OverlayDockSettings): OverlayDockShortcuts {
        val installedApps = PackageManagerInstalledAppRepository(this).installedApps()

        return settings.contentFor(
            installedApps = installedApps,
            recentAppUsages = recentAppRepository.recentAppUsages(),
            canReadRecentApps = recentAppRepository.canReadRecentApps(),
        )
    }

    private fun loadLauncherSettings(): LauncherSettings {
        return launcherSettingsRepository.loadLauncherSettings() ?: LauncherSettings()
    }

    private fun moveCollapsedHandle(offsetDp: Int) {
        val view = overlayView ?: return
        val settings = currentOverlaySettings?.copy(verticalOffsetDp = offsetDp)?.coerceOverlayDockSettings() ?: return
        currentOverlaySettings = settings
        windowManager.updateViewLayout(view, viewFactory.overlayLayoutParams(settings, expanded = false))
    }

    private fun saveCollapsedHandleOffset(offsetDp: Int) {
        val launcherSettings = loadLauncherSettings()
        launcherSettingsRepository.saveLauncherSettings(
            launcherSettings.copy(
                overlayDock =
                    launcherSettings.overlayDock
                        .copy(verticalOffsetDp = offsetDp)
                        .coerceOverlayDockSettings(),
            ),
        )
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        currentOverlaySettings = null
    }
}
